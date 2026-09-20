package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.command.RejectOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.support.OrderReversalComponent;
import com.floristeriarosy.application.order.port.out.OrderWritePort;
import com.floristeriarosy.domain.exception.order.OrderInvalidTransitionException;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;
import com.floristeriarosy.domain.exception.payment.PaymentGatewayException;
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

/** {@link RejectOrderService} — PENDING → REJECTED (order.md §3.9). */
@ExtendWith(MockitoExtension.class)
class RejectOrderServiceTest {

  @Mock private OrderReadPort orderReadPort;
  @Mock private OrderWritePort orderWritePort;
  @Mock private OrderReversalComponent reversalComponent;

  private RejectOrderService service;

  @BeforeEach
  void setUp() {
    service = new RejectOrderService(orderReadPort, orderWritePort, reversalComponent);
  }

  @Test
  void rejectsPendingOrderAndReverses() {
    UUID adminUserId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PENDING, UUID.randomUUID());
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));
    when(orderWritePort.save(order)).thenReturn(order);

    OrderDto result = service.execute(new RejectOrderCommand(orderId, adminUserId, "Out of stock"));

    assertThat(result.status()).isEqualTo(OrderStatus.REJECTED.name());
    verify(reversalComponent).reverse(order, "ORDER_REJECTED");
  }

  @Test
  void aGatewayFailureDuringReversalPropagatesSoTheTransactionRollsBack() {
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PENDING, UUID.randomUUID());
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));
    when(orderWritePort.save(order)).thenReturn(order);
    doThrow(new PaymentGatewayException("The payment gateway could not process the refund"))
        .when(reversalComponent)
        .reverse(order, "ORDER_REJECTED");

    assertThatExceptionOfType(PaymentGatewayException.class)
        .isThrownBy(
            () -> service.execute(new RejectOrderCommand(orderId, UUID.randomUUID(), "Out of stock")));
  }

  @Test
  void notPendingThrowsInvalidTransition() {
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PREPARING, UUID.randomUUID());
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));

    assertThatExceptionOfType(OrderInvalidTransitionException.class)
        .isThrownBy(
            () -> service.execute(new RejectOrderCommand(orderId, UUID.randomUUID(), "Out of stock")))
        .withMessageContaining("is not PENDING");
  }

  @Test
  void missingOrderThrowsNotFound() {
    UUID orderId = UUID.randomUUID();
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.empty());

    assertThatExceptionOfType(OrderNotFoundException.class)
        .isThrownBy(
            () -> service.execute(new RejectOrderCommand(orderId, UUID.randomUUID(), "Out of stock")));
  }

  private Order order(UUID id, OrderStatus status, UUID customerId) {
    return Order.reconstitute(
        OrderId.of(id),
        OrderNumber.of("WEB-20260914-000001"),
        OrderChannel.WEB,
        OrderFulfillment.PICKUP,
        status,
        customerId,
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
  }
}
