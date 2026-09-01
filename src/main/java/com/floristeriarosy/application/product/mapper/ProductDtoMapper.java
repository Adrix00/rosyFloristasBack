package com.floristeriarosy.application.product.mapper;

import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.domain.model.product.Product;
import java.math.BigDecimal;
import java.util.List;

/** Domain to application DTO (00-project-principles.md #10: Application Mapper). */
public final class ProductDtoMapper {

  private ProductDtoMapper() {}

  /**
   * @param product the domain product to expose
   * @param activeSalePrice the {@code sale_price} of its currently active discount, if any
   * @param categories the categories it belongs to
   * @param images its image gallery, ordered by position
   * @return its read shape, with plain UUID/enum fields a Controller may hold directly
   */
  public static ProductDto toDto(
      Product product,
      BigDecimal activeSalePrice,
      List<ProductCategoryRef> categories,
      List<ProductImageRef> images) {
    BigDecimal effectivePrice = activeSalePrice != null ? activeSalePrice : product.price();
    return new ProductDto(
        product.id().value(),
        product.name(),
        product.slug().value(),
        product.description(),
        product.price(),
        effectivePrice,
        activeSalePrice != null,
        product.status(),
        product.isExtra(),
        product.attributes(),
        categories,
        images,
        product.stock(),
        product.stock() != null,
        product.createdAt(),
        product.updatedAt());
  }
}
