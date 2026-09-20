package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.dto.ShippingBandsDto;

/** Lists the delivery tariff for the admin panel (order.md §4, §7). */
public interface GetShippingRatesUseCase {

  /**
   * @return every band, active or not, and the current delivery radius
   */
  ShippingBandsDto execute();
}
