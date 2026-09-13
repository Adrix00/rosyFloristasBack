package com.floristeriarosy.domain.model.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where and when an order is delivered or picked up (order.md, rule 3.8). Exists only for {@link
 * OrderFulfillment#DELIVERY} and {@link OrderFulfillment#PICKUP} — {@code
 * OrderFulfillment#NONE} carries none, and is never wrapped by this type. For {@code PICKUP}, the
 * recipient and address fields are {@code null}: the buyer collects in person, there is nothing to
 * encrypt.
 */
public final class OrderDelivery {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderDelivery.class);

  private final byte[] recipientNameEncrypted;
  private final byte[] recipientPhoneEncrypted;
  private final byte[] streetEncrypted;
  private final byte[] numberEncrypted;
  private final byte[] detailEncrypted;
  private final String postalCode;
  private final byte[] latitudeEncrypted;
  private final byte[] longitudeEncrypted;
  private final BigDecimal distanceKm;
  private final LocalDate deliveryDate;
  private final LocalTime slotFrom;
  private final LocalTime slotTo;
  private final byte[] cardMessageEncrypted;
  private final Instant createdAt;
  private final Instant updatedAt;

  /**
   * Private constructor. Use {@link #forDelivery}, {@link #forPickup} or {@link #reconstitute}.
   *
   * @param recipientNameEncrypted the encrypted recipient name, or {@code null} for pickup
   * @param recipientPhoneEncrypted the encrypted recipient phone, or {@code null} for pickup
   * @param streetEncrypted the encrypted street, or {@code null} for pickup
   * @param numberEncrypted the encrypted street number, or {@code null} for pickup
   * @param detailEncrypted the encrypted address detail (floor, door), or {@code null}
   * @param postalCode the postal code, or {@code null} for pickup
   * @param latitudeEncrypted the encrypted geocoded latitude, or {@code null} for pickup
   * @param longitudeEncrypted the encrypted geocoded longitude, or {@code null} for pickup
   * @param distanceKm the straight-line distance from the store, or {@code null} for pickup
   * @param deliveryDate the requested delivery or pickup date
   * @param slotFrom the requested slot's start time
   * @param slotTo the requested slot's end time
   * @param cardMessageEncrypted the encrypted card message, or {@code null}
   * @param createdAt when the row was created, or {@code null} before the first save
   * @param updatedAt when the row was last updated, or {@code null} before the first save
   */
  private OrderDelivery(
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
    this.recipientNameEncrypted = recipientNameEncrypted;
    this.recipientPhoneEncrypted = recipientPhoneEncrypted;
    this.streetEncrypted = streetEncrypted;
    this.numberEncrypted = numberEncrypted;
    this.detailEncrypted = detailEncrypted;
    this.postalCode = postalCode;
    this.latitudeEncrypted = latitudeEncrypted;
    this.longitudeEncrypted = longitudeEncrypted;
    this.distanceKm = distanceKm;
    this.deliveryDate = deliveryDate;
    this.slotFrom = slotFrom;
    this.slotTo = slotTo;
    this.cardMessageEncrypted = cardMessageEncrypted;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * A home-delivery slot, with recipient and address (order.md, rule 3.8).
   *
   * @param recipientNameEncrypted the encrypted recipient name
   * @param recipientPhoneEncrypted the encrypted recipient phone
   * @param streetEncrypted the encrypted street
   * @param numberEncrypted the encrypted street number
   * @param detailEncrypted the encrypted address detail (floor, door), or {@code null}
   * @param postalCode the postal code, not encrypted
   * @param latitudeEncrypted the encrypted geocoded latitude
   * @param longitudeEncrypted the encrypted geocoded longitude
   * @param distanceKm the straight-line distance from the store, in kilometers
   * @param deliveryDate the requested delivery date
   * @param slotFrom the requested slot's start time
   * @param slotTo the requested slot's end time
   * @param cardMessageEncrypted the encrypted card message, or {@code null}
   * @return the new delivery slot
   * @throws IllegalArgumentException {@code slotFrom} is not before {@code slotTo}
   */
  public static OrderDelivery forDelivery(
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
      byte[] cardMessageEncrypted) {
    requireValidSlot(slotFrom, slotTo);
    LOGGER.debug(
        "forDelivery deliveryDate={} slotFrom={} slotTo={} distanceKm={}",
        deliveryDate,
        slotFrom,
        slotTo,
        distanceKm);
    OrderDelivery result =
        new OrderDelivery(
            recipientNameEncrypted,
            recipientPhoneEncrypted,
            streetEncrypted,
            numberEncrypted,
            detailEncrypted,
            postalCode,
            latitudeEncrypted,
            longitudeEncrypted,
            distanceKm,
            deliveryDate,
            slotFrom,
            slotTo,
            cardMessageEncrypted,
            null,
            null);
    LOGGER.debug("forDelivery deliveryDate={} -> created", deliveryDate);
    return result;
  }

  /**
   * A pickup slot: no recipient, no address — the buyer (or whoever they name in store) collects in
   * person (order.md, rule 3.8).
   *
   * @param deliveryDate the requested pickup date
   * @param slotFrom the requested slot's start time
   * @param slotTo the requested slot's end time
   * @return the new pickup slot
   * @throws IllegalArgumentException {@code slotFrom} is not before {@code slotTo}
   */
  public static OrderDelivery forPickup(LocalDate deliveryDate, LocalTime slotFrom, LocalTime slotTo) {
    requireValidSlot(slotFrom, slotTo);
    LOGGER.debug("forPickup deliveryDate={} slotFrom={} slotTo={}", deliveryDate, slotFrom, slotTo);
    OrderDelivery result =
        new OrderDelivery(
            null, null, null, null, null, null, null, null, null, deliveryDate, slotFrom, slotTo, null,
            null, null);
    LOGGER.debug("forPickup deliveryDate={} -> created", deliveryDate);
    return result;
  }

  /**
   * Guards that the requested slot has a positive duration.
   *
   * @param slotFrom the requested slot's start time
   * @param slotTo the requested slot's end time
   * @throws IllegalArgumentException {@code slotFrom} is not before {@code slotTo}
   */
  private static void requireValidSlot(LocalTime slotFrom, LocalTime slotTo) {
    if (!slotFrom.isBefore(slotTo)) {
      throw new IllegalArgumentException("slotFrom must be before slotTo");
    }
  }

  /**
   * Rebuilds a delivery/pickup slot from persisted state. Used only by the persistence mapper.
   *
   * @param recipientNameEncrypted the persisted encrypted recipient name, or {@code null} for pickup
   * @param recipientPhoneEncrypted the persisted encrypted recipient phone, or {@code null} for
   *     pickup
   * @param streetEncrypted the persisted encrypted street, or {@code null} for pickup
   * @param numberEncrypted the persisted encrypted street number, or {@code null} for pickup
   * @param detailEncrypted the persisted encrypted address detail, or {@code null}
   * @param postalCode the persisted postal code, or {@code null} for pickup
   * @param latitudeEncrypted the persisted encrypted latitude, or {@code null} for pickup
   * @param longitudeEncrypted the persisted encrypted longitude, or {@code null} for pickup
   * @param distanceKm the persisted distance in kilometers, or {@code null} for pickup
   * @param deliveryDate the persisted date
   * @param slotFrom the persisted slot start
   * @param slotTo the persisted slot end
   * @param cardMessageEncrypted the persisted encrypted card message, or {@code null}
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt delivery/pickup slot
   */
  public static OrderDelivery reconstitute(
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
    return new OrderDelivery(
        recipientNameEncrypted,
        recipientPhoneEncrypted,
        streetEncrypted,
        numberEncrypted,
        detailEncrypted,
        postalCode,
        latitudeEncrypted,
        longitudeEncrypted,
        distanceKm,
        deliveryDate,
        slotFrom,
        slotTo,
        cardMessageEncrypted,
        createdAt,
        updatedAt);
  }

  /**
   * @return the encrypted recipient name, or {@code null} for pickup
   */
  public byte[] recipientNameEncrypted() {
    return recipientNameEncrypted == null ? null : recipientNameEncrypted.clone();
  }

  /**
   * @return the encrypted recipient phone, or {@code null} for pickup
   */
  public byte[] recipientPhoneEncrypted() {
    return recipientPhoneEncrypted == null ? null : recipientPhoneEncrypted.clone();
  }

  /**
   * @return the encrypted street, or {@code null} for pickup
   */
  public byte[] streetEncrypted() {
    return streetEncrypted == null ? null : streetEncrypted.clone();
  }

  /**
   * @return the encrypted street number, or {@code null} for pickup
   */
  public byte[] numberEncrypted() {
    return numberEncrypted == null ? null : numberEncrypted.clone();
  }

  /**
   * @return the encrypted address detail, or {@code null}
   */
  public byte[] detailEncrypted() {
    return detailEncrypted == null ? null : detailEncrypted.clone();
  }

  /**
   * @return the postal code, or {@code null} for pickup
   */
  public String postalCode() {
    return postalCode;
  }

  /**
   * @return the encrypted geocoded latitude, or {@code null} for pickup
   */
  public byte[] latitudeEncrypted() {
    return latitudeEncrypted == null ? null : latitudeEncrypted.clone();
  }

  /**
   * @return the encrypted geocoded longitude, or {@code null} for pickup
   */
  public byte[] longitudeEncrypted() {
    return longitudeEncrypted == null ? null : longitudeEncrypted.clone();
  }

  /**
   * @return the straight-line distance from the store, in kilometers, or {@code null} for pickup
   */
  public BigDecimal distanceKm() {
    return distanceKm;
  }

  /**
   * @return the requested delivery or pickup date
   */
  public LocalDate deliveryDate() {
    return deliveryDate;
  }

  /**
   * @return the requested slot's start time
   */
  public LocalTime slotFrom() {
    return slotFrom;
  }

  /**
   * @return the requested slot's end time
   */
  public LocalTime slotTo() {
    return slotTo;
  }

  /**
   * @return the encrypted card message, or {@code null}
   */
  public byte[] cardMessageEncrypted() {
    return cardMessageEncrypted == null ? null : cardMessageEncrypted.clone();
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
