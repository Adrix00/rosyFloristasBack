package com.floristeriarosy.infrastructure.web.request.order;

import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @param fulfillment how the order reaches the customer
 * @param paymentMethod always {@code CARD_ONLINE} for {@code WEB} (order.md, §5)
 * @param paymentMethodToken a new card's gateway token — alternative to {@code paymentMethodId}
 * @param paymentMethodId a saved card's id — alternative to {@code paymentMethodToken}, session only
 * @param buyer the buyer's details, required without a session
 * @param addressId a saved address id — alternative to {@code delivery}
 * @param delivery the delivery details, required when {@code fulfillment = DELIVERY} without
 *     {@code addressId}
 * @param pickup the pickup details, required when {@code fulfillment = PICKUP}
 */
public record CheckoutRequest(
    @NotNull OrderFulfillment fulfillment,
    @NotNull PaymentMethod paymentMethod,
    String paymentMethodToken,
    UUID paymentMethodId,
    @Valid BuyerRequest buyer,
    UUID addressId,
    @Valid DeliveryRequest delivery,
    @Valid PickupRequest pickup) {}
