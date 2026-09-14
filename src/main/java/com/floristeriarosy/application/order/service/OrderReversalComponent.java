package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.discount.port.out.DiscountReservationPort;
import com.floristeriarosy.application.inventory.port.in.RegisterStockMovementUseCase;
import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;
import com.floristeriarosy.application.notification.port.in.RegisterNotificationUseCase;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.domain.exception.inventory.InventoryNotManagedException;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderItem;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The reversal {@code RejectOrderService} and {@code CancelOrderService} share (order.md, section
 * 3.10): reverses every line's stock reservation, releases every line's discount reservation,
 * refunds the payment in full, and registers the notification each case calls for. Not a use case
 * — nothing outside {@code order}'s own services calls this, and it is never swapped for another
 * implementation, so it is a plain internal component rather than a capability port (ADR-003).
 */
@Component
class OrderReversalComponent {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderReversalComponent.class);
  private static final String NOTE = "order lifecycle reversal";

  private final RegisterStockMovementUseCase registerStockMovementUseCase;
  private final DiscountReservationPort discountReservationPort;
  private final OrderPaymentPort orderPaymentPort;
  private final RegisterNotificationUseCase registerNotificationUseCase;

  /**
   * @param registerStockMovementUseCase records stock {@code ADJUSTMENT} movements
   * @param discountReservationPort releases promotional reservations
   * @param orderPaymentPort refunds the order's payment in full
   * @param registerNotificationUseCase registers customer and staff notifications
   */
  OrderReversalComponent(
      RegisterStockMovementUseCase registerStockMovementUseCase,
      DiscountReservationPort discountReservationPort,
      OrderPaymentPort orderPaymentPort,
      RegisterNotificationUseCase registerNotificationUseCase) {
    this.registerStockMovementUseCase = registerStockMovementUseCase;
    this.discountReservationPort = discountReservationPort;
    this.orderPaymentPort = orderPaymentPort;
    this.registerNotificationUseCase = registerNotificationUseCase;
  }

  /**
   * @param order the order being rejected or cancelled, already moved to its terminal status
   * @param customerNotificationType {@code ORDER_REJECTED}, {@code ORDER_CANCELLED} or {@code
   *     STAFF_ORDER_CANCELLED_BY_CUSTOMER} — the one notification.md §3.1 catalog type this
   *     reversal fires besides {@code REFUND_ISSUED}
   */
  void reverse(Order order, String customerNotificationType) {
    LOGGER.debug(
        "reverse orderId={} notificationType={}", order.id(), customerNotificationType);
    for (OrderItem item : order.items()) {
      releaseStock(item);
      releaseDiscount(item);
    }
    orderPaymentPort.refund(order.id(), order.orderNumber());
    UUID recipientCustomerId = customerNotificationType.startsWith("STAFF_") ? null : order.customerId();
    registerNotificationUseCase.execute(
        new RegisterNotificationCommand(customerNotificationType, order.id().value(), recipientCustomerId));
    registerNotificationUseCase.execute(
        new RegisterNotificationCommand("REFUND_ISSUED", order.id().value(), order.customerId()));
    LOGGER.debug("reverse orderId={} -> reversed", order.id());
  }

  /**
   * @param item the sold line whose stock reservation must be reversed, if it had one
   */
  private void releaseStock(OrderItem item) {
    try {
      registerStockMovementUseCase.execute(
          item.productId().value(), StockMovementType.ADJUSTMENT, item.quantity(), null, NOTE);
    } catch (InventoryNotManagedException e) {
      LOGGER.debug("reverse releaseStock skipped productId={} (unmanaged)", item.productId());
    }
  }

  /**
   * @param item the sold line whose discount reservation must be released, if it had one
   */
  private void releaseDiscount(OrderItem item) {
    if (item.discountId() == null) {
      return;
    }
    discountReservationPort.release(item.discountId(), item.quantity());
  }
}
