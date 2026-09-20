package com.floristeriarosy.infrastructure.persistence.mapper.order;

import com.floristeriarosy.domain.model.order.ShippingBand;
import com.floristeriarosy.domain.model.order.valueobject.ShippingBandId;
import com.floristeriarosy.infrastructure.persistence.entity.order.ShippingRateEntity;
import org.springframework.stereotype.Component;

/** Converts between the domain {@link ShippingBand} and its JPA {@link ShippingRateEntity}. */
@Component
public class ShippingBandPersistenceMapper {

  /**
   * @param band the domain band
   * @return a new, detached entity carrying the band's state
   */
  public ShippingRateEntity toEntity(ShippingBand band) {
    return new ShippingRateEntity(
        band.id().value(),
        band.minKm(),
        band.maxKm(),
        band.price(),
        band.freeFromAmount(),
        band.active(),
        band.createdAt(),
        band.updatedAt());
  }

  /**
   * @param entity the persisted row
   * @return the rebuilt domain band
   */
  public ShippingBand toDomain(ShippingRateEntity entity) {
    return ShippingBand.reconstitute(
        ShippingBandId.of(entity.getId()),
        entity.getMinKm(),
        entity.getMaxKm(),
        entity.getPrice(),
        entity.getFreeFromAmount(),
        entity.isActive(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
