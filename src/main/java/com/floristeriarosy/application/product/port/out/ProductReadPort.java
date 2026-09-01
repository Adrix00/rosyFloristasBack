package com.floristeriarosy.application.product.port.out;

import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductAdminListingCriteria;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.util.Optional;

/** Retrieves products (ADR-003; product.md, section 8). */
public interface ProductReadPort {

  /**
   * @param id the product to load
   * @return the product, if it exists
   */
  Optional<Product> findById(ProductId id);

  /**
   * @param slug the product to load
   * @return the product, if it exists
   */
  Optional<Product> findBySlug(String slug);

  /**
   * @param id the product to check
   * @return {@code true} if {@code status = ACTIVE} and it has at least one {@code ACTIVE}
   *     category (product.md, section 3.3)
   */
  boolean isVisible(ProductId id);

  /**
   * @param id the product to price
   * @return the {@code sale_price} of its currently active discount, if any (product.md, section
   *     3.1: time-window vigency and, when limited, unsold units remaining)
   */
  Optional<BigDecimal> findActiveSalePrice(ProductId id);

  /**
   * @param criteria the admin's status/category/extra filters and the requested page
   * @return the matching products, paginated, regardless of visibility (product.md, section 4:
   *     {@code GET /products/all})
   */
  PageResult<ProductSummaryDto> findAllForAdmin(ProductAdminListingCriteria criteria);
}
