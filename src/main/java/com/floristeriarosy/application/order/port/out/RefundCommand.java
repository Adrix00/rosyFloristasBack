package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;

/**
 * What {@code order} asks {@link OrderPaymentPort#refund} to do — always a full refund of the
 * order's captured payment (payment.md, section 3.3).
 *
 * <p>{@code orderNumber} is included even though {@link OrderPaymentPort#refund} only needs the
 * order's identity, because Redsys requires the original charge's {@code Ds_Merchant_Order} to
 * diversify the refund signature (ADR-017). Carrying it here keeps the adapter translation
 * stateless and avoids a second join or denormalized column on {@code payments}.
 *
 * @param orderId the order whose payment must be refunded
 * @param orderNumber the original charge's business identifier, needed by Redsys for signature
 *     diversification
 */
public record RefundCommand(OrderId orderId, OrderNumber orderNumber) {}
