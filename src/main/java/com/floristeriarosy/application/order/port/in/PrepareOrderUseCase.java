package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.command.PrepareOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.domain.exception.order.OrderInvalidTransitionException;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;

/** {@code PENDING} → {@code PREPARING} (order.md §3.9). */
public interface PrepareOrderUseCase {

  /**
   * @param command the order to prepare and the admin who is preparing it
   * @return the updated order
   * @throws OrderNotFoundException the order does not exist
   * @throws OrderInvalidTransitionException the order is not {@code PENDING}
   */
  OrderDto execute(PrepareOrderCommand command);
}
