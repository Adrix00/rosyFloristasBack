package com.floristeriarosy.application.order.mapper;

import com.floristeriarosy.application.order.dto.ShippingBandDto;
import com.floristeriarosy.domain.model.order.ShippingBand;

/** Maps a domain {@link ShippingBand} to its {@link ShippingBandDto}. */
public final class ShippingBandDtoMapper {

  private ShippingBandDtoMapper() {}

  /**
   * @param band the domain band
   * @return its DTO
   */
  public static ShippingBandDto toDto(ShippingBand band) {
    return new ShippingBandDto(
        band.id().value(),
        band.minKm(),
        band.maxKm(),
        band.price(),
        band.freeFromAmount(),
        band.active());
  }
}
