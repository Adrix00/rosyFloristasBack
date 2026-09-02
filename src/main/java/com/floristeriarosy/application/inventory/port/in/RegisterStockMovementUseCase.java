package com.floristeriarosy.application.inventory.port.in;

import com.floristeriarosy.application.inventory.command.RegisterStockMovementCommand;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
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
   * Reactivates inventory for a product that already carries a prior {@code INITIAL} movement in
   * its history — a second {@code INITIAL} is impossible ({@code ux_stock_movements_initial}), so
   * {@code product.md} section 3.7 records the reactivation as an {@code ADJUSTMENT} instead.
   * Unlike a normal {@code ADJUSTMENT}, this unconditionally sets {@code products.stock} to {@code
   * stock} rather than applying it as a delta on top of the current value — the product is
   * currently unmanaged ({@code stock IS NULL}), so there is no prior numeric value to offset
   * from, only the absolute stock the reactivation starts at.
   *
   * @param productId the product to reactivate
   * @param stock the stock to (re)start at
   * @param adminUserId the admin who triggered it, or {@code null}
   * @param note optional note
   * @return the recorded movement
   */
  StockMovementDto reactivate(UUID productId, int stock, UUID adminUserId, String note);
}
