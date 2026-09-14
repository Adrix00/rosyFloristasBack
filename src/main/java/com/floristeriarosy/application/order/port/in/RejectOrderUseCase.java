package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.command.RejectOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.domain.exception.order.OrderInvalidTransitionException;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;

/** {@code PENDING} → {@code REJECTED}, admin-only (order.md §3.9, §3.10). */
public interface RejectOrderUseCase {

  /**
   * @param command the order to reject, the admin, and the reason
   * @return the updated order
   * @throws OrderNotFoundException the order does not exist
   * @throws OrderInvalidTransitionException the order is not {@code PENDING}
   */
  OrderDto execute(RejectOrderCommand command);
}
