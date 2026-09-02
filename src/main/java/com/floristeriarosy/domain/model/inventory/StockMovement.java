package com.floristeriarosy.domain.model.inventory;

import com.floristeriarosy.domain.model.inventory.valueobject.StockMovementId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root of the inventory module (inventory.md, section 1): one immutable audit row per
 * change to a product's stock. Nothing ever edits or deletes a {@code StockMovement} —
 * {@code RegisterStockMovementService} is the only writer, and it only ever inserts.
 */
public final class StockMovement {

  private static final Logger LOGGER = LoggerFactory.getLogger(StockMovement.class);

  private final StockMovementId id;
  private final ProductId productId;
  private final StockMovementType type;
  private final int quantity;
  private final int resultingStock;
  private final UUID adminUserId;
  private final String note;
  private final Instant createdAt;

  private StockMovement(
      StockMovementId id,
      ProductId productId,
      StockMovementType type,
      int quantity,
      int resultingStock,
      UUID adminUserId,
      String note,
      Instant createdAt) {
    this.id = id;
    this.productId = productId;
    this.type = type;
    this.quantity = requireValidQuantity(type, quantity);
    this.resultingStock = requireNonNegative(resultingStock);
    this.adminUserId = adminUserId;
    this.note = note;
    this.createdAt = createdAt;
  }

  /**
   * New movement, about to be inserted. {@code resultingStock} must be exactly what the caller's
   * conditional {@code UPDATE} on {@code products.stock} returned (inventory.md, section 3.1) —
   * never a value computed separately.
   *
   * @param id application-generated identifier
   * @param productId the product this movement belongs to
   * @param type the kind of movement
   * @param quantity the signed quantity, per {@code type}'s mandatory sign
   * @param resultingStock the product's stock immediately after this movement
   * @param adminUserId the admin who triggered it, or {@code null} for a system-generated movement
   * @param note optional note
   * @return the new, not-yet-persisted movement
   * @throws IllegalArgumentException {@code quantity} has the wrong sign for {@code type}, or
   *     {@code resultingStock} is negative
   */
  public static StockMovement create(
      StockMovementId id,
      ProductId productId,
      StockMovementType type,
      int quantity,
      int resultingStock,
      UUID adminUserId,
      String note) {
    LOGGER.debug(
        "create id={} productId={} type={} quantity={} resultingStock={} adminUserId={}",
        id,
        productId,
        type,
        quantity,
        resultingStock,
        adminUserId);
    StockMovement result =
        new StockMovement(id, productId, type, quantity, resultingStock, adminUserId, note, null);
    LOGGER.debug("create id={} -> created", id);
    return result;
  }

  /**
   * Rebuilds a movement from persisted state. Used only by the persistence mapper — not logged, it
   * runs once per row loaded from the database.
   *
   * @param id the persisted identifier
   * @param productId the persisted product reference
   * @param type the persisted movement kind
   * @param quantity the persisted signed quantity
   * @param resultingStock the persisted stock after this movement
   * @param adminUserId the persisted admin reference, or {@code null}
   * @param note the persisted note, or {@code null}
   * @param createdAt when the row was created
   * @return the rebuilt movement
   */
  public static StockMovement reconstitute(
      StockMovementId id,
      ProductId productId,
      StockMovementType type,
      int quantity,
      int resultingStock,
      UUID adminUserId,
      String note,
      Instant createdAt) {
    return new StockMovement(id, productId, type, quantity, resultingStock, adminUserId, note, createdAt);
  }

  /**
   * @param type the movement's kind
   * @param quantity candidate signed quantity
   * @return {@code quantity}, unchanged
   * @throws IllegalArgumentException {@code quantity} does not carry the sign {@code type} requires
   *     ({@code chk_stock_movements_*_sign})
   */
  private static int requireValidQuantity(StockMovementType type, int quantity) {
    if (type == StockMovementType.INITIAL) {
      if (quantity < 0) {
        throw new IllegalArgumentException("INITIAL quantity must be >= 0");
      }
      return quantity;
    }
    if (quantity == 0) {
      throw new IllegalArgumentException("quantity must not be zero for " + type);
    }
    if (type == StockMovementType.PURCHASE && quantity <= 0) {
      throw new IllegalArgumentException("PURCHASE quantity must be > 0");
    }
    if (type == StockMovementType.SALE && quantity >= 0) {
      throw new IllegalArgumentException("SALE quantity must be < 0");
    }
    if (type == StockMovementType.WASTE && quantity >= 0) {
      throw new IllegalArgumentException("WASTE quantity must be < 0");
    }
    return quantity;
  }

  /**
   * @param resultingStock candidate stock after this movement
   * @return {@code resultingStock}, unchanged
   * @throws IllegalArgumentException {@code resultingStock} is negative ({@code
   *     chk_stock_movements_resulting_stock})
   */
  private static int requireNonNegative(int resultingStock) {
    if (resultingStock < 0) {
      throw new IllegalArgumentException("resultingStock must not be negative");
    }
    return resultingStock;
  }

  /**
   * @return the application-generated identifier
   */
  public StockMovementId id() {
    return id;
  }

  /**
   * @return the product this movement belongs to
   */
  public ProductId productId() {
    return productId;
  }

  /**
   * @return the kind of movement
   */
  public StockMovementType type() {
    return type;
  }

  /**
   * @return the signed quantity
   */
  public int quantity() {
    return quantity;
  }

  /**
   * @return the product's stock immediately after this movement
   */
  public int resultingStock() {
    return resultingStock;
  }

  /**
   * @return the admin who triggered this movement, or {@code null} for a system-generated one
   */
  public UUID adminUserId() {
    return adminUserId;
  }

  /**
   * @return the optional note, or {@code null}
   */
  public String note() {
    return note;
  }

  /**
   * @return when the row was created, or {@code null} before the first save
   */
  public Instant createdAt() {
    return createdAt;
  }
}
