package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.command.UpdateShippingRateCommand;
import com.floristeriarosy.application.order.dto.ShippingBandDto;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.domain.exception.order.ShippingRateNotFoundException;
import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import com.floristeriarosy.domain.model.order.ShippingBand;
import com.floristeriarosy.domain.model.order.valueobject.ShippingBandId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link UpdateShippingRateService} — order.md §3.7: the resulting active bands stay coherent. */
class UpdateShippingRateServiceTest {

  private final ShippingRatePort port = mock(ShippingRatePort.class);
  private final AuditLogPort auditLogPort = mock(AuditLogPort.class);
  private final UpdateShippingRateService service = new UpdateShippingRateService(port, auditLogPort);
  private final UUID admin = UUID.randomUUID();

  private final ShippingBand near = band(0, 1);
  private final ShippingBand middle = band(1, 5);
  private final ShippingBand far = band(5, 10);

  @Test
  void aPriceChangeIsSavedAndAuditedWithOnlyTheChangedField() {
    when(port.findAllUnderTariffLock()).thenReturn(List.of(near, middle, far));
    when(port.save(any())).thenAnswer(call -> call.getArgument(0));

    ShippingBandDto updated = service.execute(command(middle, 1, 5, "9.50", true));

    assertThat(updated.price()).isEqualByComparingTo("9.50");
    verify(auditLogPort).record(admin, AuditAction.UPDATE, "shipping_rate", middle.id().value(), List.of("price"));
  }

  @Test
  void extendingTheLastBandIsCoherent() {
    when(port.findAllUnderTariffLock()).thenReturn(List.of(near, middle, far));
    when(port.save(any())).thenAnswer(call -> call.getArgument(0));

    assertThat(service.execute(command(far, 5, 15, "10.00", true)).maxKm()).isEqualByComparingTo("15");
  }

  @Test
  void deactivatingTheLastBandShrinksTheTariffButStaysCoherent() {
    when(port.findAllUnderTariffLock()).thenReturn(List.of(near, middle, far));
    when(port.save(any())).thenAnswer(call -> call.getArgument(0));

    assertThat(service.execute(command(far, 5, 10, "10.00", false)).active()).isFalse();
  }

  @Test
  void deactivatingTheOnlyActiveBandIsRefusedAndNothingIsSavedOrAudited() {
    ShippingBand only = band(0, 10);
    when(port.findAllUnderTariffLock()).thenReturn(List.of(only));

    assertThatThrownBy(() -> service.execute(command(only, 0, 10, "5.00", false)))
        .isInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("At least one");

    verify(port, never()).save(any());
    verify(auditLogPort, never()).record(any(), any(), any(), any(), any());
  }

  @Test
  void deactivatingAMiddleBandLeavesAGapAndIsRefused() {
    when(port.findAllUnderTariffLock()).thenReturn(List.of(near, middle, far));

    assertThatThrownBy(() -> service.execute(command(middle, 1, 5, "8.00", false)))
        .isInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("gap");

    verify(port, never()).save(any());
  }

  @Test
  void movingTheFirstBandAwayFromZeroIsRefused() {
    when(port.findAllUnderTariffLock()).thenReturn(List.of(near, middle, far));

    assertThatThrownBy(() -> service.execute(command(near, 0.5, 1, "5.00", true)))
        .isInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("start at 0 km");
  }

  @Test
  void aBandThatDoesNotExistIsNotFound() {
    when(port.findAllUnderTariffLock()).thenReturn(List.of(near, middle, far));

    assertThatThrownBy(() -> service.execute(new UpdateShippingRateCommand(UUID.randomUUID(), admin, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, null, true)))
        .isInstanceOf(ShippingRateNotFoundException.class);

    verify(port, never()).save(any());
  }

  @Test
  void aReplacementThatChangesNothingSavesAndAuditsNothing() {
    when(port.findAllUnderTariffLock()).thenReturn(List.of(near, middle, far));

    ShippingBandDto same = service.execute(command(middle, 1, 5, "5.00", true));

    assertThat(same.id()).isEqualTo(middle.id().value());
    verify(port, never()).save(any());
    verify(auditLogPort, never()).record(any(), any(), any(), any(), any());
  }

  private UpdateShippingRateCommand command(ShippingBand target, double minKm, double maxKm, String price, boolean active) {
    return new UpdateShippingRateCommand(
        target.id().value(), admin, BigDecimal.valueOf(minKm), BigDecimal.valueOf(maxKm), new BigDecimal(price), null, active);
  }

  private ShippingBand band(int minKm, int maxKm) {
    return ShippingBand.create(
        ShippingBandId.newId(), new BigDecimal(minKm), new BigDecimal(maxKm), new BigDecimal("5.00"), null, true);
  }
}
