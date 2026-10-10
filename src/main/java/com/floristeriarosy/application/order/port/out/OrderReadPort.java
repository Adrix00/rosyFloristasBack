package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

  /**
   * @param customerId the customer whose orders to list
   * @return the customer's orders, newest first
   */
  List<Order> findByCustomerId(UUID customerId);
}
