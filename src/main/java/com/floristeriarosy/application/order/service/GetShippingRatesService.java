package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.dto.ShippingBandsDto;
import com.floristeriarosy.application.order.mapper.ShippingBandDtoMapper;
import com.floristeriarosy.application.order.port.in.GetShippingRatesUseCase;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.domain.model.order.ShippingBands;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Implements {@link GetShippingRatesUseCase}: the whole tariff and the current delivery radius. */
@Service
public class GetShippingRatesService implements GetShippingRatesUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetShippingRatesService.class);

  private final ShippingRatePort shippingRatePort;

  /**
   * @param shippingRatePort lists the bands
   */
  public GetShippingRatesService(ShippingRatePort shippingRatePort) {
    this.shippingRatePort = shippingRatePort;
  }

  /**
   * @return every band, active or not, ordered by where it starts, and the delivery radius
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public ShippingBandsDto execute() {
    LOGGER.debug("getShippingRates");
    ShippingBands bands = new ShippingBands(shippingRatePort.findAll());
    BigDecimal radius = bands.deliveryRadiusKm().orElse(null);
    ShippingBandsDto result =
        new ShippingBandsDto(
            bands.orderedByStart().stream().map(ShippingBandDtoMapper::toDto).toList(), radius);
    LOGGER.debug("getShippingRates -> bands={} radius={}", result.bands().size(), radius);
    return result;
  }
}
