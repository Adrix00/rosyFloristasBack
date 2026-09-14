package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.command.ShipOrderCommand;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ShipOrderService} — PREPARING → OUT_FOR_DELIVERY (order.md §3.9). */
@ExtendWith(MockitoExtension.class)
class ShipOrderServiceTest {

  @Mock private OrderReadPort orderReadPort;
  @Mock private OrderWritePort orderWritePort;

  private ShipOrderService service;

  @BeforeEach
  void setUp() {
    service = new ShipOrderService(orderReadPort, orderWritePort);
  }

  @Test
  void shipsPreparingDeliveryOrder() {
    UUID adminUserId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PREPARING, OrderFulfillment.DELIVERY);
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));
    when(orderWritePort.save(order)).thenReturn(order);

    OrderDto result = service.execute(new ShipOrderCommand(orderId, adminUserId));

    assertThat(result.status()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY.name());
  }

  @Test
  void pickupOrderThrowsInvalidTransition() {
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PREPARING, OrderFulfillment.PICKUP);
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));

    assertThatExceptionOfType(OrderInvalidTransitionException.class)
        .isThrownBy(() -> service.execute(new ShipOrderCommand(orderId, UUID.randomUUID())))
        .withMessageContaining("is not PREPARING DELIVERY");
  }

  @Test
  void notPreparingThrowsInvalidTransition() {
    UUID orderId = UUID.randomUUID();
    Order order = order(orderId, OrderStatus.PENDING, OrderFulfillment.DELIVERY);
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));

    assertThatExceptionOfType(OrderInvalidTransitionException.class)
        .isThrownBy(() -> service.execute(new ShipOrderCommand(orderId, UUID.randomUUID())))
        .withMessageContaining("is not PREPARING DELIVERY");
  }

  @Test
  void missingOrderThrowsNotFound() {
    UUID orderId = UUID.randomUUID();
    when(orderReadPort.findById(OrderId.of(orderId))).thenReturn(Optional.empty());

    assertThatExceptionOfType(OrderNotFoundException.class)
        .isThrownBy(() -> service.execute(new ShipOrderCommand(orderId, UUID.randomUUID())));
  }

  private Order order(UUID id, OrderStatus status, OrderFulfillment fulfillment) {
    return Order.reconstitute(
        OrderId.of(id),
        OrderNumber.of("WEB-20260914-000001"),
        OrderChannel.WEB,
        fulfillment,
        status,
        UUID.randomUUID(),
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
