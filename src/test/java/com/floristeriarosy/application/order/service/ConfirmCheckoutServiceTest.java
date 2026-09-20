package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.cart.command.ClearCartCommand;
import com.floristeriarosy.application.cart.port.in.ClearCartUseCase;
import com.floristeriarosy.application.customer.port.in.FindOrCreateGuestCustomerUseCase;
import com.floristeriarosy.application.notification.port.in.RegisterNotificationUseCase;
import com.floristeriarosy.application.order.command.ConfirmCheckoutCommand;
import com.floristeriarosy.application.order.dto.ConfirmCheckoutOutcome;
import com.floristeriarosy.application.order.dto.IdempotencyLock;
import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.application.order.port.out.OrderWritePort;
import com.floristeriarosy.application.order.support.CheckoutReservationReleaser;
import com.floristeriarosy.application.order.support.PendingCheckoutPayloadReader;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** {@link ConfirmCheckoutService} — Fase 3a (approve) and Fase 3b (decline), order.md §3.2. */
class ConfirmCheckoutServiceTest {

  private final CheckoutIdempotencyPort idempotencyPort = mock(CheckoutIdempotencyPort.class);
  private final OrderPaymentPort orderPaymentPort = mock(OrderPaymentPort.class);
  private final OrderWritePort orderWritePort = mock(OrderWritePort.class);
  private final FindOrCreateGuestCustomerUseCase findOrCreateGuestCustomerUseCase =
      mock(FindOrCreateGuestCustomerUseCase.class);
  private final RegisterNotificationUseCase registerNotificationUseCase =
      mock(RegisterNotificationUseCase.class);
  private final ClearCartUseCase clearCartUseCase = mock(ClearCartUseCase.class);
  private final PiiCryptoPort piiCryptoPort = mock(PiiCryptoPort.class);
  private final CheckoutReservationReleaser reservationReleaser =
      mock(CheckoutReservationReleaser.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private ConfirmCheckoutService service;

  @BeforeEach
  void setUp() throws Exception {
    when(piiCryptoPort.encrypt(any())).thenReturn("enc".getBytes(StandardCharsets.UTF_8));
    when(piiCryptoPort.hmac(any())).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(piiCryptoPort.decrypt(any())).thenReturn(objectMapper.writeValueAsString(payload()));

    service =
        new ConfirmCheckoutService(
            idempotencyPort,
            orderPaymentPort,
            orderWritePort,
            findOrCreateGuestCustomerUseCase,
            registerNotificationUseCase,
            clearCartUseCase,
            piiCryptoPort,
            new PendingCheckoutPayloadReader(piiCryptoPort, objectMapper),
            reservationReleaser);
  }

  private PendingCheckoutPayload payload() {
    return new PendingCheckoutPayload(
        "WEB",
        "NONE",
        "WEB-20260914-000001",
        null,
        "session-1",
        "Ana",
        "García",
        "ana@example.com",
        "600111222",
        List.of(
            new PendingCheckoutPayload.Item(
                UUID.randomUUID(), "Rosa", Map.of(), new BigDecimal("10.00"), null, null, 2)),
        null,
        null,
        new BigDecimal("20.00"),
        BigDecimal.ZERO);
  }

  @Test
  void approvedMaterializesTheOrderAndClearsTheCart() {
    IdempotencyLock lock =
        new IdempotencyLock("PENDING", new byte[0], UUID.randomUUID(), new byte[0]);
    when(idempotencyPort.findForUpdate("idem-1")).thenReturn(Optional.of(lock));
    when(findOrCreateGuestCustomerUseCase.execute(any()))
        .thenReturn(CustomerId.of(UUID.randomUUID()));

    service.execute(
        new ConfirmCheckoutCommand(
            "idem-1", OrderNumber.of("WEB-20260914-000001"), true, "redsys-auth-1", null));

    verify(orderWritePort).save(any());
    verify(clearCartUseCase).execute(any(ClearCartCommand.class));
    verify(registerNotificationUseCase, times(2)).execute(any());
    verify(idempotencyPort).markCompleted("idem-1", 202);
  }

  @Test
  void alreadyCompletedIsIgnoredNotReprocessed() {
    IdempotencyLock lock =
        new IdempotencyLock("COMPLETED", new byte[0], UUID.randomUUID(), new byte[0]);
    when(idempotencyPort.findForUpdate("idem-2")).thenReturn(Optional.of(lock));

    service.execute(
        new ConfirmCheckoutCommand(
            "idem-2", OrderNumber.of("WEB-20260914-000002"), true, "redsys-auth-2", null));

    verify(orderWritePort, never()).save(any());
    verify(registerNotificationUseCase, never()).execute(any());
  }

  @Test
  void declinedReversesReservationAndLeavesTheRowFailedSoARetryRestartsFromFase1() {
    IdempotencyLock lock =
        new IdempotencyLock("PENDING", new byte[0], UUID.randomUUID(), new byte[0]);
    when(idempotencyPort.findForUpdate("idem-3")).thenReturn(Optional.of(lock));

    service.execute(
        new ConfirmCheckoutCommand(
            "idem-3", OrderNumber.of("WEB-20260914-000001"), false, null, "Ds_Response=0199"));

    verify(reservationReleaser).release(any(PendingCheckoutPayload.class));
    verify(orderPaymentPort, never()).confirmCharge(any(), any(), any(), any(), any());
    verify(orderWritePort, never()).save(any());
    verify(idempotencyPort).markFailed("idem-3");
    verify(idempotencyPort, never()).markCompleted(any(), anyInt());
  }

  @Test
  void aFailedRowIsReportedAsExpiredAndTouchesNothing() {
    IdempotencyLock lock =
        new IdempotencyLock("FAILED", new byte[0], UUID.randomUUID(), null);
    when(idempotencyPort.findForUpdate("idem-5")).thenReturn(Optional.of(lock));

    ConfirmCheckoutOutcome outcome =
        service.execute(
            new ConfirmCheckoutCommand(
                "idem-5", OrderNumber.of("WEB-20260914-000005"), true, "redsys-auth-5", null));

    assertThat(outcome).isEqualTo(ConfirmCheckoutOutcome.EXPIRED);
    verify(orderWritePort, never()).save(any());
    verify(reservationReleaser, never()).release(any());
    verify(idempotencyPort, never()).markCompleted(any(), anyInt());
  }

  @Test
  void aNotificationOfAPreviousAttemptFoundUnderTheLockIsReportedAsExpiredAndTouchesNothing() {
    IdempotencyLock lock =
        new IdempotencyLock("PENDING", new byte[0], UUID.randomUUID(), new byte[0]);
    when(idempotencyPort.findForUpdate("idem-8")).thenReturn(Optional.of(lock));

    ConfirmCheckoutOutcome outcome =
        service.execute(
            new ConfirmCheckoutCommand(
                "idem-8", OrderNumber.of("WEB-20260914-000099"), true, "redsys-auth-8", null));

    assertThat(outcome).isEqualTo(ConfirmCheckoutOutcome.EXPIRED);
    verify(orderWritePort, never()).save(any());
    verify(reservationReleaser, never()).release(any());
    verify(idempotencyPort, never()).markCompleted(any(), anyInt());
    verify(idempotencyPort, never()).markFailed(any());
  }

  @Test
  void pendingApprovedIsResolvedAndACompletedRowIsIgnored() {
    when(idempotencyPort.findForUpdate("idem-6"))
        .thenReturn(Optional.of(new IdempotencyLock("PENDING", new byte[0], UUID.randomUUID(), new byte[0])));
    when(idempotencyPort.findForUpdate("idem-7"))
        .thenReturn(Optional.of(new IdempotencyLock("COMPLETED", new byte[0], UUID.randomUUID(), null)));
    when(findOrCreateGuestCustomerUseCase.execute(any())).thenReturn(CustomerId.of(UUID.randomUUID()));

    ConfirmCheckoutOutcome resolved =
        service.execute(
            new ConfirmCheckoutCommand(
                "idem-6", OrderNumber.of("WEB-20260914-000001"), true, "redsys-auth-6", null));
    ConfirmCheckoutOutcome ignored =
        service.execute(
            new ConfirmCheckoutCommand(
                "idem-7", OrderNumber.of("WEB-20260914-000007"), true, "redsys-auth-7", null));

    assertThat(resolved).isEqualTo(ConfirmCheckoutOutcome.RESOLVED);
    assertThat(ignored).isEqualTo(ConfirmCheckoutOutcome.IGNORED);
  }

  @Test
  void unknownKeyIsIgnoredNotReprocessed() {
    when(idempotencyPort.findForUpdate("idem-4")).thenReturn(Optional.empty());

    service.execute(
        new ConfirmCheckoutCommand(
            "idem-4", OrderNumber.of("WEB-20260914-000004"), true, "redsys-auth-4", null));

    verify(orderWritePort, never()).save(any());
    verify(idempotencyPort, never()).markCompleted(any(), anyInt());
  }
}
