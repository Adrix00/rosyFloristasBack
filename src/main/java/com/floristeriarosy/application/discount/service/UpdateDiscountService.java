package com.floristeriarosy.application.discount.service;

import com.floristeriarosy.application.discount.command.UpdateDiscountCommand;
import com.floristeriarosy.application.discount.dto.DiscountDto;
import com.floristeriarosy.application.discount.mapper.DiscountDtoMapper;
import com.floristeriarosy.application.discount.port.in.UpdateDiscountUseCase;
import com.floristeriarosy.application.discount.port.out.DiscountReadPort;
import com.floristeriarosy.application.discount.port.out.DiscountWritePort;
import com.floristeriarosy.domain.exception.discount.DiscountLimitBelowSoldException;
import com.floristeriarosy.domain.exception.discount.DiscountNotEditableException;
import com.floristeriarosy.domain.exception.discount.DiscountNotFoundException;
import com.floristeriarosy.domain.exception.discount.DiscountOverlapException;
import com.floristeriarosy.domain.exception.discount.DiscountPeriodInvalidException;
import com.floristeriarosy.domain.exception.discount.DiscountPriceNotLowerException;
import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link UpdateDiscountUseCase}: partial edit of a discount, per the editability rules
 * of product-discounts.md, section 3.3.
 */
@Service
@Transactional
public class UpdateDiscountService implements UpdateDiscountUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateDiscountService.class);

  private final DiscountReadPort readPort;
  private final DiscountWritePort writePort;

  /**
   * @param readPort loads the discount being updated
   * @param writePort persists the updated discount
   */
  public UpdateDiscountService(DiscountReadPort readPort, DiscountWritePort writePort) {
    this.readPort = readPort;
    this.writePort = writePort;
  }

  /**
   * @param command id of the discount to update, plus the requested new field values
   * @return the updated discount
   * @throws DiscountNotFoundException {@code command.id()} does not exist
   * @throws DiscountNotEditableException a changed field is not editable in the current state
   * @throws DiscountPeriodInvalidException the new {@code endsAt} is not in the future
   * @throws DiscountLimitBelowSoldException the new {@code quantityLimit} is below units already
   *     sold
   * @throws DiscountPriceNotLowerException the new {@code salePrice} is not lower than {@code
   *     originalPrice}
   * @throws DiscountOverlapException the new vigency window overlaps another discount of the same
   *     product
   */
  @Override
  public DiscountDto execute(UpdateDiscountCommand command) {
    LOGGER.debug(
        "updateDiscount id={} startsAt={} endsAt={} quantityLimit={} salePrice={}",
        command.id(),
        command.startsAt(),
        command.endsAt(),
        command.quantityLimit(),
        command.salePrice());

    DiscountId id = DiscountId.of(command.id());
    Discount discount =
        readPort.findById(id).orElseThrow(() -> new DiscountNotFoundException("Discount " + id + " not found"));

    discount.update(command.startsAt(), command.endsAt(), command.quantityLimit(), command.salePrice());
    Discount saved = writePort.save(discount);

    DiscountDto result = DiscountDtoMapper.toDto(saved);
    LOGGER.debug("updateDiscount -> id={} state={}", result.id(), result.state());
    return result;
  }
}
