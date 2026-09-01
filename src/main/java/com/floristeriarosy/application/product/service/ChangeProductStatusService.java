package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.command.ChangeProductStatusCommand;
import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.application.product.mapper.ProductDtoMapper;
import com.floristeriarosy.application.product.port.in.ChangeProductStatusUseCase;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.port.out.ProductWritePort;
import com.floristeriarosy.domain.exception.product.ProductDiscontinuedException;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link ChangeProductStatusUseCase}: changes a product's status. */
@Service
@Transactional
public class ChangeProductStatusService implements ChangeProductStatusUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChangeProductStatusService.class);

  private final ProductReadPort readPort;
  private final ProductWritePort writePort;
  private final ProductCategoryPort categoryPort;
  private final ProductImagePort imagePort;

  /**
   * @param readPort loads the product being changed
   * @param writePort persists the new status
   * @param categoryPort loads the product's categories for the response
   * @param imagePort loads the product's gallery for the response
   */
  public ChangeProductStatusService(
      ProductReadPort readPort,
      ProductWritePort writePort,
      ProductCategoryPort categoryPort,
      ProductImagePort imagePort) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.categoryPort = categoryPort;
    this.imagePort = imagePort;
  }

  /**
   * Changes the product's status. Idempotent on the same status; {@code DISCONTINUED} is terminal
   * (product.md, section 3.2).
   *
   * @param command id of the product to change, plus its new status
   * @return the updated product
   * @throws ProductNotFoundException {@code command.id()} does not exist
   * @throws ProductDiscontinuedException the product is already {@code DISCONTINUED} and {@code
   *     command.status()} is different
   */
  @Override
  public ProductDto execute(ChangeProductStatusCommand command) {
    LOGGER.debug("changeProductStatus id={} status={}", command.id(), command.status());

    ProductId id = ProductId.of(command.id());
    Product product =
        readPort.findById(id).orElseThrow(() -> new ProductNotFoundException("Product " + id + " not found"));

    product.changeStatus(command.status());
    Product saved = writePort.updateStatus(id, product.status());

    List<ProductCategoryRef> categories = categoryPort.findCategories(id);
    List<ProductImageRef> images = imagePort.findImages(id);
    BigDecimal activeSalePrice = readPort.findActiveSalePrice(id).orElse(null);
    ProductDto result = ProductDtoMapper.toDto(saved, activeSalePrice, categories, images);

    LOGGER.debug("changeProductStatus -> id={} status={}", result.id(), result.status());
    return result;
  }
}
