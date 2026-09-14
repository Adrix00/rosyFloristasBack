package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.query.GetOrderQuery;

/** Loads a single order if the caller can prove ownership (order.md §4, §6). */
public interface GetOrderUseCase {

  /**
   * @param query the order to look up and the caller's identity
   * @return the public order view
   */
  OrderDto execute(GetOrderQuery query);
}
