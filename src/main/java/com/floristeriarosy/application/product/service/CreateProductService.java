package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.command.CreateProductCommand;
import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.dto.ProductImageAssignment;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.application.product.mapper.ProductDtoMapper;
import com.floristeriarosy.application.product.port.in.CreateProductUseCase;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductInventoryPort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.port.out.ProductWritePort;
import com.floristeriarosy.application.product.validation.ProductAttributeValidator;
import com.floristeriarosy.domain.exception.product.ProductAlreadyExistsException;
import com.floristeriarosy.domain.exception.product.ProductWithoutCategoryException;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.shared.util.LogSanitizer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link CreateProductUseCase}: the product, its categories, its gallery and — if it
 * starts with managed inventory — its {@code INITIAL} stock movement, all in one transaction
 * (product.md, section 7).
 */
@Service
@Transactional
public class CreateProductService implements CreateProductUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateProductService.class);

  private final ProductWritePort writePort;
  private final ProductReadPort readPort;
  private final ProductExistencePort existencePort;
  private final ProductCategoryPort categoryPort;
  private final ProductImagePort imagePort;
  private final ProductInventoryPort inventoryPort;
  private final ProductAttributeValidator attributeValidator;

  /**
   * @param writePort persists the new product
   * @param readPort reloads the product after inventory activation, to reflect its final stock
   * @param existencePort checks the generated slug is not already taken
   * @param categoryPort assigns the product's categories
   * @param imagePort assigns the product's gallery
   * @param inventoryPort activates managed inventory when an initial stock is given
   * @param attributeValidator validates {@code attributes} against the declared definitions
   */
  public CreateProductService(
      ProductWritePort writePort,
      ProductReadPort readPort,
      ProductExistencePort existencePort,
      ProductCategoryPort categoryPort,
      ProductImagePort imagePort,
      ProductInventoryPort inventoryPort,
      ProductAttributeValidator attributeValidator) {
    this.writePort = writePort;
    this.readPort = readPort;
    this.existencePort = existencePort;
    this.categoryPort = categoryPort;
    this.imagePort = imagePort;
    this.inventoryPort = inventoryPort;
    this.attributeValidator = attributeValidator;
  }

  /**
   * Creates a new, {@code ACTIVE} product.
   *
   * @param command the product's fields, categories, gallery and optional initial stock
   * @return the created product
   * @throws ProductWithoutCategoryException {@code command.categoryIds()} is empty (product.md,
   *     section 3.4)
   * @throws ProductAlreadyExistsException the slug generated from {@code command.name()} is
   *     already in use
   */
  @Override
  public ProductDto execute(CreateProductCommand command) {
    LOGGER.debug(
        "createProduct name={} price={} categoryIds={} isExtra={} imageIds={} initialStock={}",
        LogSanitizer.sanitize(command.name()),
        command.price(),
        command.categoryIds().size(),
        command.isExtra(),
        command.imageIds().size(),
        command.initialStock());

    if (command.categoryIds().isEmpty()) {
      throw new ProductWithoutCategoryException("A product must belong to at least one category");
    }
    attributeValidator.validate(command.attributes());

    ProductSlug slug = ProductSlug.generateFrom(command.name());
    if (existencePort.existsBySlug(slug.value())) {
      throw new ProductAlreadyExistsException(
          "A product with slug '" + slug.value() + "' already exists");
    }

    Product product =
        Product.create(
            ProductId.newId(),
            command.name(),
            slug,
            command.description(),
            command.price(),
            command.isExtra(),
            command.attributes());
    Product saved = writePort.save(product);
    ProductId id = saved.id();

    categoryPort.replaceCategories(id, command.categoryIds().stream().map(CategoryId::of).toList());
    imagePort.replaceImages(
        id, command.imageIds().stream().map(imageId -> new ProductImageAssignment(imageId, null)).toList());
    if (command.initialStock() != null) {
      inventoryPort.initializeStock(id, command.initialStock(), null, null);
      saved = readPort.findById(id).orElseThrow();
    }

    List<ProductCategoryRef> categories = categoryPort.findCategories(id);
    List<ProductImageRef> images = imagePort.findImages(id);
    ProductDto result = ProductDtoMapper.toDto(saved, null, categories, images);

    LOGGER.debug("createProduct -> id={} slug={}", result.id(), result.slug());
    return result;
  }
}
