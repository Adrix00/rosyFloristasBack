package com.floristeriarosy.application.order.command;

import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * @param customerId the logged-in customer, or {@code null} for a guest checkout
 * @param sessionToken the guest cart's session token, or {@code null} with a session
 * @param fulfillment how the order reaches the customer
 * @param paymentMethod always {@code CARD_ONLINE} for {@code WEB} (order.md, §5)
 * @param paymentMethodToken the gateway token for a new card
 * @param paymentMethodId a saved card's id — accepted by the DTO, not yet supported (Phase 6)
 * @param buyerFirstName the buyer's first name (guest form, or ignored with a session)
 * @param buyerLastName the buyer's last name
 * @param buyerEmail the buyer's email
 * @param buyerPhone the buyer's phone
 * @param addressId a saved address id — accepted by the DTO, not yet supported (Phase 6)
 * @param recipientName the delivery recipient's name
 * @param recipientPhone the delivery recipient's phone
 * @param street the delivery street
 * @param number the delivery street number
 * @param detail the delivery address detail, or {@code null}
 * @param postalCode the delivery postal code
 * @param deliveryDate the requested delivery/pickup date
 * @param slotFrom the requested slot's start
 * @param slotTo the requested slot's end
 * @param cardMessage the card message, or {@code null}
 * @param idempotencyKey the {@code Idempotency-Key} header value
 */
public record CheckoutCommand(
    UUID customerId,
    String sessionToken,
    OrderFulfillment fulfillment,
    PaymentMethod paymentMethod,
    String paymentMethodToken,
    UUID paymentMethodId,
    String buyerFirstName,
    String buyerLastName,
    String buyerEmail,
    String buyerPhone,
    UUID addressId,
    String recipientName,
    String recipientPhone,
    String street,
    String number,
    String detail,
    String postalCode,
    LocalDate deliveryDate,
    LocalTime slotFrom,
    LocalTime slotTo,
    String cardMessage,
    String idempotencyKey) {}
