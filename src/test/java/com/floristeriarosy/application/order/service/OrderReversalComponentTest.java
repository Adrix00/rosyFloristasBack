package com.floristeriarosy.application.order.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.discount.port.out.DiscountReservationPort;
import com.floristeriarosy.application.inventory.port.in.RegisterStockMovementUseCase;
import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;
import com.floristeriarosy.application.notification.port.in.RegisterNotificationUseCase;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.domain.exception.inventory.InventoryNotManagedException;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.OrderItem;
import com.floristeriarosy.domain.model.order.OrderStatus;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderItemId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link OrderReversalComponent} — stock, discount, refund and notifications on reject/cancel. */
@ExtendWith(MockitoExtension.class)
class OrderReversalComponentTest {

  @Mock private RegisterStockMovementUseCase registerStockMovementUseCase;
  @Mock private DiscountReservationPort discountReservationPort;
  @Mock private OrderPaymentPort orderPaymentPort;
  @Mock private RegisterNotificationUseCase registerNotificationUseCase;

  private OrderReversalComponent component;

  @BeforeEach
  void setUp() {
    component =
        new OrderReversalComponent(
            registerStockMovementUseCase,
            discountReservationPort,
            orderPaymentPort,
            registerNotificationUseCase);
  }

  @Test
  void reverseReleasesStockDiscountRefundAndNotificationsForOrderRejected() {
    UUID customerId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    DiscountId discountId = DiscountId.of(UUID.randomUUID());
    Order order = order(customerId, List.of(line(productId, discountId, 3)));

    component.reverse(order, "ORDER_REJECTED");

    verify(registerStockMovementUseCase)
        .execute(productId, StockMovementType.ADJUSTMENT, 3, null, "order lifecycle reversal");
    verify(discountReservationPort).release(discountId, 3);
    verify(orderPaymentPort).refund(order.id(), order.orderNumber());
    ArgumentCaptor<RegisterNotificationCommand> notificationCaptor =
        ArgumentCaptor.forClass(RegisterNotificationCommand.class);
    verify(registerNotificationUseCase, org.mockito.Mockito.times(2))
        .execute(notificationCaptor.capture());
    List<RegisterNotificationCommand> notifications = notificationCaptor.getAllValues();
    assertNotification(notifications.get(0), "ORDER_REJECTED", order.id().value(), customerId);
    assertNotification(notifications.get(1), "REFUND_ISSUED", order.id().value(), customerId);
  }

  @Test
  void reverseUsesNullCustomerRecipientForStaffNotification() {
    UUID productId = UUID.randomUUID();
    Order order = order(UUID.randomUUID(), List.of(line(productId, null, 1)));

    component.reverse(order, "STAFF_ORDER_CANCELLED_BY_CUSTOMER");

    ArgumentCaptor<RegisterNotificationCommand> notificationCaptor =
        ArgumentCaptor.forClass(RegisterNotificationCommand.class);
    verify(registerNotificationUseCase, org.mockito.Mockito.times(2))
        .execute(notificationCaptor.capture());
    List<RegisterNotificationCommand> notifications = notificationCaptor.getAllValues();
    assertNotification(notifications.get(0), "STAFF_ORDER_CANCELLED_BY_CUSTOMER", order.id().value(), null);
    assertNotification(notifications.get(1), "REFUND_ISSUED", order.id().value(), order.customerId());
  }

  @Test
  void reverseSkipsStockReleaseForUnmanagedProduct() {
    UUID productId = UUID.randomUUID();
    Order order = order(UUID.randomUUID(), List.of(line(productId, null, 2)));
    when(registerStockMovementUseCase.execute(any(), any(), anyInt(), any(), any()))
        .thenThrow(new InventoryNotManagedException("Unmanaged"));

    component.reverse(order, "ORDER_CANCELLED");

    verify(discountReservationPort, never()).release(any(), anyInt());
    verify(orderPaymentPort).refund(order.id(), order.orderNumber());
    verify(registerNotificationUseCase, org.mockito.Mockito.times(2)).execute(any());
  }

  @Test
  void reverseSkipsDiscountReleaseWhenNoDiscount() {
    UUID productId = UUID.randomUUID();
    Order order = order(UUID.randomUUID(), List.of(line(productId, null, 1)));

    component.reverse(order, "ORDER_REJECTED");

    verifyNoInteractions(discountReservationPort);
    verify(orderPaymentPort).refund(order.id(), order.orderNumber());
  }

  private void assertNotification(
      RegisterNotificationCommand command, String type, UUID orderId, UUID customerId) {
    org.assertj.core.api.Assertions.assertThat(command.type()).isEqualTo(type);
    org.assertj.core.api.Assertions.assertThat(command.orderId()).isEqualTo(orderId);
    org.assertj.core.api.Assertions.assertThat(command.customerId()).isEqualTo(customerId);
  }

  private Order order(UUID customerId, List<OrderItem> items) {
    return Order.reconstitute(
        OrderId.of(UUID.randomUUID()),
        OrderNumber.of("WEB-20260914-000001"),
        OrderChannel.WEB,
        OrderFulfillment.PICKUP,
        OrderStatus.CANCELLED,
        customerId,
        null,
        null,
        null,
        null,
        null,
        null,
        items,
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

  private OrderItem line(UUID productId, DiscountId discountId, int quantity) {
    return OrderItem.reconstitute(
        OrderItemId.of(UUID.randomUUID()),
        ProductId.of(productId),
        "Ramo",
        null,
        new BigDecimal("25.00"),
        null,
        discountId,
        quantity,
        Instant.now());
  }
}
