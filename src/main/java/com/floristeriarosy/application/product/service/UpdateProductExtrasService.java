package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.command.UpdateProductExtrasCommand;
import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.application.product.mapper.ProductDtoMapper;
import com.floristeriarosy.application.product.port.in.UpdateProductExtrasUseCase;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.port.out.ProductSuggestionPort;
import com.floristeriarosy.domain.exception.product.ProductNotAnExtraException;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.exception.product.ProductSuggestsItselfException;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link UpdateProductExtrasUseCase}: replaces a product's full set of suggested extras. */
@Service
@Transactional
public class UpdateProductExtrasService implements UpdateProductExtrasUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateProductExtrasService.class);

  private final ProductReadPort readPort;
  private final ProductCategoryPort categoryPort;
  private final ProductImagePort imagePort;
  private final ProductSuggestionPort suggestionPort;

  /**
   * @param readPort loads the product being updated and every candidate extra
   * @param categoryPort loads the product's categories for the response
   * @param imagePort loads the product's gallery for the response
   * @param suggestionPort replaces the product's suggestions
   */
  public UpdateProductExtrasService(
      ProductReadPort readPort,
      ProductCategoryPort categoryPort,
      ProductImagePort imagePort,
      ProductSuggestionPort suggestionPort) {
    this.readPort = readPort;
    this.categoryPort = categoryPort;
    this.imagePort = imagePort;
    this.suggestionPort = suggestionPort;
  }

  /**
   * Replaces every suggested extra. Only products with {@code is_extra = true} may be suggested
   * (product.md, section 3.6).
   *
   * @param command id of the product to update, plus its complete new suggestion set
   * @return the updated product
   * @throws ProductNotFoundException {@code command.id()} or one of {@code
   *     command.extraProductIds()} does not exist
   * @throws ProductNotAnExtraException one of {@code command.extraProductIds()} has {@code
   *     is_extra = false}
   * @throws ProductSuggestsItselfException {@code command.id()} is among {@code
   *     command.extraProductIds()}
   */
  @Override
  public ProductDto execute(UpdateProductExtrasCommand command) {
    LOGGER.debug("updateProductExtras id={} extraProductIds={}", command.id(), command.extraProductIds().size());

    ProductId id = ProductId.of(command.id());
    Product product =
        readPort.findById(id).orElseThrow(() -> new ProductNotFoundException("Product " + id + " not found"));

    for (UUID extraId : command.extraProductIds()) {
      if (extraId.equals(command.id())) {
        throw new ProductSuggestsItselfException("Product " + id + " cannot suggest itself");
      }
      ProductId candidateId = ProductId.of(extraId);
      Product candidate =
          readPort
              .findById(candidateId)
              .orElseThrow(() -> new ProductNotFoundException("Product " + candidateId + " not found"));
      if (!candidate.isExtra()) {
        throw new ProductNotAnExtraException("Product " + candidateId + " has is_extra = false");
      }
    }

    suggestionPort.replaceSuggestions(id, command.extraProductIds().stream().map(ProductId::of).toList());

    List<ProductCategoryRef> categories = categoryPort.findCategories(id);
    List<ProductImageRef> images = imagePort.findImages(id);
    BigDecimal activeSalePrice = readPort.findActiveSalePrice(id).orElse(null);
    ProductDto result = ProductDtoMapper.toDto(product, activeSalePrice, categories, images);

    LOGGER.debug("updateProductExtras -> id={} count={}", id, command.extraProductIds().size());
    return result;
  }
}
