package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.dto.CheckoutStatusDto;
import com.floristeriarosy.application.order.port.in.GetCheckoutStatusUseCase;
import com.floristeriarosy.application.order.dto.IdempotencyLock;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.query.GetCheckoutStatusQuery;
import com.floristeriarosy.domain.exception.order.CheckoutNotFoundException;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import java.util.Optional;
import java.util.UUID;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetCheckoutStatusUseCase}: a thin read over the {@code POST /checkout}
 * idempotency lock, for the frontend to resolve a checkout attempt after returning from Redsys
 * (ADR-017). Resolves the confirmed order's number when the lock is {@code COMPLETED}.
 */
@Service
public class GetCheckoutStatusService implements GetCheckoutStatusUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetCheckoutStatusService.class);

  private final CheckoutIdempotencyPort idempotencyPort;
  private final OrderReadPort orderReadPort;

  /**
   * @param idempotencyPort reads the checkout lock
   * @param orderReadPort resolves the confirmed order's business identifier
   */
  public GetCheckoutStatusService(
      CheckoutIdempotencyPort idempotencyPort, OrderReadPort orderReadPort) {
    this.idempotencyPort = idempotencyPort;
    this.orderReadPort = orderReadPort;
  }

  /**
   * @param query the idempotency key to look up
   * @return the current status
   * @throws CheckoutNotFoundException no attempt exists for that key
   */
  @Override
  public CheckoutStatusDto execute(GetCheckoutStatusQuery query) {
    LOGGER.debug("getCheckoutStatus idempotencyKey={}", Encode.forJava(query.idempotencyKey()));
    IdempotencyLock lock =
        idempotencyPort
            .find(query.idempotencyKey())
            .orElseThrow(
                () ->
                    new CheckoutNotFoundException(
                        "No checkout attempt for " + query.idempotencyKey()));
    String orderNumber =
        "COMPLETED".equals(lock.status()) ? resolveOrderNumber(lock.resourceId()) : null;
    CheckoutStatusDto result = new CheckoutStatusDto(lock.status(), orderNumber, null);
    LOGGER.debug(
        "getCheckoutStatus idempotencyKey={} status={} orderNumber={}",
        Encode.forJava(query.idempotencyKey()),
        lock.status(),
        Encode.forJava(orderNumber));
    return result;
  }

  /**
   * @param orderId the order identifier stored as the lock's resource id
   * @return the order's business number, or {@code null} if the order is not yet visible
   */
  private String resolveOrderNumber(UUID orderId) {
    if (orderId == null) {
      return null;
    }
    Optional<Order> order = orderReadPort.findById(OrderId.of(orderId));
    return order.map(value -> value.orderNumber().value()).orElse(null);
  }
}
