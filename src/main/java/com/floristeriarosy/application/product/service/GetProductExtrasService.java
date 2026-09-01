package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.port.in.GetProductExtrasUseCase;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.application.product.port.out.ProductSuggestionPort;
import com.floristeriarosy.application.product.query.GetProductExtrasQuery;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Implements {@link GetProductExtrasUseCase}: lists a product's suggested extras, already filtered by visibility. */
@Service
public class GetProductExtrasService implements GetProductExtrasUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetProductExtrasService.class);

  private final ProductExistencePort existencePort;
  private final ProductSuggestionPort suggestionPort;

  /**
   * @param existencePort checks the source product exists
   * @param suggestionPort lists its visible suggested extras
   */
  public GetProductExtrasService(ProductExistencePort existencePort, ProductSuggestionPort suggestionPort) {
    this.existencePort = existencePort;
    this.suggestionPort = suggestionPort;
  }

  /**
   * @param query the product whose suggested extras to list
   * @return the visible suggested extras
   * @throws ProductNotFoundException {@code query.id()} does not exist
   */
  @Override
  public List<ProductSummaryDto> execute(GetProductExtrasQuery query) {
    LOGGER.debug("getProductExtras id={}", query.id());

    ProductId id = ProductId.of(query.id());
    if (!existencePort.existsById(id)) {
      throw new ProductNotFoundException("Product " + id + " not found");
    }
    List<ProductSummaryDto> result = suggestionPort.findVisibleSuggestions(id);

    LOGGER.debug("getProductExtras id={} -> count={}", id, result.size());
    return result;
  }
}
