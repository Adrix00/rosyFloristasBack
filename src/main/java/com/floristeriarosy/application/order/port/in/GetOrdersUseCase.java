package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.query.GetOrdersQuery;
import java.util.List;

/** Lists the orders belonging to an authenticated customer (order.md §4, §6). */
public interface GetOrdersUseCase {

  /**
   * @param query the customer whose orders to list
   * @return the customer's orders, newest first
   */
  List<OrderDto> execute(GetOrdersQuery query);
}
