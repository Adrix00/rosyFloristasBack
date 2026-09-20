package com.floristeriarosy.application.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything {@code ConfirmCheckoutService} (Phase 3b) needs to materialize the order once Redsys
 * confirms — serialized to JSON, then encrypted as one blob into {@code
 * idempotency_keys.checkout_payload_encrypted} (ADR-017). Never returned to a client.
 *
 * @param channel the sales channel
 * @param fulfillment how the order reaches the customer
 * @param orderNumber the business order number minted at Fase 1; {@code Ds_Order} in Redsys's
 *     notification is only the 12-digit technical id, so the real number is recovered from this
 *     payload
 * @param customerId the linked customer, or {@code null} for an unlinked guest checkout
 * @param sessionToken the guest cart's session token, or {@code null} with a session
 * @param buyerFirstName the buyer's first name
 * @param buyerLastName the buyer's last name
 * @param buyerEmail the buyer's email
 * @param buyerPhone the buyer's phone
 * @param items the priced, snapshotted lines to sell
 * @param delivery the delivery slot, or {@code null} for {@code PICKUP}/{@code NONE}
 * @param pickup the pickup slot, or {@code null} for {@code DELIVERY}/{@code NONE}
 * @param subtotal the sum of every line's total
 * @param shippingCost the computed shipping cost
 */
public record PendingCheckoutPayload(
    String channel,
    String fulfillment,
    String orderNumber,
    UUID customerId,
    String sessionToken,
    String buyerFirstName,
    String buyerLastName,
    String buyerEmail,
    String buyerPhone,
    List<Item> items,
    Delivery delivery,
    Pickup pickup,
    BigDecimal subtotal,
    BigDecimal shippingCost) {

  /**
   * Defensively copies {@code items} (SpotBugs EI_EXPOSE_REP2). Nested {@link Item#productAttributes}
   * is copied by {@link Item}'s own compact constructor.
   */
  public PendingCheckoutPayload {
    items = List.copyOf(items);
  }

  /**
   * @param productId the sold product
   * @param productName the product's name at the moment of sale
   * @param productAttributes the product's attribute values at the moment of sale
   * @param unitPrice the base price at the moment of sale
   * @param discountPrice the discounted price, or {@code null}
   * @param discountId the applied promotion, or {@code null}
   * @param quantity units sold
   */
  public record Item(
      UUID productId,
      String productName,
      Map<String, Object> productAttributes,
      BigDecimal unitPrice,
      BigDecimal discountPrice,
      UUID discountId,
      int quantity) {

    /**
     * Defensively copies {@code productAttributes} (SpotBugs EI_EXPOSE_REP2), using a plain {@link
     * LinkedHashMap} rather than {@link Map#copyOf} so a {@code null} value is tolerated.
     */
    public Item {
      productAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(productAttributes));
    }
  }

  /**
   * @param recipientName the recipient's name
   * @param recipientPhone the recipient's phone
   * @param street the street
   * @param number the street number
   * @param detail the address detail, or {@code null}
   * @param postalCode the postal code
   * @param latitude the geocoded latitude
   * @param longitude the geocoded longitude
   * @param distanceKm the distance from the store
   * @param deliveryDate the requested date
   * @param slotFrom the slot's start
   * @param slotTo the slot's end
   * @param cardMessage the card message, or {@code null}
   */
  public record Delivery(
      String recipientName,
      String recipientPhone,
      String street,
      String number,
      String detail,
      String postalCode,
      BigDecimal latitude,
      BigDecimal longitude,
      BigDecimal distanceKm,
      LocalDate deliveryDate,
      LocalTime slotFrom,
      LocalTime slotTo,
      String cardMessage) {}

  /**
   * @param deliveryDate the requested date
   * @param slotFrom the slot's start
   * @param slotTo the slot's end
   */
  public record Pickup(LocalDate deliveryDate, LocalTime slotFrom, LocalTime slotTo) {}
}
