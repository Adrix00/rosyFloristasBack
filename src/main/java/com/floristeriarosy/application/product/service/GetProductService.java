package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.application.product.mapper.ProductDtoMapper;
import com.floristeriarosy.application.product.port.in.GetProductUseCase;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.query.GetProductQuery;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.shared.util.LogSanitizer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetProductUseCase}: loads a single product by id or slug. A non-visible match
 * responds as not found, never as forbidden — no distinction between a public and an admin caller
 * exists yet ({@code SecurityConfig} placeholder; tracked gap, same as category).
 */
@Service
public class GetProductService implements GetProductUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetProductService.class);

  private final ProductReadPort readPort;
  private final ProductCategoryPort categoryPort;
  private final ProductImagePort imagePort;

  /**
   * @param readPort loads the product by id or slug, and its active discount price
   * @param categoryPort loads the product's categories
   * @param imagePort loads the product's gallery
   */
  public GetProductService(ProductReadPort readPort, ProductCategoryPort categoryPort, ProductImagePort imagePort) {
    this.readPort = readPort;
    this.categoryPort = categoryPort;
    this.imagePort = imagePort;
  }

  /**
   * @param query the raw path segment: a UUID or a slug
   * @return the matching product
   * @throws ProductNotFoundException no match exists, or it is not visible (product.md, section
   *     3.3, section 4)
   */
  @Override
  public ProductDto execute(GetProductQuery query) {
    LOGGER.debug("getProduct idOrSlug={}", LogSanitizer.sanitize(query.idOrSlug()));

    Product product = load(query.idOrSlug());
    if (!readPort.isVisible(product.id())) {
      throw new ProductNotFoundException("Product " + query.idOrSlug() + " not found");
    }

    List<ProductCategoryRef> categories = categoryPort.findCategories(product.id());
    List<ProductImageRef> images = imagePort.findImages(product.id());
    BigDecimal activeSalePrice = readPort.findActiveSalePrice(product.id()).orElse(null);
    ProductDto result = ProductDtoMapper.toDto(product, activeSalePrice, categories, images);

    LOGGER.debug("getProduct idOrSlug={} -> id={}", LogSanitizer.sanitize(query.idOrSlug()), result.id());
    return result;
  }

  /**
   * @param idOrSlug the raw path segment
   * @return the matching product
   * @throws ProductNotFoundException no product matches {@code idOrSlug}
   */
  private Product load(String idOrSlug) {
    Optional<Product> byId = parseUuid(idOrSlug).flatMap(readPort::findById);
    return byId
        .or(() -> readPort.findBySlug(idOrSlug))
        .orElseThrow(() -> new ProductNotFoundException("Product " + idOrSlug + " not found"));
  }

  /**
   * @param value the raw path segment
   * @return {@code value} parsed as a {@link ProductId}, if it is a valid UUID
   */
  private Optional<ProductId> parseUuid(String value) {
    try {
      return Optional.of(ProductId.of(UUID.fromString(value)));
    } catch (IllegalArgumentException notAUuid) {
      return Optional.empty();
    }
  }
}
