package com.floristeriarosy.application.inventory.port.in;

import com.floristeriarosy.application.inventory.command.RegisterStockMovementCommand;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.exception.inventory.InventoryAlreadyInitializedException;
import com.floristeriarosy.domain.exception.inventory.InventoryInsufficientStockException;
import com.floristeriarosy.domain.exception.inventory.InventoryNotManagedException;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import java.util.UUID;

/**
 * The single write path of the inventory module (inventory.md, section 1, section 7): {@code
 * product}, {@code order} and {@code purchasing} call this directly instead of writing {@code
 * stock_movements} or {@code products.stock} on their own.
 */
public interface RegisterStockMovementUseCase {

  /**
   * @param command the product, movement kind, signed quantity and note
   * @return the recorded movement
   * @throws InventoryNotManagedException the product has {@code stock = NULL}
   * @throws InventoryInsufficientStockException a {@code SALE}/{@code WASTE}/negative {@code
   *     ADJUSTMENT} would take stock below zero
   * @throws InventoryAlreadyInitializedException a second {@code INITIAL} was attempted for the
   *     same product
   */
  StockMovementDto execute(RegisterStockMovementCommand command);

  /**
   * Convenience overload for a caller that must not depend on {@link RegisterStockMovementCommand}
   * itself — e.g. {@code ProductInventoryPersistenceAdapter}, an {@code infrastructure.persistence}
   * class barred from the {@code application..command} package (InfrastructureArchitectureTest:
   * {@code persistence_should_not_depend_on_commands}). Delegates to {@link
   * #execute(RegisterStockMovementCommand)}.
   *
   * @param productId the product whose stock is changing
   * @param type the kind of movement
   * @param quantity the signed quantity
   * @param adminUserId the admin who triggered it, or {@code null}
   * @param note optional note
   * @return the recorded movement
   */
  default StockMovementDto execute(
      UUID productId, StockMovementType type, int quantity, UUID adminUserId, String note) {
    return execute(new RegisterStockMovementCommand(productId, type, quantity, adminUserId, note));
  }

  /**
   * Activates managed inventory for a product, whether or not it was ever managed before
   * (product.md, section 3.7). A product with no movement history gets an {@code INITIAL}; one that
   * already carries history gets an {@code ADJUSTMENT}, because {@code ux_stock_movements_initial}
   * allows exactly one {@code INITIAL} per product for its whole lifetime.
   *
   * <p>The caller does not have to know which case it is in, and must not find out by attempting
   * an {@code INITIAL} and catching the violation: the failed {@code INSERT} aborts the enclosing
   * PostgreSQL transaction and marks it rollback-only, so the fallback would write into a doomed
   * transaction and the request would fail with a 500 either way.
   *
   * <p>The {@code ADJUSTMENT}'s quantity is the delta against the sum of the product's previous
   * movements, never the absolute stock: {@code products.stock} must equal that sum, and a daily
   * reconciliation job raises {@code RECONCILIATION_MISMATCH} when it does not (inventory.md,
   * section 3.8). A reactivation whose delta is zero writes no movement row at all — there is
   * nothing to record, and {@code chk_stock_movements_quantity_nonzero} forbids it.
   *
   * @param productId the product to activate inventory for
   * @param stock the stock to (re)start at
   * @param adminUserId the admin who triggered it, or {@code null}
   * @param note optional note
   */
  void initializeOrReactivate(UUID productId, int stock, UUID adminUserId, String note);

  /**
   * Sets a managed product's stock to an absolute value and records the {@code ADJUSTMENT} that
   * represents the change (product.md, section 3.7: an administrator states the stock they counted,
   * not a delta).
   *
   * <p>Takes {@code expectedStock} because the caller has already read it: the write is conditional
   * on the product still holding that value, so two adjustments issued at once cannot compound into
   * a third number neither administrator asked for. The loser is told (409), never merged silently.
   *
   * @param productId the product to adjust
   * @param expectedStock the stock the caller read before deciding
   * @param newStock the stock to set
   * @param adminUserId the admin who triggered it, or {@code null}
   * @param note optional note
   * @throws ResourceModifiedException the product's stock changed since {@code expectedStock} was
   *     read
   */
  void adjustToAbsolute(UUID productId, int expectedStock, int newStock, UUID adminUserId, String note);
}
