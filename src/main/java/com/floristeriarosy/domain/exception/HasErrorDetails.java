package com.floristeriarosy.domain.exception;

import java.util.Map;

/**
 * Implemented by a domain exception whose RFC 7807 body needs a structured field beyond {@code
 * code} (ADR-012) — e.g. cart.md's {@code CART_INSUFFICIENT_STOCK}, which must carry the real
 * {@code availableQuantity} alongside the message. Kept generic, in {@code domain.exception}
 * itself, rather than a per-module handler method in {@code GlobalExceptionHandler}: any future
 * module with the same need (order.md's insufficient-stock-at-checkout, for instance) gets the
 * same wiring for free.
 */
public interface HasErrorDetails {

  /**
   * @return the extra fields to add to the RFC 7807 body, keyed by their JSON property name
   */
  Map<String, Object> errorDetails();
}
