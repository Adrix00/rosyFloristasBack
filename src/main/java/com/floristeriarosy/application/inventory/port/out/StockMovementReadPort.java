package com.floristeriarosy.application.inventory.port.out;

import com.floristeriarosy.application.inventory.dto.ReconciliationMismatch;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.List;

/** Retrieves stock movements (ADR-003; inventory.md, section 8). */
public interface StockMovementReadPort {

  /**
   * @param productId the product whose history to list
   * @param page requested page, zero-based
   * @param size requested page size
   * @return the matching movements, paginated, most recent first
   */
  PageResult<StockMovementDto> findByProduct(ProductId productId, int page, int size);

  /**
   * Runs the {@code RECONCILIATION_MISMATCH} detection query (inventory.md, section 3.8): every
   * managed product whose {@code stock} disagrees with the sum of its own movements.
   *
   * @return every product currently mismatched
   */
  List<ReconciliationMismatch> findReconciliationMismatches();
}
