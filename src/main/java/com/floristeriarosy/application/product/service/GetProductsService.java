package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductAdminListingCriteria;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.port.in.GetProductsUseCase;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.query.GetProductsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetProductsUseCase}: the admin product listing ({@code GET /products/all}),
 * every status included, no visibility check (product.md, section 4).
 */
@Service
public class GetProductsService implements GetProductsUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetProductsService.class);

  private final ProductReadPort readPort;

  /**
   * @param readPort lists products for the admin panel, regardless of visibility
   */
  public GetProductsService(ProductReadPort readPort) {
    this.readPort = readPort;
  }

  /**
   * @param query the combinable filters and the requested page
   * @return the matching products, paginated, regardless of visibility
   */
  @Override
  public PageResult<ProductSummaryDto> execute(GetProductsQuery query) {
    LOGGER.debug(
        "getProducts status={} withoutCategory={} isExtra={} page={} size={}",
        query.status(),
        query.withoutCategory(),
        query.isExtra(),
        query.page(),
        query.size());

    ProductAdminListingCriteria criteria =
        new ProductAdminListingCriteria(
            query.status(), query.withoutCategory(), query.isExtra(), query.page(), query.size());
    PageResult<ProductSummaryDto> result = readPort.findAllForAdmin(criteria);

    LOGGER.debug("getProducts -> totalElements={}", result.totalElements());
    return result;
  }
}
