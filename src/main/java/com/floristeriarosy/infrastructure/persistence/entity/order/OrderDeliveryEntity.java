package com.floristeriarosy.infrastructure.persistence.entity.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * JPA mapping of the {@code order_deliveries} table (order.md, rule 3.8). Shares its primary key
 * with the owning {@link OrderEntity}. No {@code @Version}: the child is always persisted through
 * its owning order.
 */
@Entity
@Table(name = "order_deliveries")
public class OrderDeliveryEntity {

  @Id
  @Column(name = "order_id")
  private UUID orderId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id")
  private OrderEntity order;

  @Column(name = "recipient_name_encrypted")
  private byte[] recipientNameEncrypted;

  @Column(name = "recipient_phone_encrypted")
  private byte[] recipientPhoneEncrypted;

  @Column(name = "street_encrypted")
  private byte[] streetEncrypted;

  @Column(name = "number_encrypted")
  private byte[] numberEncrypted;

  @Column(name = "detail_encrypted")
  private byte[] detailEncrypted;

  @Column(name = "postal_code", length = 10)
  private String postalCode;

  @Column(name = "latitude_encrypted")
  private byte[] latitudeEncrypted;

  @Column(name = "longitude_encrypted")
  private byte[] longitudeEncrypted;

  @Column(name = "distance_km", precision = 5, scale = 2)
  private BigDecimal distanceKm;

  @Column(name = "delivery_date", nullable = false)
  private LocalDate deliveryDate;

  @Column(name = "slot_from", nullable = false)
  private LocalTime slotFrom;

  @Column(name = "slot_to", nullable = false)
  private LocalTime slotTo;

  @Column(name = "card_message_encrypted")
  private byte[] cardMessageEncrypted;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA; not for application use. */
  protected OrderDeliveryEntity() {}

  /**
   * @param orderId the primary key, equal to the owning order's id
   * @param order the owning order
   * @param recipientNameEncrypted the encrypted recipient name, or {@code null} for pickup
   * @param recipientPhoneEncrypted the encrypted recipient phone, or {@code null} for pickup
   * @param streetEncrypted the encrypted street, or {@code null} for pickup
   * @param numberEncrypted the encrypted street number, or {@code null} for pickup
   * @param detailEncrypted the encrypted address detail, or {@code null}
   * @param postalCode the postal code, or {@code null} for pickup
   * @param latitudeEncrypted the encrypted latitude, or {@code null} for pickup
   * @param longitudeEncrypted the encrypted longitude, or {@code null} for pickup
   * @param distanceKm the straight-line distance, or {@code null} for pickup
   * @param deliveryDate the requested delivery or pickup date
   * @param slotFrom the requested slot's start time
   * @param slotTo the requested slot's end time
   * @param cardMessageEncrypted the encrypted card message, or {@code null}
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted entity
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted entity
   */
  public OrderDeliveryEntity(
      UUID orderId,
      OrderEntity order,
      byte[] recipientNameEncrypted,
      byte[] recipientPhoneEncrypted,
      byte[] streetEncrypted,
      byte[] numberEncrypted,
      byte[] detailEncrypted,
      String postalCode,
      byte[] latitudeEncrypted,
      byte[] longitudeEncrypted,
      BigDecimal distanceKm,
      LocalDate deliveryDate,
      LocalTime slotFrom,
      LocalTime slotTo,
      byte[] cardMessageEncrypted,
      Instant createdAt,
      Instant updatedAt) {
    this.orderId = orderId;
    this.order = order;
    this.recipientNameEncrypted = copy(recipientNameEncrypted);
    this.recipientPhoneEncrypted = copy(recipientPhoneEncrypted);
    this.streetEncrypted = copy(streetEncrypted);
    this.numberEncrypted = copy(numberEncrypted);
    this.detailEncrypted = copy(detailEncrypted);
    this.postalCode = postalCode;
    this.latitudeEncrypted = copy(latitudeEncrypted);
    this.longitudeEncrypted = copy(longitudeEncrypted);
    this.distanceKm = distanceKm;
    this.deliveryDate = deliveryDate;
    this.slotFrom = slotFrom;
    this.slotTo = slotTo;
    this.cardMessageEncrypted = copy(cardMessageEncrypted);
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
    updatedAt = now;
  }

  /** Refreshes {@code updatedAt} on every update. */
  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  /** @return the primary key */
  public UUID getOrderId() {
    return orderId;
  }

  /** @return the owning order */
  public OrderEntity getOrder() {
    return order;
  }

  /**
   * @param order the owning order
   */
  public void setOrder(OrderEntity order) {
    this.order = order;
  }

  /** @return the encrypted recipient name, or {@code null} for pickup */
  public byte[] getRecipientNameEncrypted() {
    return copy(recipientNameEncrypted);
  }

  /** @return the encrypted recipient phone, or {@code null} for pickup */
  public byte[] getRecipientPhoneEncrypted() {
    return copy(recipientPhoneEncrypted);
  }

  /** @return the encrypted street, or {@code null} for pickup */
  public byte[] getStreetEncrypted() {
    return copy(streetEncrypted);
  }

  /** @return the encrypted street number, or {@code null} for pickup */
  public byte[] getNumberEncrypted() {
    return copy(numberEncrypted);
  }

  /** @return the encrypted address detail, or {@code null} */
  public byte[] getDetailEncrypted() {
    return copy(detailEncrypted);
  }

  /** @return the postal code, or {@code null} for pickup */
  public String getPostalCode() {
    return postalCode;
  }

  /** @return the encrypted latitude, or {@code null} for pickup */
  public byte[] getLatitudeEncrypted() {
    return copy(latitudeEncrypted);
  }

  /** @return the encrypted longitude, or {@code null} for pickup */
  public byte[] getLongitudeEncrypted() {
    return copy(longitudeEncrypted);
  }

  /** @return the straight-line distance, or {@code null} for pickup */
  public BigDecimal getDistanceKm() {
    return distanceKm;
  }

  /** @return the requested delivery or pickup date */
  public LocalDate getDeliveryDate() {
    return deliveryDate;
  }

  /** @return the requested slot's start time */
  public LocalTime getSlotFrom() {
    return slotFrom;
  }

  /** @return the requested slot's end time */
  public LocalTime getSlotTo() {
    return slotTo;
  }

  /** @return the encrypted card message, or {@code null} */
  public byte[] getCardMessageEncrypted() {
    return copy(cardMessageEncrypted);
  }

  /** @return when the row was created */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** @return when the row was last updated */
  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
