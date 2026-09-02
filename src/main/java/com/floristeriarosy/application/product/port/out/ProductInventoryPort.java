package com.floristeriarosy.application.product.port.out;

import com.floristeriarosy.domain.model.product.valueobject.ProductId;

/**
 * Requests {@code inventory} to write {@code products.stock} and its {@code stock_movements} trail
 * (ADR-003; product.md, section 8). {@code product} never writes {@code stock_movements} on its
 * own: it delegates every {@code INITIAL}/{@code ADJUSTMENT} it triggers to {@code
 * RegisterStockMovementUseCase} (inventory.md, section 1), the module's single transactional write
 * path. This port only owns the mode switch's intent and the {@code low_stock_threshold} update;
 * {@code ProductStockPort} (inventory.md, section 8) is the port that actually executes the write.
 */
public interface ProductInventoryPort {

  /**
   * First-time activation of inventory management: sets {@code stock} and records an {@code
   * INITIAL} movement. {@code ux_stock_movements_initial} guarantees this runs at most once per
   * product (product.md, section 3.7).
   *
   * @param id the product to activate inventory for
   * @param stock the initial stock
   * @param lowStockThreshold the low-stock alert threshold, or {@code null} to leave it unset
   * @param note optional note for the movement
   */
  void initializeStock(ProductId id, int stock, Integer lowStockThreshold, String note);

  /**
   * Sets {@code stock} to {@code newStock} on an already-managed product, recording the delta as
   * an {@code ADJUSTMENT} movement — or a second activation after the product was previously
   * unmanaged, per section 3.7 ("no segundo INITIAL"). A zero delta updates the threshold only,
   * without writing a movement row ({@code chk_stock_movements_quantity_nonzero} forbids a
   * zero-quantity non-{@code INITIAL} movement).
   *
   * @param id the product to adjust
   * @param newStock the new stock value
   * @param lowStockThreshold the low-stock alert threshold, or {@code null} to leave it unset
   * @param note optional note for the movement
   */
  void adjustStock(ProductId id, int newStock, Integer lowStockThreshold, String note);

  /**
   * Switches a product back to unmanaged inventory: {@code stock} becomes {@code null}: the
   * movement history is left intact (product.md, section 3.7).
   *
   * @param id the product to deactivate inventory for
   */
  void disableStockManagement(ProductId id);
}
