package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.dto.ShippingBandsDto;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.domain.model.order.ShippingBand;
import com.floristeriarosy.domain.model.order.valueobject.ShippingBandId;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link GetShippingRatesService} — every band ordered by start, plus the current radius. */
class GetShippingRatesServiceTest {

  private final ShippingRatePort port = mock(ShippingRatePort.class);
  private final GetShippingRatesService service = new GetShippingRatesService(port);

  @Test
  void listsEveryBandByStartWithTheRadiusOfTheFurthestActiveOne() {
    when(port.findAll()).thenReturn(List.of(band(5, 10, true), band(0, 5, true), band(10, 20, false)));

    ShippingBandsDto result = service.execute();

    assertThat(result.bands()).extracting(b -> b.minKm().intValue()).containsExactly(0, 5, 10);
    assertThat(result.deliveryRadiusKm()).isEqualByComparingTo("10");
  }

  @Test
  void theRadiusIsNullWhenNoBandIsActive() {
    when(port.findAll()).thenReturn(List.of(band(0, 5, false)));

    assertThat(service.execute().deliveryRadiusKm()).isNull();
  }

  private ShippingBand band(int minKm, int maxKm, boolean active) {
    return ShippingBand.create(
        ShippingBandId.newId(), new BigDecimal(minKm), new BigDecimal(maxKm), new BigDecimal("5.00"), null, active);
  }
}
