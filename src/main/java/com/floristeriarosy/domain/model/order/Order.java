package com.floristeriarosy.domain.model.order;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root of the order module (order.md). A self-contained document: it carries its own
 * encrypted snapshot of the buyer and of every sold line, and survives the buyer's account being
 * archived or a product changing or being discontinued after the sale (ADR-007). An order that was
 * never paid never reaches this type — {@code order.md}, section 1.
 */
public final class Order {

  private static final Logger LOGGER = LoggerFactory.getLogger(Order.class);

  private final OrderId id;
  private final OrderNumber orderNumber;
  private final OrderChannel channel;
  private final OrderFulfillment fulfillment;
  private OrderStatus status;
  private final UUID customerId;
  private final byte[] buyerFirstNameEncrypted;
  private final byte[] buyerLastNameEncrypted;
  private final byte[] buyerEmailEncrypted;
  private final byte[] buyerEmailHash;
  private final byte[] buyerPhoneEncrypted;
  private final byte[] buyerPhoneHash;
  private final List<OrderItem> items;
  private final OrderDelivery delivery;
  private final BigDecimal subtotal;
  private final BigDecimal shippingCost;
  private final Instant placedAt;
  private LocalDate retentionUntil;
  private Instant personalDataPurgedAt;
  private final long version;
  private final Instant createdAt;
  private Instant updatedAt;

