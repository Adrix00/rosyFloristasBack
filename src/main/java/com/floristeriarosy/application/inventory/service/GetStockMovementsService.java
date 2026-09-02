package com.floristeriarosy.application.inventory.service;

import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.inventory.port.in.GetStockMovementsUseCase;
import com.floristeriarosy.application.inventory.port.out.StockMovementReadPort;
import com.floristeriarosy.application.inventory.query.GetStockMovementsQuery;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Implements {@link GetStockMovementsUseCase}: a product's complete stock movement history. */
@Service
public class GetStockMovementsService implements GetStockMovementsUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetStockMovementsService.class);

  private final ProductExistencePort productExistencePort;
  private final StockMovementReadPort readPort;

  /**
   * @param productExistencePort checks the source product exists
   * @param readPort lists the product's movement history
   */
  public GetStockMovementsService(ProductExistencePort productExistencePort, StockMovementReadPort readPort) {
    this.productExistencePort = productExistencePort;
    this.readPort = readPort;
  }

  /**
   * @param query the product whose history to list, plus the requested page
   * @return the matching movements, paginated, most recent first
   * @throws ProductNotFoundException {@code query.productId()} does not exist
   */
  @Override
  public PageResult<StockMovementDto> execute(GetStockMovementsQuery query) {
    LOGGER.debug(
        "getStockMovements productId={} page={} size={}", query.productId(), query.page(), query.size());

    ProductId productId = ProductId.of(query.productId());
    if (!productExistencePort.existsById(productId)) {
      throw new ProductNotFoundException("Product " + productId + " not found");
    }
    PageResult<StockMovementDto> result = readPort.findByProduct(productId, query.page(), query.size());

    LOGGER.debug("getStockMovements productId={} -> totalElements={}", productId, result.totalElements());
    return result;
  }
}
