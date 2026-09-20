package com.floristeriarosy.infrastructure.persistence.entity.order;

import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA mapping of the {@code orders} table (order.md, section 2). Carries {@code @Version}
 * (ADR-009) and owns its {@code order_items} / {@code order_deliveries} children through cascade.
 * {@code total} is a generated column and is not mapped here (order.md §2).
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderEntity.class);

  @Id private UUID id;

  @Column(name = "order_number", nullable = false, length = 30)
  private String orderNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderFulfillment fulfillment;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private OrderStatus status;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "buyer_first_name_encrypted")
  private byte[] buyerFirstNameEncrypted;

  @Column(name = "buyer_last_name_encrypted")
  private byte[] buyerLastNameEncrypted;

  @Column(name = "buyer_email_encrypted")
  private byte[] buyerEmailEncrypted;

  @Column(name = "buyer_email_hash")
  private byte[] buyerEmailHash;

  @Column(name = "buyer_phone_encrypted")
  private byte[] buyerPhoneEncrypted;

  @Column(name = "buyer_phone_hash")
  private byte[] buyerPhoneHash;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal subtotal;

  @Column(name = "shipping_cost", nullable = false, precision = 10, scale = 2)
  private BigDecimal shippingCost;

  @Column(name = "placed_at", nullable = false)
  private Instant placedAt;

  @Column(name = "retention_until")
  private LocalDate retentionUntil;

  @Column(name = "personal_data_purged_at")
  private Instant personalDataPurgedAt;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(
      mappedBy = "order",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<OrderItemEntity> items = new ArrayList<>();

  @OneToOne(
      mappedBy = "order",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private OrderDeliveryEntity delivery;

  /** Required by JPA; not for application use. */
  protected OrderEntity() {}

  /**
   * @param id the primary key
   * @param orderNumber the business identifier
   * @param channel the sales channel
   * @param fulfillment how the order reaches the customer
   * @param status the fulfillment status
   * @param customerId the linked customer, or {@code null}
   * @param buyerFirstNameEncrypted the encrypted buyer first name, or {@code null} if purged
   * @param buyerLastNameEncrypted the encrypted buyer last name, or {@code null} if purged
   * @param buyerEmailEncrypted the encrypted buyer email, or {@code null} if purged
   * @param buyerEmailHash the buyer email HMAC, or {@code null} if purged
   * @param buyerPhoneEncrypted the encrypted buyer phone, or {@code null} if purged
   * @param buyerPhoneHash the buyer phone HMAC, or {@code null} if purged
   * @param subtotal the subtotal
   * @param shippingCost the shipping cost
   * @param placedAt when the order was placed
   * @param retentionUntil the PII retention deadline, or {@code null}
   * @param personalDataPurgedAt when PII was purged, or {@code null}
   * @param version the optimistic-locking version
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted entity
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted entity
   */
  public OrderEntity(
      UUID id,
      String orderNumber,
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
      BigDecimal subtotal,
      BigDecimal shippingCost,
      Instant placedAt,
      LocalDate retentionUntil,
      Instant personalDataPurgedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.orderNumber = orderNumber;
    this.channel = channel;
    this.fulfillment = fulfillment;
    this.status = status;
    this.customerId = customerId;
    this.buyerFirstNameEncrypted = copy(buyerFirstNameEncrypted);
    this.buyerLastNameEncrypted = copy(buyerLastNameEncrypted);
    this.buyerEmailEncrypted = copy(buyerEmailEncrypted);
    this.buyerEmailHash = copy(buyerEmailHash);
    this.buyerPhoneEncrypted = copy(buyerPhoneEncrypted);
    this.buyerPhoneHash = copy(buyerPhoneHash);
    this.subtotal = subtotal;
    this.shippingCost = shippingCost;
    this.placedAt = placedAt;
    this.retentionUntil = retentionUntil;
    this.personalDataPurgedAt = personalDataPurgedAt;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * @param value a byte array, or {@code null}
   * @return a defensive copy, or {@code null}
   */
  private static byte[] copy(byte[] value) {
    return value == null ? null : value.clone();
  }

  /** Sets {@code createdAt}/{@code updatedAt} if the application code left them unset. */
  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (placedAt == null) {
      placedAt = now;
    }
    updatedAt = now;
    LOGGER.debug("onCreate id={} orderNumber={}", id, orderNumber);
  }

  /** Refreshes {@code updatedAt} on every update. */
  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
    LOGGER.debug("onUpdate id={} orderNumber={}", id, orderNumber);
  }

  /** @return the primary key */
  public UUID getId() {
    return id;
  }

  /** @return the business identifier */
  public String getOrderNumber() {
    return orderNumber;
  }

  /** @return the sales channel */
  public OrderChannel getChannel() {
    return channel;
  }

  /** @return how the order reaches the customer */
  public OrderFulfillment getFulfillment() {
    return fulfillment;
  }

  /** @return the fulfillment status */
  public OrderStatus getStatus() {
    return status;
  }

  /** @return the linked customer, or {@code null} */
  public UUID getCustomerId() {
    return customerId;
  }

  /** @return the encrypted buyer first name, or {@code null} if purged */
  public byte[] getBuyerFirstNameEncrypted() {
    return copy(buyerFirstNameEncrypted);
  }

  /** @return the encrypted buyer last name, or {@code null} if purged */
  public byte[] getBuyerLastNameEncrypted() {
    return copy(buyerLastNameEncrypted);
  }

  /** @return the encrypted buyer email, or {@code null} if purged */
  public byte[] getBuyerEmailEncrypted() {
    return copy(buyerEmailEncrypted);
  }

  /** @return the buyer email HMAC, or {@code null} if purged */
  public byte[] getBuyerEmailHash() {
    return copy(buyerEmailHash);
  }

  /** @return the encrypted buyer phone, or {@code null} if purged */
  public byte[] getBuyerPhoneEncrypted() {
    return copy(buyerPhoneEncrypted);
  }

  /** @return the buyer phone HMAC, or {@code null} if purged */
  public byte[] getBuyerPhoneHash() {
    return copy(buyerPhoneHash);
  }

  /** @return the subtotal */
  public BigDecimal getSubtotal() {
    return subtotal;
  }

  /** @return the shipping cost */
  public BigDecimal getShippingCost() {
    return shippingCost;
  }

  /** @return when the order was placed */
  public Instant getPlacedAt() {
    return placedAt;
  }

  /** @return the PII retention deadline, or {@code null} */
  public LocalDate getRetentionUntil() {
    return retentionUntil;
  }

  /** @return when PII was purged, or {@code null} */
  public Instant getPersonalDataPurgedAt() {
    return personalDataPurgedAt;
  }

  /** @return the optimistic-locking version */
  public long getVersion() {
    return version;
  }

  /** @return when the row was created */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** @return when the row was last updated */
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /** @param status the fulfillment status */
  public void setStatus(OrderStatus status) {
    this.status = status;
  }

  /** @param customerId the linked customer, or {@code null} */
  public void setCustomerId(UUID customerId) {
    this.customerId = customerId;
  }

  /** @param buyerFirstNameEncrypted the encrypted buyer first name, or {@code null} */
  public void setBuyerFirstNameEncrypted(byte[] buyerFirstNameEncrypted) {
    this.buyerFirstNameEncrypted = copy(buyerFirstNameEncrypted);
  }

  /** @param buyerLastNameEncrypted the encrypted buyer last name, or {@code null} */
  public void setBuyerLastNameEncrypted(byte[] buyerLastNameEncrypted) {
    this.buyerLastNameEncrypted = copy(buyerLastNameEncrypted);
  }

  /** @param buyerEmailEncrypted the encrypted buyer email, or {@code null} */
  public void setBuyerEmailEncrypted(byte[] buyerEmailEncrypted) {
    this.buyerEmailEncrypted = copy(buyerEmailEncrypted);
  }

  /** @param buyerEmailHash the buyer email HMAC, or {@code null} */
  public void setBuyerEmailHash(byte[] buyerEmailHash) {
    this.buyerEmailHash = copy(buyerEmailHash);
  }

  /** @param buyerPhoneEncrypted the encrypted buyer phone, or {@code null} */
  public void setBuyerPhoneEncrypted(byte[] buyerPhoneEncrypted) {
    this.buyerPhoneEncrypted = copy(buyerPhoneEncrypted);
  }

  /** @param buyerPhoneHash the buyer phone HMAC, or {@code null} */
  public void setBuyerPhoneHash(byte[] buyerPhoneHash) {
    this.buyerPhoneHash = copy(buyerPhoneHash);
  }

  /** @param subtotal the subtotal */
  public void setSubtotal(BigDecimal subtotal) {
    this.subtotal = subtotal;
  }

  /** @param shippingCost the shipping cost */
  public void setShippingCost(BigDecimal shippingCost) {
    this.shippingCost = shippingCost;
  }

  /** @param placedAt when the order was placed */
  public void setPlacedAt(Instant placedAt) {
    this.placedAt = placedAt;
  }

  /** @param retentionUntil the PII retention deadline, or {@code null} */
  public void setRetentionUntil(LocalDate retentionUntil) {
    this.retentionUntil = retentionUntil;
  }

  /** @param personalDataPurgedAt when PII was purged, or {@code null} */
  public void setPersonalDataPurgedAt(Instant personalDataPurgedAt) {
    this.personalDataPurgedAt = personalDataPurgedAt;
  }

  /** @return an unmodifiable view of the sold lines */
  public List<OrderItemEntity> getItems() {
    return List.copyOf(items);
  }

  /**
   * @param items the sold lines, with each child's {@code order} back-reference already set
   */
  public void setItems(List<OrderItemEntity> items) {
    this.items = new ArrayList<>(items == null ? List.of() : items);
  }

  /** @return the delivery/pickup slot, or {@code null} for {@code NONE} */
  public OrderDeliveryEntity getDelivery() {
    return delivery;
  }

  /**
   * @param delivery the delivery/pickup slot, with its {@code order} back-reference already set,
   *     or {@code null} for {@code NONE}
   */
  public void setDelivery(OrderDeliveryEntity delivery) {
    this.delivery = delivery;
  }
}
