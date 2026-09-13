package com.floristeriarosy.application.payment.command;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;

/**
 * @param orderId the order whose captured payment must be refunded in full
 * @param orderNumber the original charge's business identifier, needed by Redsys for refund
 *     signature diversification (ADR-017)
 */
public record RefundOrderPaymentCommand(OrderId orderId, OrderNumber orderNumber) {}
