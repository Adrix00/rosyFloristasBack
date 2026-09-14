package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.util.Optional;

/** Read access to {@code orders} (order.md, section 8). */
public interface OrderReadPort {

  /**
   * @param id the order to load
   * @return the order, if it exists
   */
  Optional<Order> findById(OrderId id);

  /**
   * @param orderNumber the business identifier to look up
   * @return the order, if it exists
   */
  Optional<Order> findByOrderNumber(OrderNumber orderNumber);
}
