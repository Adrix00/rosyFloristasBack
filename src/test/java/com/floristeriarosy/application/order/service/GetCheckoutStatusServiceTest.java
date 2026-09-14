package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.IdempotencyLock;
import com.floristeriarosy.application.order.query.GetCheckoutStatusQuery;
import com.floristeriarosy.domain.exception.order.CheckoutNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link GetCheckoutStatusService} — thin read over the Fase-1 idempotency lock (ADR-017). */
@ExtendWith(MockitoExtension.class)
class GetCheckoutStatusServiceTest {

  @Mock private CheckoutIdempotencyPort idempotencyPort;

  private GetCheckoutStatusService service;

  @BeforeEach
  void setUp() {
    service = new GetCheckoutStatusService(idempotencyPort);
  }

  @Test
  void pendingReportsPendingWithNoOrderNumber() {
    when(idempotencyPort.find("idem-1"))
        .thenReturn(Optional.of(new IdempotencyLock("PENDING", new byte[0], UUID.randomUUID())));

    var status = service.execute(new GetCheckoutStatusQuery("idem-1"));

    assertThat(status.status()).isEqualTo("PENDING");
    assertThat(status.orderNumber()).isNull();
    assertThat(status.reason()).isNull();
  }

  @Test
  void throwsWhenNoAttemptExistsForTheKey() {
    when(idempotencyPort.find("idem-2")).thenReturn(Optional.empty());

    assertThatExceptionOfType(CheckoutNotFoundException.class)
        .isThrownBy(() -> service.execute(new GetCheckoutStatusQuery("idem-2")));
  }
}
