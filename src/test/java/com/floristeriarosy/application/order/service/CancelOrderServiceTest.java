package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.command.CancelOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.port.out.OrderWritePort;
import com.floristeriarosy.domain.exception.order.OrderInvalidTransitionException;
import com.floristeriarosy.domain.exception.order.OrderNotCancelableException;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;
import com.floristeriarosy.domain.exception.order.OrderValidationFailedException;
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

/** {@link CancelOrderService} — admin and customer cancellation paths (order.md §3.9). */
@ExtendWith(MockitoExtension.class)
class CancelOrderServiceTest {

  @Mock private OrderReadPort orderReadPort;
  @Mock private OrderWritePort orderWritePort;
  @Mock private OrderReversalComponent reversalComponent;

  private CancelOrderService service;

  @BeforeEach
  void setUp() {
    service = new CancelOrderService(orderReadPort, orderWritePort, reversalComponent);
  }

  @Test
  void adminCancelsPendingOrder() {
    UUID adminUserId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PENDING, UUID.randomUUID());
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));
    when(orderWritePort.save(order)).thenReturn(order);

    OrderDto result =
        service.execute(new CancelOrderCommand(orderId, null, adminUserId, "Customer request"));

    assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED.name());
    verify(reversalComponent).reverse(order, "ORDER_CANCELLED");
  }

  @Test
  void adminCancelsPreparingOrder() {
    UUID adminUserId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PREPARING, UUID.randomUUID());
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));
    when(orderWritePort.save(order)).thenReturn(order);

    OrderDto result =
        service.execute(new CancelOrderCommand(orderId, null, adminUserId, "Customer request"));

    assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED.name());
    verify(reversalComponent).reverse(order, "ORDER_CANCELLED");
  }

  @Test
  void customerCancelsOwnPendingOrder() {
    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PENDING, customerId);
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));
    when(orderWritePort.save(order)).thenReturn(order);

    OrderDto result = service.execute(new CancelOrderCommand(orderId, customerId, null, null));

    assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED.name());
    verify(reversalComponent).reverse(order, "STAFF_ORDER_CANCELLED_BY_CUSTOMER");
  }

  @Test
  void adminCancelWithoutReasonThrowsValidationFailed() {
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PENDING, UUID.randomUUID());
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));

    assertThatExceptionOfType(OrderValidationFailedException.class)
        .isThrownBy(() -> service.execute(new CancelOrderCommand(orderId, null, UUID.randomUUID(), null)))
        .withMessageContaining("reason is required");
  }

  @Test
  void adminCancelFromDeliveredThrowsInvalidTransition() {
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.DELIVERED, UUID.randomUUID());
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));

    assertThatExceptionOfType(OrderInvalidTransitionException.class)
        .isThrownBy(
            () -> service.execute(
                new CancelOrderCommand(orderId, null, UUID.randomUUID(), "Customer request")))
        .withMessageContaining("is not PENDING or PREPARING");
  }

  @Test
  void customerCancelFromPreparingThrowsNotCancelable() {
    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PREPARING, customerId);
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));

    assertThatExceptionOfType(OrderNotCancelableException.class)
        .isThrownBy(
            () -> service.execute(new CancelOrderCommand(orderId, customerId, null, null)))
        .withMessageContaining("is not PENDING");
  }

  @Test
  void customerCancelingOrderTheyDoNotOwnThrowsNotFound() {
    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PENDING, UUID.randomUUID());
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));

    assertThatExceptionOfType(OrderNotFoundException.class)
        .isThrownBy(
            () -> service.execute(new CancelOrderCommand(orderId, customerId, null, null)));
  }

  @Test
  void missingOrderThrowsNotFound() {
    UUID orderId = UUID.randomUUID();
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.empty());

    assertThatExceptionOfType(OrderNotFoundException.class)
        .isThrownBy(() -> service.execute(new CancelOrderCommand(orderId, null, UUID.randomUUID(), "Reason")));
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
