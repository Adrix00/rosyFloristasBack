package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.command.CompleteOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.domain.exception.order.OrderInvalidTransitionException;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;

/** Marks an order {@code DELIVERED} (order.md §3.9). */
public interface CompleteOrderUseCase {

  /**
   * @param command the order to complete and the admin who is completing it
   * @return the updated order
   * @throws OrderNotFoundException the order does not exist
   * @throws OrderInvalidTransitionException the order is not in the expected status for its
   *     fulfillment mode
   */
  OrderDto execute(CompleteOrderCommand command);
}
