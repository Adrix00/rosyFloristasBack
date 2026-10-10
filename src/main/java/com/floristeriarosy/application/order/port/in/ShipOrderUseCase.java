package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.command.ShipOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.domain.exception.order.OrderInvalidTransitionException;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;

/** {@code PREPARING} → {@code OUT_FOR_DELIVERY} for {@code DELIVERY} orders (order.md §3.9). */
public interface ShipOrderUseCase {

  /**
   * @param command the order to send out and the admin who is sending it
   * @return the updated order
   * @throws OrderNotFoundException the order does not exist
   * @throws OrderInvalidTransitionException the order is not {@code PREPARING} or is not a
   *     {@code DELIVERY} order
   */
  OrderDto execute(ShipOrderCommand command);
}
