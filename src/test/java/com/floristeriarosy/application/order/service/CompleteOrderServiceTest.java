package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;
import com.floristeriarosy.application.notification.port.in.RegisterNotificationUseCase;
import com.floristeriarosy.application.order.command.CompleteOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.port.out.OrderWritePort;
import com.floristeriarosy.domain.exception.order.OrderInvalidTransitionException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link CompleteOrderService} — marks orders DELIVERED (order.md §3.9). */
@ExtendWith(MockitoExtension.class)
class CompleteOrderServiceTest {

  @Mock private OrderReadPort orderReadPort;
  @Mock private OrderWritePort orderWritePort;
  @Mock private RegisterNotificationUseCase registerNotificationUseCase;

  private CompleteOrderService service;

  @BeforeEach
  void setUp() {
    service = new CompleteOrderService(orderReadPort, orderWritePort, registerNotificationUseCase);
  }

  @Test
  void completesOutForDeliveryDeliveryOrder() {
    UUID adminUserId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.OUT_FOR_DELIVERY, OrderFulfillment.DELIVERY, customerId);
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));
    when(orderWritePort.save(order)).thenReturn(order);

    OrderDto result = service.execute(new CompleteOrderCommand(orderId, adminUserId));

    assertThat(result.status()).isEqualTo(OrderStatus.DELIVERED.name());
    ArgumentCaptor<RegisterNotificationCommand> captor =
        ArgumentCaptor.forClass(RegisterNotificationCommand.class);
    verify(registerNotificationUseCase).execute(captor.capture());
    RegisterNotificationCommand notification = captor.getValue();
    assertThat(notification.type()).isEqualTo("ORDER_DELIVERED");
    assertThat(notification.orderId()).isEqualTo(orderId);
    assertThat(notification.customerId()).isEqualTo(customerId);
  }

  @Test
  void completesPreparingPickupOrder() {
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PREPARING, OrderFulfillment.PICKUP, UUID.randomUUID());
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));
    when(orderWritePort.save(order)).thenReturn(order);

    OrderDto result = service.execute(new CompleteOrderCommand(orderId, UUID.randomUUID()));

    assertThat(result.status()).isEqualTo(OrderStatus.DELIVERED.name());
  }

  @Test
  void deliveryFromPreparingThrowsInvalidTransition() {
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PREPARING, OrderFulfillment.DELIVERY, UUID.randomUUID());
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));

    assertThatExceptionOfType(OrderInvalidTransitionException.class)
        .isThrownBy(() -> service.execute(new CompleteOrderCommand(orderId, UUID.randomUUID())))
        .withMessageContaining("not in the expected status");
  }

  @Test
  void pickupFromOutForDeliveryThrowsInvalidTransition() {
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.OUT_FOR_DELIVERY, OrderFulfillment.PICKUP, UUID.randomUUID());
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));

    assertThatExceptionOfType(OrderInvalidTransitionException.class)
        .isThrownBy(() -> service.execute(new CompleteOrderCommand(orderId, UUID.randomUUID())))
        .withMessageContaining("not in the expected status");
  }

  @Test
  void missingOrderThrowsNotFound() {
    UUID orderId = UUID.randomUUID();
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.empty());

    assertThatExceptionOfType(OrderNotFoundException.class)
        .isThrownBy(() -> service.execute(new CompleteOrderCommand(orderId, UUID.randomUUID())));
  }

  private Order order(UUID id, OrderStatus status, OrderFulfillment fulfillment, UUID customerId) {
    return Order.reconstitute(
        OrderId.of(id),
        OrderNumber.of("WEB-20260914-000001"),
        OrderChannel.WEB,
        fulfillment,
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
