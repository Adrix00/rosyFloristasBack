package com.floristeriarosy.application.discount.service;

import com.floristeriarosy.application.discount.command.EndDiscountCommand;
import com.floristeriarosy.application.discount.dto.DiscountDto;
import com.floristeriarosy.application.discount.mapper.DiscountDtoMapper;
import com.floristeriarosy.application.discount.port.in.EndDiscountUseCase;
import com.floristeriarosy.application.discount.port.out.DiscountReadPort;
import com.floristeriarosy.application.discount.port.out.DiscountWritePort;
import com.floristeriarosy.domain.exception.discount.DiscountNotFoundException;
import com.floristeriarosy.domain.exception.discount.DiscountPeriodInvalidException;
import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link EndDiscountUseCase}: closes a discount now instead of deleting it, so its
 * history and any {@code order_items.discount_id} references survive (product-discounts.md, section
 * 3.4).
 */
@Service
@Transactional
public class EndDiscountService implements EndDiscountUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndDiscountService.class);

  private final DiscountReadPort readPort;
  private final DiscountWritePort writePort;

  /**
   * @param readPort loads the discount being closed
   * @param writePort sets {@code ends_at = now()} at the database
   */
  public EndDiscountService(DiscountReadPort readPort, DiscountWritePort writePort) {
    this.readPort = readPort;
    this.writePort = writePort;
  }

  /**
   * @param command id of the discount to close
   * @return the closed discount
   * @throws DiscountNotFoundException {@code command.id()} does not exist
   * @throws DiscountPeriodInvalidException this discount has not started yet (delete it instead),
   *     or has already ended
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public DiscountDto execute(EndDiscountCommand command) {
    LOGGER.debug("endDiscount id={}", command.id());

    DiscountId id = DiscountId.of(command.id());
    Discount discount =
        readPort
            .findById(id)
            .orElseThrow(() -> new DiscountNotFoundException("Discount " + id + " not found"));

    discount.end();
    Discount saved = writePort.endNow(id);

    DiscountDto result = DiscountDtoMapper.toDto(saved);
    LOGGER.debug("endDiscount -> id={} endsAt={}", result.id(), result.endsAt());
    return result;
  }
}
