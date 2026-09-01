package com.floristeriarosy.application.product.service;

import com.floristeriarosy.application.product.command.ChangeInventoryModeCommand;
import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.application.product.mapper.ProductDtoMapper;
import com.floristeriarosy.application.product.port.in.ChangeInventoryModeUseCase;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductInventoryPort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.exception.product.ProductStockRequiredException;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link ChangeInventoryModeUseCase}: switches a product's inventory mode (product.md, section 3.7). */
@Service
@Transactional
public class ChangeInventoryModeService implements ChangeInventoryModeUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChangeInventoryModeService.class);

  private final ProductReadPort readPort;
  private final ProductInventoryPort inventoryPort;
  private final ProductCategoryPort categoryPort;
  private final ProductImagePort imagePort;

  /**
   * @param readPort loads the product being changed, before and after the write
   * @param inventoryPort activates, adjusts or deactivates managed inventory
   * @param categoryPort loads the product's categories for the response
   * @param imagePort loads the product's gallery for the response
   */
  public ChangeInventoryModeService(
      ProductReadPort readPort,
      ProductInventoryPort inventoryPort,
      ProductCategoryPort categoryPort,
      ProductImagePort imagePort) {
    this.readPort = readPort;
    this.inventoryPort = inventoryPort;
    this.categoryPort = categoryPort;
    this.imagePort = imagePort;
  }

  /**
   * Activates, adjusts or deactivates managed inventory, per the transition table in product.md,
   * section 3.7.
   *
   * @param command id of the product to change, plus the new mode and, if managed, its stock
   * @return the updated product
   * @throws ProductNotFoundException {@code command.id()} does not exist
   * @throws ProductStockRequiredException {@code command.managed()} is {@code true} without a
   *     stock value
   */
  @Override
  public ProductDto execute(ChangeInventoryModeCommand command) {
    LOGGER.debug(
        "changeInventoryMode id={} managed={} stock={} lowStockThreshold={}",
        command.id(),
        command.managed(),
        command.stock(),
        command.lowStockThreshold());

    ProductId id = ProductId.of(command.id());
    Product product =
        readPort.findById(id).orElseThrow(() -> new ProductNotFoundException("Product " + id + " not found"));

    if (command.managed()) {
      if (command.stock() == null) {
        throw new ProductStockRequiredException("Activating inventory management requires an initial stock");
      }
      if (product.stock() == null) {
        inventoryPort.initializeStock(id, command.stock(), command.lowStockThreshold(), command.note());
      } else {
        inventoryPort.adjustStock(id, command.stock(), command.lowStockThreshold(), command.note());
      }
    } else if (product.stock() != null) {
      inventoryPort.disableStockManagement(id);
    }

    Product refreshed = readPort.findById(id).orElseThrow();
    List<ProductCategoryRef> categories = categoryPort.findCategories(id);
    List<ProductImageRef> images = imagePort.findImages(id);
    BigDecimal activeSalePrice = readPort.findActiveSalePrice(id).orElse(null);
    ProductDto result = ProductDtoMapper.toDto(refreshed, activeSalePrice, categories, images);

    LOGGER.debug("changeInventoryMode -> id={} stock={}", id, result.stock());
    return result;
  }
}
