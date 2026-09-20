package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.cart.command.ClearCartCommand;
import com.floristeriarosy.application.cart.port.in.ClearCartUseCase;
import com.floristeriarosy.application.customer.command.FindOrCreateGuestCustomerCommand;
import com.floristeriarosy.application.customer.port.in.FindOrCreateGuestCustomerUseCase;
import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;
import com.floristeriarosy.application.notification.port.in.RegisterNotificationUseCase;
import com.floristeriarosy.application.order.command.ConfirmCheckoutCommand;
import com.floristeriarosy.application.order.dto.ConfirmCheckoutOutcome;
import com.floristeriarosy.application.order.dto.IdempotencyLock;
import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.application.order.port.in.ConfirmCheckoutUseCase;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.application.order.port.out.OrderWritePort;
import com.floristeriarosy.application.order.support.CheckoutReservationReleaser;
import com.floristeriarosy.application.order.support.PendingCheckoutPayloadReader;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.OrderDelivery;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.OrderItem;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderItemId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.shared.util.LogSanitizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link ConfirmCheckoutUseCase} — Fase 3a (approved) or Fase 3b (declined) of
 * order.md §3.2, driven by Redsys's notification, never by the browser's return. Idempotent by
 * construction: a lock not {@code PENDING} means this was already handled, and Redsys's own retry
 * behavior (resend until {@code 200 OK}) is exactly why that matters (ADR-017).
 */