  /**
   * Private constructor. Use {@link #place} to create a new order, or {@link #reconstitute} to rebuild
   * one from persisted state.
   *
   * @param id application-generated identifier
   * @param orderNumber the application-generated business identifier
   * @param channel the sales channel
   * @param fulfillment how the order reaches the customer
   * @param status the fulfillment status
   * @param customerId the linked customer, or {@code null} for an unlinked guest checkout
   * @param buyerFirstNameEncrypted the buyer's encrypted first name
   * @param buyerLastNameEncrypted the buyer's encrypted last name
   * @param buyerEmailEncrypted the buyer's encrypted email
   * @param buyerEmailHash the buyer's email HMAC
   * @param buyerPhoneEncrypted the buyer's encrypted phone
   * @param buyerPhoneHash the buyer's phone HMAC
   * @param items the sold lines, at least one
   * @param delivery the delivery/pickup slot, or {@code null} for {@link OrderFulfillment#NONE}
   * @param subtotal the sum of every line's total, before shipping
   * @param shippingCost the computed shipping cost
   * @param placedAt when the order was placed
   * @param retentionUntil the PII retention deadline, or {@code null}
   * @param personalDataPurgedAt when PII was purged, or {@code null} if not yet purged
   * @param version the optimistic-locking version
   * @param createdAt when the row was created, or {@code null} before the first save
   * @param updatedAt when the row was last updated, or {@code null} before the first save
   */
  private Order(
      OrderId id,
      OrderNumber orderNumber,
      OrderChannel channel,
      OrderFulfillment fulfillment,
      OrderStatus status,
      UUID customerId,
      byte[] buyerFirstNameEncrypted,
      byte[] buyerLastNameEncrypted,
      byte[] buyerEmailEncrypted,
      byte[] buyerEmailHash,
      byte[] buyerPhoneEncrypted,
      byte[] buyerPhoneHash,
      List<OrderItem> items,
      OrderDelivery delivery,
      BigDecimal subtotal,
      BigDecimal shippingCost,
      Instant placedAt,
      LocalDate retentionUntil,
      Instant personalDataPurgedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.orderNumber = Objects.requireNonNull(orderNumber, "orderNumber");
    this.channel = Objects.requireNonNull(channel, "channel");
    this.fulfillment = Objects.requireNonNull(fulfillment, "fulfillment");
    this.status = Objects.requireNonNull(status, "status");
    this.customerId = customerId;
    this.buyerFirstNameEncrypted = buyerFirstNameEncrypted;
    this.buyerLastNameEncrypted = buyerLastNameEncrypted;
    this.buyerEmailEncrypted = buyerEmailEncrypted;
    this.buyerEmailHash = buyerEmailHash;
    this.buyerPhoneEncrypted = buyerPhoneEncrypted;
    this.buyerPhoneHash = buyerPhoneHash;
    this.items = List.copyOf(items);
    this.delivery = delivery;
    this.subtotal = Objects.requireNonNull(subtotal, "subtotal");
    this.shippingCost = Objects.requireNonNull(shippingCost, "shippingCost");
    this.placedAt = placedAt;
    this.retentionUntil = retentionUntil;
    this.personalDataPurgedAt = personalDataPurgedAt;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Places a new, already-paid order (order.md, section 3.2, Fase 3a). Born {@code PENDING} — an
   * order that was never charged never reaches this factory (section 1).
   *
   * @param id application-generated identifier, minted at Fase 1 (ADR-017)
   * @param orderNumber the application-generated business identifier (section 3.1)
   * @param channel the sales channel
   * @param fulfillment how the order reaches the customer
   * @param customerId the linked customer, or {@code null} for an unlinked guest checkout
   * @param buyerFirstNameEncrypted the buyer's encrypted first name
   * @param buyerLastNameEncrypted the buyer's encrypted last name
   * @param buyerEmailEncrypted the buyer's encrypted email
   * @param buyerEmailHash the buyer's email HMAC
   * @param buyerPhoneEncrypted the buyer's encrypted phone
   * @param buyerPhoneHash the buyer's phone HMAC
   * @param items the sold lines, at least one
   * @param delivery the delivery/pickup slot, or {@code null} for {@link OrderFulfillment#NONE}
   * @param subtotal the sum of every line's total, before shipping
   * @param shippingCost the computed shipping cost, zero for {@code PICKUP}/{@code NONE}
   * @return the new, {@code PENDING} order
   * @throws IllegalArgumentException {@code items} is empty
   */
  public static Order place(
      OrderId id,
      OrderNumber orderNumber,
      OrderChannel channel,
      OrderFulfillment fulfillment,
      UUID customerId,
      byte[] buyerFirstNameEncrypted,
      byte[] buyerLastNameEncrypted,
      byte[] buyerEmailEncrypted,
      byte[] buyerEmailHash,
      byte[] buyerPhoneEncrypted,
      byte[] buyerPhoneHash,
      List<OrderItem> items,
      OrderDelivery delivery,
      BigDecimal subtotal,
      BigDecimal shippingCost) {
    if (items.isEmpty()) {
      throw new IllegalArgumentException("items must not be empty");
    }
    LOGGER.debug(
        "place id={} orderNumber={} channel={} fulfillment={} itemCount={}",
        id,
        orderNumber,
        channel,
        fulfillment,
        items.size());
    Order result =
        new Order(
            id,
            orderNumber,
            channel,
            fulfillment,
            OrderStatus.PENDING,
            customerId,
            buyerFirstNameEncrypted,
            buyerLastNameEncrypted,
            buyerEmailEncrypted,
            buyerEmailHash,
            buyerPhoneEncrypted,
            buyerPhoneHash,
            items,
            delivery,
            subtotal,
            shippingCost,
            Instant.now(),
            null,
            null,
            0L,
            null,
            null);
    LOGGER.debug("place id={} -> placed", id);
    return result;
  }

  /**
   * Rebuilds an order from persisted state. Used only by the persistence mapper.
   *
   * @param id the persisted identifier
   * @param orderNumber the persisted business identifier
   * @param channel the persisted sales channel
   * @param fulfillment the persisted fulfillment mode
   * @param status the persisted fulfillment status
   * @param customerId the persisted linked customer, or {@code null}
   * @param buyerFirstNameEncrypted the persisted encrypted buyer first name, or {@code null} if
   *     purged
   * @param buyerLastNameEncrypted the persisted encrypted buyer last name, or {@code null} if purged
   * @param buyerEmailEncrypted the persisted encrypted buyer email, or {@code null} if purged
   * @param buyerEmailHash the persisted buyer email HMAC, or {@code null} if purged
   * @param buyerPhoneEncrypted the persisted encrypted buyer phone, or {@code null} if purged
   * @param buyerPhoneHash the persisted buyer phone HMAC, or {@code null} if purged
   * @param items the persisted sold lines
   * @param delivery the persisted delivery/pickup slot, or {@code null}
   * @param subtotal the persisted subtotal
   * @param shippingCost the persisted shipping cost
   * @param placedAt when the order was placed
   * @param retentionUntil the persisted PII retention deadline, or {@code null}
   * @param personalDataPurgedAt when PII was purged, or {@code null} if not yet purged
   * @param version the optimistic-locking version (ADR-009)
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt order
   */
  public static Order reconstitute(
      OrderId id,
      OrderNumber orderNumber,
      OrderChannel channel,
      OrderFulfillment fulfillment,
      OrderStatus status,
      UUID customerId,
      byte[] buyerFirstNameEncrypted,
      byte[] buyerLastNameEncrypted,
      byte[] buyerEmailEncrypted,
      byte[] buyerEmailHash,
      byte[] buyerPhoneEncrypted,
      byte[] buyerPhoneHash,
      List<OrderItem> items,
      OrderDelivery delivery,
      BigDecimal subtotal,
      BigDecimal shippingCost,
      Instant placedAt,
      LocalDate retentionUntil,
      Instant personalDataPurgedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Order(
        id,
        orderNumber,
        channel,
        fulfillment,
        status,
        customerId,
        buyerFirstNameEncrypted,
        buyerLastNameEncrypted,
        buyerEmailEncrypted,
        buyerEmailHash,
        buyerPhoneEncrypted,
        buyerPhoneHash,
        items,
        delivery,
        subtotal,
        shippingCost,
        placedAt,
        retentionUntil,
        personalDataPurgedAt,
        version,
        createdAt,
        updatedAt);
  }

  /**
   * @return {@code subtotal} plus {@code shippingCost} — mirrors the {@code total} generated column
   *     (order.md, section 2)
   */
  public BigDecimal total() {
    return subtotal.add(shippingCost);
  }

  /**
   * Moves the order into preparation (order.md, section 3.9).
   *
   * @throws IllegalStateException the order is not {@code PENDING} — the caller must have already
   *     checked this and thrown the user-facing {@code OrderInvalidTransitionException} itself;
   *     this is a last-resort guard, not the real precondition check
   */
  public void prepare() {
    LOGGER.debug("prepare id={} status={}", id, status);
    requireStatus(OrderStatus.PENDING);
    status = OrderStatus.PREPARING;
    LOGGER.debug("prepare id={} -> preparing", id);
  }

  /**
   * Sends a {@code DELIVERY} order out for delivery (order.md, section 3.9).
   *
   * @throws IllegalStateException the order is not {@code PREPARING}, or {@code fulfillment} is not
   *     {@code DELIVERY} — same caveat as {@link #prepare()}
   */
  public void ship() {
    LOGGER.debug("ship id={} status={} fulfillment={}", id, status, fulfillment);
    requireStatus(OrderStatus.PREPARING);
    if (fulfillment != OrderFulfillment.DELIVERY) {
      throw new IllegalStateException("Order " + id + " fulfillment is " + fulfillment + ", expected DELIVERY");
    }
    status = OrderStatus.OUT_FOR_DELIVERY;
    LOGGER.debug("ship id={} -> out for delivery", id);
  }

  /**
   * Marks the order delivered or picked up (order.md, section 3.9): from {@code OUT_FOR_DELIVERY}
   * for {@code DELIVERY}, or directly from {@code PREPARING} for {@code PICKUP}/{@code NONE}.
   *
   * @throws IllegalStateException the order is not in the expected status for its fulfillment mode
   *     — same caveat as {@link #prepare()}
   */
  public void complete() {
    LOGGER.debug("complete id={} status={} fulfillment={}", id, status, fulfillment);
    requireStatus(fulfillment == OrderFulfillment.DELIVERY ? OrderStatus.OUT_FOR_DELIVERY : OrderStatus.PREPARING);
    status = OrderStatus.DELIVERED;
    LOGGER.debug("complete id={} -> delivered", id);
  }

  /**
   * Rejects the order before preparation starts (order.md, section 3.9) — the admin cannot fulfill
   * it. Reversal (stock, discount, refund, notification) is a separate concern, driven by the
   * caller after this returns (section 3.10).
   *
   * @throws IllegalStateException the order is not {@code PENDING} — same caveat as {@link
   *     #prepare()}
   */
  public void reject() {
    LOGGER.debug("reject id={} status={}", id, status);
    requireStatus(OrderStatus.PENDING);
    status = OrderStatus.REJECTED;
    LOGGER.debug("reject id={} -> rejected", id);
  }

  /**
   * Cancels the order (order.md, section 3.9) — from {@code PENDING} or {@code PREPARING}. Which of
   * those two the caller is actually allowed to use depends on who is asking (admin vs. the owning
   * customer, section 3.9) and is checked by the caller before this is invoked, not here — same
   * caveat as {@link #prepare()}. Reversal is a separate concern (section 3.10).
   *
   * @throws IllegalStateException the order is not {@code PENDING} or {@code PREPARING}
   */
  public void cancel() {
    LOGGER.debug("cancel id={} status={}", id, status);
    if (status != OrderStatus.PENDING && status != OrderStatus.PREPARING) {
      throw new IllegalStateException("Order " + id + " is " + status + ", expected PENDING or PREPARING");
    }
    status = OrderStatus.CANCELLED;
    LOGGER.debug("cancel id={} -> cancelled", id);
  }

  /**
   * @param expected the status this order must currently have
   * @throws IllegalStateException the current status is not {@code expected}
   */
  private void requireStatus(OrderStatus expected) {
    if (status != expected) {
      throw new IllegalStateException("Order " + id + " is " + status + ", expected " + expected);
    }
  }

  /**
   * @return the application-generated identifier
   */
  public OrderId id() {
    return id;
  }

  /**
   * @return the application-generated business identifier
   */
  public OrderNumber orderNumber() {
    return orderNumber;
  }

  /**
   * @return the sales channel
   */
  public OrderChannel channel() {
    return channel;
  }

  /**
   * @return how the order reaches the customer
   */
  public OrderFulfillment fulfillment() {
    return fulfillment;
  }

  /**
   * @return the fulfillment status
   */
  public OrderStatus status() {
    return status;
  }

  /**
   * @return the linked customer, or {@code null} for an unlinked guest checkout
   */
  public UUID customerId() {
    return customerId;
  }

  /**
   * @return the buyer's encrypted first name, or {@code null} if purged
   */
  public byte[] buyerFirstNameEncrypted() {
    return buyerFirstNameEncrypted == null ? null : buyerFirstNameEncrypted.clone();
  }

  /**
   * @return the buyer's encrypted last name, or {@code null} if purged
   */
  public byte[] buyerLastNameEncrypted() {
    return buyerLastNameEncrypted == null ? null : buyerLastNameEncrypted.clone();
  }

  /**
   * @return the buyer's encrypted email, or {@code null} if purged
   */
  public byte[] buyerEmailEncrypted() {
    return buyerEmailEncrypted == null ? null : buyerEmailEncrypted.clone();
  }

  /**
   * @return the buyer's email HMAC, or {@code null} if purged
   */
  public byte[] buyerEmailHash() {
    return buyerEmailHash == null ? null : buyerEmailHash.clone();
  }

  /**
   * @return the buyer's encrypted phone, or {@code null} if purged
   */
  public byte[] buyerPhoneEncrypted() {
    return buyerPhoneEncrypted == null ? null : buyerPhoneEncrypted.clone();
  }

  /**
   * @return the buyer's phone HMAC, or {@code null} if purged
   */
  public byte[] buyerPhoneHash() {
    return buyerPhoneHash == null ? null : buyerPhoneHash.clone();
  }

  /**
   * @return the sold lines, unmodifiable
   */
  public List<OrderItem> items() {
    return items;
  }

  /**
   * @return the delivery/pickup slot, or {@code null} for {@link OrderFulfillment#NONE}
   */
  public OrderDelivery delivery() {
    return delivery;
  }

  /**
   * @return the sum of every line's total, before shipping
   */
  public BigDecimal subtotal() {
    return subtotal;
  }

  /**
   * @return the computed shipping cost
   */
  public BigDecimal shippingCost() {
    return shippingCost;
  }

  /**
   * @return when the order was placed, or {@code null} before the first save
   */
  public Instant placedAt() {
    return placedAt;
  }

  /**
   * @return the PII retention deadline, or {@code null} before the order reaches a terminal status
   */
  public LocalDate retentionUntil() {
    return retentionUntil;
  }

  /**
   * @return when PII was purged, or {@code null} if not yet purged
   */
  public Instant personalDataPurgedAt() {
    return personalDataPurgedAt;
  }

  /**
   * @return the optimistic-locking version (ADR-009)
   */
  public long version() {
    return version;
  }

  /**
   * @return when the row was created, or {@code null} before the first save
   */
  public Instant createdAt() {
    return createdAt;
  }

  /**
   * @return when the row was last updated, or {@code null} before the first save
   */
  public Instant updatedAt() {
    return updatedAt;
  }
}
