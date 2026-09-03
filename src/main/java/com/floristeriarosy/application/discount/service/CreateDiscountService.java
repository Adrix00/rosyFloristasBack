package com.floristeriarosy.application.discount.service;

import com.floristeriarosy.application.discount.command.CreateDiscountCommand;
import com.floristeriarosy.application.discount.dto.DiscountDto;
import com.floristeriarosy.application.discount.mapper.DiscountDtoMapper;
import com.floristeriarosy.application.discount.port.in.CreateDiscountUseCase;
import com.floristeriarosy.application.discount.port.out.DiscountWritePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.domain.exception.discount.DiscountOverlapException;
import com.floristeriarosy.domain.exception.discount.DiscountPeriodInvalidException;
import com.floristeriarosy.domain.exception.discount.DiscountPriceNotLowerException;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link CreateDiscountUseCase}: creates a new promotional price for a product, freezing
 * its current price as {@code originalPrice} (product-discounts.md, section 1, section 5).
 */
@Service
@Transactional
public class CreateDiscountService implements CreateDiscountUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateDiscountService.class);

  private final ProductReadPort productReadPort;
  private final DiscountWritePort writePort;

  /**
   * @param productReadPort loads the product being discounted, for its current price
   * @param writePort persists the created discount
   */
  public CreateDiscountService(ProductReadPort productReadPort, DiscountWritePort writePort) {
    this.productReadPort = productReadPort;
    this.writePort = writePort;
  }

  /**
   * @param command the product to discount, plus the promotion's fields
   * @return the created discount
   * @throws ProductNotFoundException {@code command.productId()} does not exist
   * @throws DiscountPeriodInvalidException {@code endsAt} is not after {@code startsAt}, or is not
   *     in the future
   * @throws DiscountPriceNotLowerException {@code salePrice} is not lower than the product's
   *     current price
   * @throws DiscountOverlapException the vigency window overlaps another discount of the same
   *     product
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public DiscountDto execute(CreateDiscountCommand command) {
    LOGGER.debug(
        "createDiscount productId={} salePrice={} startsAt={} endsAt={} quantityLimit={}",
        command.productId(),
        command.salePrice(),
        command.startsAt(),
        command.endsAt(),
        command.quantityLimit());

    ProductId productId = ProductId.of(command.productId());
    Product product =
        productReadPort
            .findById(productId)
            .orElseThrow(() -> new ProductNotFoundException("Product " + productId + " not found"));

    if (!command.endsAt().isAfter(command.startsAt())) {
      throw new DiscountPeriodInvalidException("endsAt must be after startsAt");
    }
    if (!command.endsAt().isAfter(Instant.now())) {
      throw new DiscountPeriodInvalidException("endsAt must be in the future");
    }
    if (command.salePrice().compareTo(product.price()) >= 0) {
      throw new DiscountPriceNotLowerException(
          "salePrice must be lower than the product's current price " + product.price());
    }

    Discount discount =
        Discount.create(
            DiscountId.newId(),
            productId,
            product.price(),
            command.salePrice(),
            command.startsAt(),
            command.endsAt(),
            command.quantityLimit());
    Discount saved = writePort.save(discount);

    DiscountDto result = DiscountDtoMapper.toDto(saved);
    LOGGER.debug("createDiscount -> id={} state={}", result.id(), result.state());
    return result;
  }
}
