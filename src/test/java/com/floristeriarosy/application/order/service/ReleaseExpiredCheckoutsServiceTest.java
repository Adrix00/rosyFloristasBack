package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.dto.IdempotencyLock;
import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.support.CheckoutReservationReleaser;
import com.floristeriarosy.application.order.support.PendingCheckoutPayloadReader;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/** {@link ReleaseExpiredCheckoutsService} — abandoned-checkout cleanup (ADR-017). */
class ReleaseExpiredCheckoutsServiceTest {

  private final CheckoutIdempotencyPort idempotencyPort = mock(CheckoutIdempotencyPort.class);
  private final PiiCryptoPort piiCryptoPort = mock(PiiCryptoPort.class);
  private final CheckoutReservationReleaser releaser = mock(CheckoutReservationReleaser.class);
  private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private ReleaseExpiredCheckoutsService service;

  @BeforeEach
  void setUp() throws Exception {
    when(piiCryptoPort.decrypt(any())).thenReturn(objectMapper.writeValueAsString(payload()));
    service =
        new ReleaseExpiredCheckoutsService(
            idempotencyPort,
            new PendingCheckoutPayloadReader(piiCryptoPort, objectMapper),
            releaser,
            transactionManager);
  }

  @Test
  void releasesEveryClaimableExpiredCheckoutAndMarksItFailed() {
    when(idempotencyPort.findExpiredPendingKeys(any(), eq(100))).thenReturn(List.of("k1", "k2"));
    when(idempotencyPort.lockExpiredPending(eq("k1"), any())).thenReturn(Optional.of(lock()));
    when(idempotencyPort.lockExpiredPending(eq("k2"), any())).thenReturn(Optional.of(lock()));

    int released = service.execute();

    assertThat(released).isEqualTo(2);
    verify(releaser, times(2)).release(any(PendingCheckoutPayload.class));
    verify(idempotencyPort).markFailed("k1");
    verify(idempotencyPort).markFailed("k2");
  }

  @Test
  void runsEachRowInItsOwnTransaction() {
    when(idempotencyPort.findExpiredPendingKeys(any(), eq(100))).thenReturn(List.of("k1", "k2", "k3"));
    when(idempotencyPort.lockExpiredPending(any(), any())).thenReturn(Optional.of(lock()));

    service.execute();

    verify(transactionManager, times(3)).getTransaction(any());
    verify(transactionManager, times(3)).commit(any());
  }

  @Test
  void aRowResolvedOrClaimedElsewhereIsSkippedUntouched() {
    when(idempotencyPort.findExpiredPendingKeys(any(), eq(100))).thenReturn(List.of("k1"));
    when(idempotencyPort.lockExpiredPending(eq("k1"), any())).thenReturn(Optional.empty());

    assertThat(service.execute()).isZero();

    verify(releaser, never()).release(any());
    verify(idempotencyPort, never()).markFailed(any());
  }

  @Test
  void anUnreadablePayloadIsLeftPendingSoFixingTheKeyRecoversIt() {
    when(idempotencyPort.findExpiredPendingKeys(any(), eq(100))).thenReturn(List.of("k1"));
    when(idempotencyPort.lockExpiredPending(eq("k1"), any())).thenReturn(Optional.of(lock()));
    when(piiCryptoPort.decrypt(any())).thenThrow(new IllegalStateException("bad ciphertext"));

    assertThat(service.execute()).isZero();

    verify(releaser, never()).release(any());
    verify(idempotencyPort, never()).markFailed(any());
  }

  @Test
  void aFailingRowDoesNotStopTheOthers() {
    when(idempotencyPort.findExpiredPendingKeys(any(), eq(100))).thenReturn(List.of("k1", "k2"));
    when(idempotencyPort.lockExpiredPending(any(), any())).thenReturn(Optional.of(lock()));
    doThrow(new IllegalStateException("boom")).doNothing().when(releaser).release(any());

    int released = service.execute();

    assertThat(released).isEqualTo(1);
    verify(idempotencyPort, never()).markFailed("k1");
    verify(idempotencyPort).markFailed("k2");
    verify(transactionManager).rollback(any());
  }

  private IdempotencyLock lock() {
    return new IdempotencyLock("PENDING", new byte[0], UUID.randomUUID(), new byte[] {1});
  }

  private PendingCheckoutPayload payload() {
    return new PendingCheckoutPayload(
        "WEB",
        "NONE",
        "WEB-20260920-000001",
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
}
