package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.query.GetOrderQuery;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;
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

/** {@link GetOrderService} — ownership-enforced order lookup (order.md §4, §6). */
@ExtendWith(MockitoExtension.class)
class GetOrderServiceTest {

  @Mock private OrderReadPort orderReadPort;
  @Mock private PiiCryptoPort piiCryptoPort;

  private GetOrderService service;

  @BeforeEach
  void setUp() {
    service = new GetOrderService(orderReadPort, piiCryptoPort);
  }

  @Test
  void returnsOrderOwnedByCustomer() {
    UUID customerId = UUID.randomUUID();
    Order order = paidOrder(customerId, new byte[0]);
    when(orderReadPort.findByOrderNumber(OrderNumber.of("WEB-20260914-000001")))
        .thenReturn(Optional.of(order));

    var result = service.execute(new GetOrderQuery("WEB-20260914-000001", customerId, null));

    assertThat(result.orderNumber()).isEqualTo("WEB-20260914-000001");
  }

  @Test
  void wrongCustomerIsNotFound() {
    UUID customerId = UUID.randomUUID();
    Order order = paidOrder(UUID.randomUUID(), new byte[0]);
    when(orderReadPort.findByOrderNumber(OrderNumber.of("WEB-20260914-000001")))
        .thenReturn(Optional.of(order));

    assertThatExceptionOfType(OrderNotFoundException.class)
        .isThrownBy(() -> service.execute(new GetOrderQuery("WEB-20260914-000001", customerId, null)));
  }

  @Test
  void guestWithMatchingEmailReturnsOrder() {
    byte[] emailHash = new byte[] {1, 2, 3};
    Order order = paidOrder(null, emailHash);
    when(orderReadPort.findByOrderNumber(OrderNumber.of("WEB-20260914-000001")))
        .thenReturn(Optional.of(order));
    when(piiCryptoPort.hmac("ana@example.com")).thenReturn(emailHash);

    var result =
        service.execute(new GetOrderQuery("WEB-20260914-000001", null, "ana@example.com"));

    assertThat(result.orderNumber()).isEqualTo("WEB-20260914-000001");
  }

  @Test
  void guestWithMismatchingEmailIsNotFound() {
    byte[] emailHash = new byte[] {1, 2, 3};
    Order order = paidOrder(null, emailHash);
    when(orderReadPort.findByOrderNumber(OrderNumber.of("WEB-20260914-000001")))
        .thenReturn(Optional.of(order));
    when(piiCryptoPort.hmac("other@example.com")).thenReturn(new byte[] {4, 5, 6});

    assertThatExceptionOfType(OrderNotFoundException.class)
        .isThrownBy(
            () -> service.execute(new GetOrderQuery("WEB-20260914-000001", null, "other@example.com")));
  }

  @Test
  void missingOrderThrowsNotFound() {
    when(orderReadPort.findByOrderNumber(OrderNumber.of("WEB-20260914-000001")))
        .thenReturn(Optional.empty());

    assertThatExceptionOfType(OrderNotFoundException.class)
        .isThrownBy(() -> service.execute(new GetOrderQuery("WEB-20260914-000001", null, "ana@example.com")));
  }

  private Order paidOrder(UUID customerId, byte[] emailHash) {
    return Order.reconstitute(
        OrderId.of(UUID.randomUUID()),
        OrderNumber.of("WEB-20260914-000001"),
        OrderChannel.WEB,
        OrderFulfillment.PICKUP,
        OrderStatus.PENDING,
        customerId,
        null,
        null,
        null,
        emailHash,
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
  }
}
