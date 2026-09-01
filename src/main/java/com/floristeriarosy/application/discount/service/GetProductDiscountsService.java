package com.floristeriarosy.application.discount.service;

import com.floristeriarosy.application.discount.dto.DiscountDto;
import com.floristeriarosy.application.discount.mapper.DiscountDtoMapper;
import com.floristeriarosy.application.discount.port.in.GetProductDiscountsUseCase;
import com.floristeriarosy.application.discount.port.out.DiscountReadPort;
import com.floristeriarosy.application.discount.query.GetProductDiscountsQuery;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Implements {@link GetProductDiscountsUseCase}: lists a product's complete discount history. */
@Service
public class GetProductDiscountsService implements GetProductDiscountsUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetProductDiscountsService.class);

  private final ProductExistencePort productExistencePort;
  private final DiscountReadPort readPort;

  /**
   * @param productExistencePort checks the source product exists
   * @param readPort lists the product's discount history
   */
  public GetProductDiscountsService(ProductExistencePort productExistencePort, DiscountReadPort readPort) {
    this.productExistencePort = productExistencePort;
    this.readPort = readPort;
  }

  /**
   * @param query the product whose discount history to list
   * @return every discount ever created for the product, most recent first
   * @throws ProductNotFoundException {@code query.productId()} does not exist
   */
  @Override
  public List<DiscountDto> execute(GetProductDiscountsQuery query) {
    LOGGER.debug("getProductDiscounts productId={}", query.productId());

    ProductId productId = ProductId.of(query.productId());
    if (!productExistencePort.existsById(productId)) {
      throw new ProductNotFoundException("Product " + productId + " not found");
    }
    List<DiscountDto> result =
        readPort.findByProduct(productId).stream().map(DiscountDtoMapper::toDto).toList();

    LOGGER.debug("getProductDiscounts productId={} -> count={}", productId, result.size());
    return result;
  }
}
