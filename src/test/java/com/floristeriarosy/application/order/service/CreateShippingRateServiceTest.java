package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.command.CreateShippingRateCommand;
import com.floristeriarosy.application.order.dto.ShippingBandDto;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import com.floristeriarosy.domain.model.order.ShippingBand;
import com.floristeriarosy.domain.model.order.valueobject.ShippingBandId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link CreateShippingRateService} — order.md §3.7: the resulting active bands stay coherent. */
class CreateShippingRateServiceTest {

  private final ShippingRatePort port = mock(ShippingRatePort.class);
  private final AuditLogPort auditLogPort = mock(AuditLogPort.class);
  private final CreateShippingRateService service = new CreateShippingRateService(port, auditLogPort);
  private final UUID admin = UUID.randomUUID();

  @Test
  void aBandThatExtendsTheTariffIsSavedAndAudited() {
    when(port.findAllForUpdate()).thenReturn(List.of(band(0, 5, true), band(5, 10, true)));
    when(port.save(any())).thenAnswer(call -> call.getArgument(0));

    ShippingBandDto created = service.execute(command(10, 15, true));

    assertThat(created.maxKm()).isEqualByComparingTo("15");
    verify(port).save(any(ShippingBand.class));
    verify(auditLogPort)
        .record(
            admin,
            AuditAction.CREATE,
            "shipping_rate",
            created.id(),
            List.of("min_km", "max_km", "price", "free_from_amount", "active"));
  }

  @Test
  void anInactiveBandNeverBreaksTheTariffWhereverItIs() {
    when(port.findAllForUpdate()).thenReturn(List.of(band(0, 10, true)));
    when(port.save(any())).thenAnswer(call -> call.getArgument(0));

    assertThat(service.execute(command(20, 30, false)).active()).isFalse();
  }

  @Test
  void aBandThatLeavesAGapIsRefusedAndNothingIsSavedOrAudited() {
    when(port.findAllForUpdate()).thenReturn(List.of(band(0, 5, true)));

    assertThatThrownBy(() -> service.execute(command(7, 10, true)))
        .isInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("gap");

    verify(port, never()).save(any());
    verify(auditLogPort, never()).record(any(), any(), any(), any(), any());
  }

  @Test
  void aBandThatOverlapsIsRefused() {
    when(port.findAllForUpdate()).thenReturn(List.of(band(0, 5, true)));

    assertThatThrownBy(() -> service.execute(command(3, 8, true)))
        .isInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("overlap");

    verify(port, never()).save(any());
  }

  @Test
  void aBandBreakingItsOwnInvariantsIsRefused() {
    when(port.findAllForUpdate()).thenReturn(List.of(band(0, 5, true)));

    assertThatThrownBy(() -> service.execute(command(8, 5, true))).isInstanceOf(ShippingRatesInvalidException.class);

    verify(port, never()).save(any());
  }

  private CreateShippingRateCommand command(int minKm, int maxKm, boolean active) {
    return new CreateShippingRateCommand(
        admin, new BigDecimal(minKm), new BigDecimal(maxKm), new BigDecimal("12.00"), null, active);
  }

  private ShippingBand band(int minKm, int maxKm, boolean active) {
    return ShippingBand.create(
        ShippingBandId.newId(), new BigDecimal(minKm), new BigDecimal(maxKm), new BigDecimal("5.00"), null, active);
  }
}
