package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.command.UpdateProductImagesCommand;
import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.application.product.mapper.ProductDtoMapper;
import com.floristeriarosy.application.product.port.in.UpdateProductImagesUseCase;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link UpdateProductImagesUseCase}: replaces a product's full image gallery. */
@Service
@Transactional
public class UpdateProductImagesService implements UpdateProductImagesUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateProductImagesService.class);

  private final ProductReadPort readPort;
  private final ProductCategoryPort categoryPort;
  private final ProductImagePort imagePort;

  /**
   * @param readPort loads the product being updated, for the response
   * @param categoryPort loads the product's categories for the response
   * @param imagePort replaces and reloads the product's gallery
   */
  public UpdateProductImagesService(
      ProductReadPort readPort, ProductCategoryPort categoryPort, ProductImagePort imagePort) {
    this.readPort = readPort;
    this.categoryPort = categoryPort;
    this.imagePort = imagePort;
  }

  /**
   * Replaces the whole gallery, in order (product.md, section 4).
   *
   * @param command id of the product to update, plus its complete new gallery
   * @return the updated product
   * @throws ProductNotFoundException {@code command.id()} does not exist
   */
  @Override
  public ProductDto execute(UpdateProductImagesCommand command) {
    LOGGER.debug("updateProductImages id={} count={}", command.id(), command.images().size());

    ProductId id = ProductId.of(command.id());
    Product product =
        readPort.findById(id).orElseThrow(() -> new ProductNotFoundException("Product " + id + " not found"));

    imagePort.replaceImages(id, command.images());

    List<ProductCategoryRef> categories = categoryPort.findCategories(id);
    List<ProductImageRef> images = imagePort.findImages(id);
    BigDecimal activeSalePrice = readPort.findActiveSalePrice(id).orElse(null);
    ProductDto result = ProductDtoMapper.toDto(product, activeSalePrice, categories, images);

    LOGGER.debug("updateProductImages -> id={} count={}", id, images.size());
    return result;
  }
}
