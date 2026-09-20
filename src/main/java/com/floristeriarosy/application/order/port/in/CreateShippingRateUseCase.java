package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.command.CreateShippingRateCommand;
import com.floristeriarosy.application.order.dto.ShippingBandDto;

/** Creates a delivery band, keeping the active bands one coherent tariff (order.md §3.7, §7). */
public interface CreateShippingRateUseCase {

  /**
   * @param command the band to create and who is creating it
   * @return the created band
   * @throws com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException the band, or the
   *     resulting set of active bands, breaks a rule; nothing is created
   */
  ShippingBandDto execute(CreateShippingRateCommand command);
}
