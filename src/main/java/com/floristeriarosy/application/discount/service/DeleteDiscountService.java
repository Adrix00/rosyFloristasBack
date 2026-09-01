package com.floristeriarosy.application.discount.service;

import com.floristeriarosy.application.discount.command.DeleteDiscountCommand;
import com.floristeriarosy.application.discount.port.in.DeleteDiscountUseCase;
import com.floristeriarosy.application.discount.port.out.DiscountReadPort;
import com.floristeriarosy.application.discount.port.out.DiscountWritePort;
import com.floristeriarosy.domain.exception.discount.DiscountAlreadyStartedException;
import com.floristeriarosy.domain.exception.discount.DiscountNotFoundException;
import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link DeleteDiscountUseCase}: permanently removes a discount that has not started
 * yet (product-discounts.md, section 3.4).
 */
@Service
@Transactional
public class DeleteDiscountService implements DeleteDiscountUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeleteDiscountService.class);

  private final DiscountReadPort readPort;
  private final DiscountWritePort writePort;

  /**
   * @param readPort loads the discount being deleted, to check it has not started yet
   * @param writePort performs the delete
   */
  public DeleteDiscountService(DiscountReadPort readPort, DiscountWritePort writePort) {
    this.readPort = readPort;
    this.writePort = writePort;
  }

  /**
   * @param command id of the discount to delete
   * @throws DiscountNotFoundException {@code command.id()} does not exist
   * @throws DiscountAlreadyStartedException the discount has already started; it must be closed
   *     via {@code POST /discounts/{id}/end} instead
   */
  @Override
  public void execute(DeleteDiscountCommand command) {
    LOGGER.debug("deleteDiscount id={}", command.id());

    DiscountId id = DiscountId.of(command.id());
    Discount discount =
        readPort.findById(id).orElseThrow(() -> new DiscountNotFoundException("Discount " + id + " not found"));

    discount.requireNotStarted();
    writePort.delete(id);

    LOGGER.debug("deleteDiscount -> id={} deleted", id);
  }
}
