package com.floristeriarosy.application.inventory.port.in;

import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.inventory.query.GetStockMovementsQuery;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;

/** Lists a product's complete stock movement history, paginated (inventory.md, section 4). */
public interface GetStockMovementsUseCase {

  /**
   * @param query the product whose history to list, plus the requested page
   * @return the matching movements, paginated, most recent first
   * @throws ProductNotFoundException {@code query.productId()} does not exist
   */
  PageResult<StockMovementDto> execute(GetStockMovementsQuery query);
}
