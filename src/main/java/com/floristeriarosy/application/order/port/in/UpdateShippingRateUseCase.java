package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.command.UpdateShippingRateCommand;
import com.floristeriarosy.application.order.dto.ShippingBandDto;

/** Replaces a delivery band, keeping the active bands one coherent tariff (order.md §3.7, §7). */
public interface UpdateShippingRateUseCase {

  /**
   * @param command the band to change, its new fields and who is changing it
   * @return the updated band
   * @throws com.floristeriarosy.domain.exception.order.ShippingRateNotFoundException the band does
   *     not exist
   * @throws com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException the band, or the
   *     resulting set of active bands, breaks a rule; nothing is changed
   */
  ShippingBandDto execute(UpdateShippingRateCommand command);
}
