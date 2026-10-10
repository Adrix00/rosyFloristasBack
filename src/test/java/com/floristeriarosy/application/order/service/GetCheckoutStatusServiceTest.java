package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.dto.IdempotencyLock;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.query.GetCheckoutStatusQuery;
import com.floristeriarosy.domain.exception.order.CheckoutNotFoundException;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.OrderStatus;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
  @Mock private OrderReadPort orderReadPort;

  private GetCheckoutStatusService service;

  @BeforeEach
  void setUp() {
    service = new GetCheckoutStatusService(idempotencyPort, orderReadPort);
  }

  @Test
  void pendingReportsPendingWithNoOrderNumber() {
    when(idempotencyPort.find("idem-1"))
        .thenReturn(Optional.of(new IdempotencyLock("PENDING", new byte[0], UUID.randomUUID(), null)));

    var status = service.execute(new GetCheckoutStatusQuery("idem-1"));

    assertThat(status.status()).isEqualTo("PENDING");
    assertThat(status.orderNumber()).isNull();
    assertThat(status.reason()).isNull();
  }

  @Test
  void completedResolvesOrderNumber() {
    UUID orderId = UUID.randomUUID();
    when(idempotencyPort.find("idem-2"))
        .thenReturn(Optional.of(new IdempotencyLock("COMPLETED", new byte[0], orderId, null)));
    Order order =
        Order.reconstitute(
            OrderId.of(orderId),
            OrderNumber.of("WEB-20260914-000001"),
            OrderChannel.WEB,
            OrderFulfillment.PICKUP,
            OrderStatus.PENDING,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            new BigDecimal("25.00"),
            BigDecimal.ZERO,
            Instant.now(),
            null,
            null,
            0L,
            Instant.now(),
            Instant.now());
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));

    var status = service.execute(new GetCheckoutStatusQuery("idem-2"));

    assertThat(status.status()).isEqualTo("COMPLETED");
    assertThat(status.orderNumber()).isEqualTo("WEB-20260914-000001");
    assertThat(status.reason()).isNull();
  }

  @Test
  void throwsWhenNoAttemptExistsForTheKey() {
    when(idempotencyPort.find("idem-3")).thenReturn(Optional.empty());

    assertThatExceptionOfType(CheckoutNotFoundException.class)
        .isThrownBy(() -> service.execute(new GetCheckoutStatusQuery("idem-3")));
  }
}
