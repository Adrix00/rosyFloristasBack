package com.floristeriarosy.application.inventory.service;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.inventory.command.RegisterStockMovementCommand;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.inventory.mapper.StockMovementDtoMapper;
import com.floristeriarosy.application.inventory.port.in.RegisterStockMovementUseCase;
import com.floristeriarosy.application.inventory.port.out.ProductStockPort;
import com.floristeriarosy.application.inventory.port.out.StockMovementReadPort;
import com.floristeriarosy.application.inventory.port.out.StockMovementWritePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.application.shared.support.AdminDisplayNameResolver;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.exception.inventory.InventoryInsufficientStockException;
import com.floristeriarosy.domain.exception.inventory.InventoryNotManagedException;
import com.floristeriarosy.domain.model.inventory.StockMovement;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import com.floristeriarosy.domain.model.inventory.valueobject.StockMovementId;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.shared.util.LogSanitizer;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link RegisterStockMovementUseCase}: the single write path of the inventory module
 * (inventory.md, section 1, section 3.1). Every write is a conditional {@code UPDATE} on {@code
 * products.stock} followed by an {@code INSERT} into {@code stock_movements}, both in the same
 * transaction, never a {@code SELECT} before the write itself.
 */
@Service
@Transactional
public class RegisterStockMovementService implements RegisterStockMovementUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(RegisterStockMovementService.class);

  private final ProductStockPort stockPort;
  private final StockMovementWritePort movementWritePort;
  private final StockMovementReadPort movementReadPort;
  private final ProductReadPort productReadPort;
  private final AdminReadPort adminReadPort;
  private final PiiCryptoPort piiCryptoPort;

  /**
   * @param stockPort applies the conditional {@code UPDATE} on {@code products.stock}
   * @param movementWritePort inserts the resulting audit row
   * @param movementReadPort totals a product's existing movements, so an activation can tell an
   *     {@code INITIAL} from a reactivation without provoking a constraint violation
   * @param productReadPort diagnostic-only read, used solely to disambiguate an unmanaged product
   *     from insufficient stock after a failed conditional decrement (inventory.md, section 3.1:
   *     this is not the write path itself, only its failure diagnosis)
   * @param adminReadPort resolves the triggering admin's encrypted email, for the response's
   *     {@code adminUserName} (inventory.md, section 6)
   * @param piiCryptoPort decrypts it (ADR-005)
   */
  public RegisterStockMovementService(
      ProductStockPort stockPort,
      StockMovementWritePort movementWritePort,
      StockMovementReadPort movementReadPort,
      ProductReadPort productReadPort,
      AdminReadPort adminReadPort,
      PiiCryptoPort piiCryptoPort) {
    this.stockPort = stockPort;
    this.movementWritePort = movementWritePort;
    this.movementReadPort = movementReadPort;
    this.productReadPort = productReadPort;
    this.adminReadPort = adminReadPort;
    this.piiCryptoPort = piiCryptoPort;
  }

  /**
   * @param command the product, movement kind, signed quantity and note
   * @return the recorded movement
   */
  @Override
  public StockMovementDto execute(RegisterStockMovementCommand command) {
    LOGGER.debug(
        "registerStockMovement productId={} type={} quantity={} adminUserId={}",
        command.productId(),
        command.type(),
        command.quantity(),
        command.adminUserId());

    ProductId productId = ProductId.of(command.productId());
    int resultingStock = applyToProductStock(productId, command.type(), command.quantity());

    StockMovement movement =
        StockMovement.create(
            StockMovementId.newId(),
            productId,
            command.type(),
            command.quantity(),
            resultingStock,
            command.adminUserId(),
            command.note());
    StockMovement saved = movementWritePort.save(movement);

    String adminUserName = AdminDisplayNameResolver.resolve(adminReadPort, piiCryptoPort, saved.adminUserId());
    StockMovementDto result = StockMovementDtoMapper.toDto(saved, adminUserName);
    LOGGER.debug("registerStockMovement -> id={} resultingStock={}", result.id(), result.resultingStock());
    return result;
  }

  /**
   * Explicit override of {@link RegisterStockMovementUseCase}'s default overload. Required so
   * Spring's transactional proxy actually intercepts this call: invoking an interface {@code
   * default} method through the proxy runs it against the raw target, so its self-invocation of
   * {@code this.execute(command)} would bypass the proxy — and with it, {@code @Transactional} —
   * entirely, letting {@link com.floristeriarosy.application.inventory.port.out.ProductStockPort}'s
   * write commit independently of the {@code stock_movements} insert. Overriding it here makes the
   * call itself proxied, so the class-level {@code @Transactional} applies before the body ever
   * self-invokes {@link #execute(RegisterStockMovementCommand)}.
   *
   * @param productId the product whose stock is changing
   * @param type the kind of movement
   * @param quantity the signed quantity
   * @param adminUserId the admin who triggered it, or {@code null}
   * @param note optional note
   * @return the recorded movement
   */
  @Override
  public StockMovementDto execute(
      UUID productId, StockMovementType type, int quantity, UUID adminUserId, String note) {
    return execute(new RegisterStockMovementCommand(productId, type, quantity, adminUserId, note));
  }

  /**
   * Asks the movement history which case this is instead of attempting an {@code INITIAL} and
   * catching the {@code ux_stock_movements_initial} violation: that violation aborts the enclosing
   * PostgreSQL transaction, so no fallback written inside it could ever commit.
   *
   * @param productId the product to activate inventory for
   * @param stock the stock to (re)start at
   * @param adminUserId the admin who triggered it, or {@code null}
   * @param note optional note
   */
  @Override
  public void initializeOrReactivate(UUID productId, int stock, UUID adminUserId, String note) {
    LOGGER.debug(
        "initializeOrReactivate productId={} stock={} adminUserId={} note={}",
        productId,
        stock,
        adminUserId,
        LogSanitizer.sanitize(String.valueOf(note)));

    ProductId id = ProductId.of(productId);
    Optional<Integer> previousTotal = movementReadPort.movementsTotal(id);
    if (previousTotal.isEmpty()) {
      execute(new RegisterStockMovementCommand(productId, StockMovementType.INITIAL, stock, adminUserId, note));
      LOGGER.debug("initializeOrReactivate productId={} -> INITIAL {}", productId, stock);
      return;
    }

    int resultingStock = stockPort.setInitial(id, stock);
    int delta = stock - previousTotal.get();
    if (delta == 0) {
      LOGGER.debug("initializeOrReactivate productId={} -> stock already matches its history, no movement", productId);
      return;
    }
    StockMovement movement =
        StockMovement.create(
            StockMovementId.newId(), id, StockMovementType.ADJUSTMENT, delta, resultingStock, adminUserId, note);
    movementWritePort.save(movement);
    LOGGER.debug(
        "initializeOrReactivate productId={} -> ADJUSTMENT {} resultingStock={}",
        productId,
        delta,
        resultingStock);
  }

  /**
   * @param productId the product to adjust
   * @param expectedStock the stock the caller read before deciding
   * @param newStock the stock to set
   * @param adminUserId the admin who triggered it, or {@code null}
   * @param note optional note
   * @throws ResourceModifiedException the product's stock changed since {@code expectedStock} was
   *     read
   */
  @Override
  public void adjustToAbsolute(
      UUID productId, int expectedStock, int newStock, UUID adminUserId, String note) {
    LOGGER.debug(
        "adjustToAbsolute productId={} expectedStock={} newStock={} adminUserId={}",
        productId,
        expectedStock,
        newStock,
        adminUserId);

    int delta = newStock - expectedStock;
    if (delta == 0) {
      LOGGER.debug("adjustToAbsolute productId={} -> stock unchanged, no movement", productId);
      return;
    }

    ProductId id = ProductId.of(productId);
    int resultingStock =
        stockPort
            .compareAndSetStock(id, expectedStock, newStock)
            .orElseThrow(
                () ->
                    new ResourceModifiedException(
                        "Stock of product " + id + " changed while the adjustment was being prepared"));
    movementWritePort.save(
        StockMovement.create(
            StockMovementId.newId(), id, StockMovementType.ADJUSTMENT, delta, resultingStock, adminUserId, note));
    LOGGER.debug("adjustToAbsolute productId={} -> delta={} resultingStock={}", productId, delta, resultingStock);
  }

  /**
   * Dispatches to the right conditional write for {@code type}, per inventory.md, section 3.1.
   *
   * @param productId the product being written to
   * @param type the kind of movement
   * @param quantity the signed quantity
   * @return the resulting stock, exactly as the database's conditional {@code UPDATE} returned it
   * @throws InventoryNotManagedException the product has {@code stock = NULL}
   * @throws InventoryInsufficientStockException an outgoing movement would take stock below zero
   */
  private int applyToProductStock(ProductId productId, StockMovementType type, int quantity) {
    if (type == StockMovementType.INITIAL) {
      return stockPort.setInitial(productId, quantity);
    }
    if (isIncrease(type, quantity)) {
      return stockPort
          .incrementConditional(productId, quantity)
          .orElseThrow(() -> new InventoryNotManagedException("Product " + productId + " has no managed inventory"));
    }
    int absoluteQuantity = Math.abs(quantity);
    Optional<Integer> decremented = stockPort.decrementConditional(productId, absoluteQuantity);
    if (decremented.isEmpty()) {
      throw insufficientOrNotManaged(productId);
    }
    return decremented.get();
  }

  /**
   * @param type {@code PURCHASE}, {@code SALE}, {@code WASTE} or {@code ADJUSTMENT}
   * @param quantity the signed quantity
   * @return {@code true} if this movement increases stock
   */
  private boolean isIncrease(StockMovementType type, int quantity) {
    if (type == StockMovementType.PURCHASE) {
      return true;
    }
    if (type == StockMovementType.SALE || type == StockMovementType.WASTE) {
      return false;
    }
    return quantity > 0;
  }

  /**
   * Disambiguates a failed conditional decrement with a single diagnostic read — not the write
   * path itself, only its failure diagnosis (inventory.md, section 3.1).
   *
   * @param productId the product whose decrement failed
   * @return {@link InventoryNotManagedException} if {@code stock IS NULL}, otherwise {@link
   *     InventoryInsufficientStockException}
   */
  private RuntimeException insufficientOrNotManaged(ProductId productId) {
    Integer currentStock = productReadPort.findById(productId).map(Product::stock).orElse(null);
    if (currentStock == null) {
      return new InventoryNotManagedException("Product " + productId + " has no managed inventory");
    }
    return new InventoryInsufficientStockException("Product " + productId + " has insufficient stock");
  }
}