@Service
@Transactional
public class ConfirmCheckoutService implements ConfirmCheckoutUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmCheckoutService.class);

  private final CheckoutIdempotencyPort idempotencyPort;
  private final OrderPaymentPort orderPaymentPort;
  private final OrderWritePort orderWritePort;
  private final FindOrCreateGuestCustomerUseCase findOrCreateGuestCustomerUseCase;
  private final RegisterNotificationUseCase registerNotificationUseCase;
  private final ClearCartUseCase clearCartUseCase;
  private final PiiCryptoPort piiCryptoPort;
  private final PendingCheckoutPayloadReader payloadReader;
  private final CheckoutReservationReleaser reservationReleaser;

  /**
   * @param idempotencyPort reads and resolves the Fase-1 lock
   * @param orderPaymentPort records the confirmed capture
   * @param orderWritePort persists the materialized order
   * @param findOrCreateGuestCustomerUseCase links a {@code WEB} guest checkout (customer.md §3.2)
   * @param registerNotificationUseCase registers the confirmation emails (notification.md §3.1)
   * @param clearCartUseCase empties the cart the order was built from
   * @param piiCryptoPort encrypts the buyer/delivery snapshot
   * @param payloadReader decrypts and deserializes the pending payload
   * @param reservationReleaser reverses the Fase-1 stock and discount reservations on decline
   */
  public ConfirmCheckoutService(
      CheckoutIdempotencyPort idempotencyPort,
      OrderPaymentPort orderPaymentPort,
      OrderWritePort orderWritePort,
      FindOrCreateGuestCustomerUseCase findOrCreateGuestCustomerUseCase,
      RegisterNotificationUseCase registerNotificationUseCase,
      ClearCartUseCase clearCartUseCase,
      PiiCryptoPort piiCryptoPort,
      PendingCheckoutPayloadReader payloadReader,
      CheckoutReservationReleaser reservationReleaser) {
    this.idempotencyPort = idempotencyPort;
    this.orderPaymentPort = orderPaymentPort;
    this.orderWritePort = orderWritePort;
    this.findOrCreateGuestCustomerUseCase = findOrCreateGuestCustomerUseCase;
    this.registerNotificationUseCase = registerNotificationUseCase;
    this.clearCartUseCase = clearCartUseCase;
    this.piiCryptoPort = piiCryptoPort;
    this.payloadReader = payloadReader;
    this.reservationReleaser = reservationReleaser;
  }

  /**
   * @param command the decoded, signature-verified Redsys notification
   * @return {@link ConfirmCheckoutOutcome#EXPIRED} when the row is already {@code FAILED}, or its
   *     payload belongs to a different attempt than the notification's (both decided under the row
   *     lock, so neither the cleanup nor a re-armed key can race it), {@link
   *     ConfirmCheckoutOutcome#IGNORED} when it is unknown or already completed, {@link
   *     ConfirmCheckoutOutcome#RESOLVED} otherwise. An approval completes the row; a decline
   *     releases the reservation and leaves the row {@code FAILED}, so a retry with the same key
   *     restarts from Fase 1 (order.md §3.2, §10).
   */
  @Override
  public ConfirmCheckoutOutcome execute(ConfirmCheckoutCommand command) {
    LOGGER.debug(
        "confirmCheckout idempotencyKey={} approved={}",
        Encode.forJava(command.idempotencyKey()),
        command.approved());
    Optional<IdempotencyLock> lock = idempotencyPort.findForUpdate(command.idempotencyKey());
    if (lock.isPresent() && "FAILED".equals(lock.get().status())) {
      LOGGER.debug(
          "confirmCheckout idempotencyKey={} -> expired, reservation already released",
          Encode.forJava(command.idempotencyKey()));
      return ConfirmCheckoutOutcome.EXPIRED;
    }
    if (lock.isEmpty() || !"PENDING".equals(lock.get().status())) {
      LOGGER.debug(
          "confirmCheckout idempotencyKey={} -> already resolved or unknown, ignoring (Redsys retry)",
          Encode.forJava(command.idempotencyKey()));
      return ConfirmCheckoutOutcome.IGNORED;
    }
    PendingCheckoutPayload payload = payloadReader.read(lock.get().checkoutPayloadEncrypted());
    if (!payload.orderNumber().equals(command.orderNumber().value())) {
      LOGGER.debug(
          "confirmCheckout idempotencyKey={} -> notification belongs to a previous attempt",
          Encode.forJava(command.idempotencyKey()));
      return ConfirmCheckoutOutcome.EXPIRED;
    }
    OrderId orderId = OrderId.of(lock.get().resourceId());
    if (command.approved()) {
      confirm(orderId, command, payload);
      idempotencyPort.markCompleted(command.idempotencyKey(), 202);
    } else {
      LOGGER.debug("confirmCheckout orderId={} -> declined, reversing Fase 1", orderId);
      reservationReleaser.release(payload);
      idempotencyPort.markFailed(command.idempotencyKey());
    }
    LOGGER.debug(
        "confirmCheckout idempotencyKey={} -> resolved", Encode.forJava(command.idempotencyKey()));
    return ConfirmCheckoutOutcome.RESOLVED;
  }

  /**
   * @param orderId the order to materialize
   * @param command the approved notification
   * @param payload the decrypted pending-checkout payload
   */
  private void confirm(OrderId orderId, ConfirmCheckoutCommand command, PendingCheckoutPayload payload) {
    LOGGER.debug("confirmCheckout orderId={} -> approved, materializing order", orderId);
    UUID customerId = resolveCustomer(payload);
    List<OrderItem> items = payload.items().stream().map(this::toOrderItem).toList();
    OrderDelivery delivery = toOrderDelivery(payload);
    Order order =
        Order.place(
            orderId,
            command.orderNumber(),
            OrderChannel.valueOf(payload.channel()),
            OrderFulfillment.valueOf(payload.fulfillment()),
            customerId,
            piiCryptoPort.encrypt(payload.buyerFirstName()),
            piiCryptoPort.encrypt(payload.buyerLastName()),
            piiCryptoPort.encrypt(payload.buyerEmail()),
            piiCryptoPort.hmac(payload.buyerEmail().trim().toLowerCase(Locale.ROOT)),
            piiCryptoPort.encrypt(payload.buyerPhone()),
            piiCryptoPort.hmac(payload.buyerPhone()),
            items,
            delivery,
            payload.subtotal(),
            payload.shippingCost());
    orderWritePort.save(order);
    orderPaymentPort.confirmCharge(
        orderId,
        command.orderNumber(),
        PaymentMethod.CARD_ONLINE,
        order.total(),
        command.providerPaymentIntentId());
    clearCartUseCase.execute(new ClearCartCommand(payload.customerId(), payload.sessionToken()));
    registerNotificationUseCase.execute(
        new RegisterNotificationCommand("ORDER_CONFIRMED", orderId.value(), customerId));
    registerNotificationUseCase.execute(
        new RegisterNotificationCommand("STAFF_NEW_ORDER", orderId.value(), null));
    LOGGER.debug("confirmCheckout orderId={} -> order materialized", orderId);
  }

  /**
   * @param payload the decrypted pending-checkout payload
   * @return the linked customer's id, or {@code null}
   */
  private UUID resolveCustomer(PendingCheckoutPayload payload) {
    if (payload.customerId() != null) {
      return payload.customerId();
    }
    var result =
        findOrCreateGuestCustomerUseCase.execute(
            new FindOrCreateGuestCustomerCommand(
                payload.buyerEmail(),
                payload.buyerFirstName(),
                payload.buyerLastName(),
                payload.buyerPhone()));
    UUID customerId = result != null ? result.value() : null;
    LOGGER.debug("confirmCheckout resolveCustomer -> customerId={}", customerId);
    return customerId;
  }

  /**
   * @param item the pending-checkout line
   * @return the domain snapshot
   */
  private OrderItem toOrderItem(PendingCheckoutPayload.Item item) {
    return OrderItem.snapshot(
        OrderItemId.newId(),
        ProductId.of(item.productId()),
        item.productName(),
        item.productAttributes(),
        item.unitPrice(),
        item.discountPrice(),
        item.discountId() != null ? DiscountId.of(item.discountId()) : null,
        item.quantity());
  }

  /**
   * @param payload the decrypted pending-checkout payload
   * @return the domain delivery/pickup slot, or {@code null} for {@code NONE}
   */
  private OrderDelivery toOrderDelivery(PendingCheckoutPayload payload) {
    if (payload.delivery() != null) {
      var d = payload.delivery();
      return OrderDelivery.forDelivery(
          piiCryptoPort.encrypt(d.recipientName()),
          piiCryptoPort.encrypt(d.recipientPhone()),
          piiCryptoPort.encrypt(d.street()),
          piiCryptoPort.encrypt(d.number()),
          d.detail() != null ? piiCryptoPort.encrypt(d.detail()) : null,
          d.postalCode(),
          piiCryptoPort.encrypt(d.latitude().toString()),
          piiCryptoPort.encrypt(d.longitude().toString()),
          d.distanceKm(),
          d.deliveryDate(),
          d.slotFrom(),
          d.slotTo(),
          d.cardMessage() != null ? piiCryptoPort.encrypt(d.cardMessage()) : null);
    }
    if (payload.pickup() != null) {
      var p = payload.pickup();
      return OrderDelivery.forPickup(p.deliveryDate(), p.slotFrom(), p.slotTo());
    }
    return null;
  }
}
