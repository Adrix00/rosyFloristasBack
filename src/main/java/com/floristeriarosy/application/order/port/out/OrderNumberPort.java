package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;

/** Mints the next business identifier from {@code order_number_seq} (order.md, rule 3.1). */
public interface OrderNumberPort {

  /**
   * @param channel the sales channel, prefixes the formatted number
   * @return the next order number
   */
  OrderNumber next(OrderChannel channel);
}
