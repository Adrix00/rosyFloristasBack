package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.command.CancelOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.domain.exception.order.OrderInvalidTransitionException;
import com.floristeriarosy.domain.exception.order.OrderNotCancelableException;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;
import com.floristeriarosy.domain.exception.order.OrderValidationFailedException;

/**
 * {@code PENDING} or {@code PREPARING} → {@code CANCELLED} (order.md §3.9, §3.10). Admin may cancel
 * from {@code PENDING} or {@code PREPARING}; the owning customer may cancel only from {@code PENDING}.
 */
public interface CancelOrderUseCase {

  /**
   * @param command the order to cancel and who is cancelling it
   * @return the updated order
   * @throws OrderNotFoundException the order does not exist, or the customer does not own it
   * @throws OrderValidationFailedException an admin cancel is missing the required reason
   * @throws OrderInvalidTransitionException an admin tried to cancel from a non-allowed status
   * @throws OrderNotCancelableException a customer tried to cancel from a non-allowed status
   */
  OrderDto execute(CancelOrderCommand command);
}
