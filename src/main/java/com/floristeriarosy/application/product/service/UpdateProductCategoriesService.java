package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.command.UpdateProductCategoriesCommand;
import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.application.product.mapper.ProductDtoMapper;
import com.floristeriarosy.application.product.port.in.UpdateProductCategoriesUseCase;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.exception.product.ProductWithoutCategoryException;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link UpdateProductCategoriesUseCase}: replaces a product's full set of categories. */
@Service
@Transactional
public class UpdateProductCategoriesService implements UpdateProductCategoriesUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateProductCategoriesService.class);

  private final ProductReadPort readPort;
  private final ProductCategoryPort categoryPort;
  private final ProductImagePort imagePort;

  /**
   * @param readPort loads the product being updated, for the response
   * @param categoryPort replaces and reloads the product's categories
   * @param imagePort loads the product's gallery for the response
   */
  public UpdateProductCategoriesService(
      ProductReadPort readPort, ProductCategoryPort categoryPort, ProductImagePort imagePort) {
    this.readPort = readPort;
    this.categoryPort = categoryPort;
    this.imagePort = imagePort;
  }

  /**
   * Replaces every category association. The list cannot be emptied through this endpoint — a
   * product only loses its last category when that category itself is deleted (product.md,
   * section 3.4).
   *
   * @param command id of the product to update, plus its complete new category set
   * @return the updated product
   * @throws ProductNotFoundException {@code command.id()} does not exist
   * @throws ProductWithoutCategoryException {@code command.categoryIds()} is empty
   */
  @Override
  public ProductDto execute(UpdateProductCategoriesCommand command) {
    LOGGER.debug("updateProductCategories id={} categoryIds={}", command.id(), command.categoryIds().size());

    if (command.categoryIds().isEmpty()) {
      throw new ProductWithoutCategoryException("A product cannot be left without any category through this endpoint");
    }

    ProductId id = ProductId.of(command.id());
    Product product =
        readPort.findById(id).orElseThrow(() -> new ProductNotFoundException("Product " + id + " not found"));

    categoryPort.replaceCategories(id, command.categoryIds().stream().map(CategoryId::of).toList());

    List<ProductCategoryRef> categories = categoryPort.findCategories(id);
    List<ProductImageRef> images = imagePort.findImages(id);
    BigDecimal activeSalePrice = readPort.findActiveSalePrice(id).orElse(null);
    ProductDto result = ProductDtoMapper.toDto(product, activeSalePrice, categories, images);

    LOGGER.debug("updateProductCategories -> id={} count={}", id, categories.size());
    return result;
  }
}
