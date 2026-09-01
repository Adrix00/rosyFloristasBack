package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.command.UpdateProductCommand;
import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.application.product.mapper.ProductDtoMapper;
import com.floristeriarosy.application.product.port.in.UpdateProductUseCase;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.port.out.ProductWritePort;
import com.floristeriarosy.application.product.validation.ProductAttributeValidator;
import com.floristeriarosy.domain.exception.product.ProductAlreadyExistsException;
import com.floristeriarosy.domain.exception.product.ProductHasActiveDiscountException;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.shared.util.LogSanitizer;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link UpdateProductUseCase}: full replace ({@code PUT}) of a product's own fields. */
@Service
@Transactional
public class UpdateProductService implements UpdateProductUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateProductService.class);

  private final ProductReadPort readPort;
  private final ProductWritePort writePort;
  private final ProductCategoryPort categoryPort;
  private final ProductImagePort imagePort;
  private final ProductAttributeValidator attributeValidator;

  /**
   * @param readPort loads the product being updated, checks the new slug for conflicts, and
   *     checks for an active discount
   * @param writePort persists the updated product
   * @param categoryPort loads the product's categories for the response
   * @param imagePort loads the product's gallery for the response
   * @param attributeValidator validates {@code attributes} against the declared definitions
   */
  public UpdateProductService(
      ProductReadPort readPort,
      ProductWritePort writePort,
      ProductCategoryPort categoryPort,
      ProductImagePort imagePort,
      ProductAttributeValidator attributeValidator) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.categoryPort = categoryPort;
    this.imagePort = imagePort;
    this.attributeValidator = attributeValidator;
  }

  /**
   * Replaces name, description, price, extra flag and attributes of an existing product. Renaming
   * regenerates the slug (product.md, section 3.1).
   *
   * @param command id of the product to update, plus its new field values
   * @return the updated product
   * @throws ProductNotFoundException {@code command.id()} does not exist
   * @throws ProductAlreadyExistsException the slug generated from the new name is already used by
   *     another product
   * @throws ProductHasActiveDiscountException the price is changing while a discount is currently
   *     active (product.md, section 3.8)
   */
  @Override
  public ProductDto execute(UpdateProductCommand command) {
    LOGGER.debug(
        "updateProduct id={} name={} price={} isExtra={} attributes={}",
        command.id(),
        LogSanitizer.sanitize(command.name()),
        command.price(),
        command.isExtra(),
        LogSanitizer.sanitize(String.valueOf(command.attributes())));

    attributeValidator.validate(command.attributes());

    ProductId id = ProductId.of(command.id());
    Product product =
        readPort.findById(id).orElseThrow(() -> new ProductNotFoundException("Product " + id + " not found"));

    ProductSlug slug = ProductSlug.generateFrom(command.name());
    if (!slug.equals(product.slug())) {
      readPort
          .findBySlug(slug.value())
          .filter(other -> !other.id().equals(id))
          .ifPresent(
              other -> {
                throw new ProductAlreadyExistsException(
                    "A product with slug '" + slug.value() + "' already exists");
              });
    }

    if (command.price().compareTo(product.price()) != 0) {
      readPort
          .findActiveSalePrice(id)
          .ifPresent(
              price -> {
                throw new ProductHasActiveDiscountException(
                    "Product " + id + " has an active discount; end it before changing the base price");
              });
    }

    product.replace(
        command.name(), slug, command.description(), command.price(), command.isExtra(), command.attributes());
    Product saved = writePort.save(product);

    List<ProductCategoryRef> categories = categoryPort.findCategories(id);
    List<ProductImageRef> images = imagePort.findImages(id);
    BigDecimal activeSalePrice = readPort.findActiveSalePrice(id).orElse(null);
    ProductDto result = ProductDtoMapper.toDto(saved, activeSalePrice, categories, images);

    LOGGER.debug("updateProduct -> id={} slug={}", result.id(), result.slug());
    return result;
  }
}
