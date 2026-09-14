package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.dto.CheckoutStatusDto;
import com.floristeriarosy.application.order.port.in.GetCheckoutStatusUseCase;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.IdempotencyLock;
import com.floristeriarosy.application.order.query.GetCheckoutStatusQuery;
import com.floristeriarosy.domain.exception.order.CheckoutNotFoundException;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetCheckoutStatusUseCase}: a thin read over the {@code POST /checkout}
 * idempotency lock, for the frontend to resolve a checkout attempt after returning from Redsys
 * (ADR-017). Does not resolve {@code orderNumber} for a {@code COMPLETED} attempt in this phase —
 * that needs {@code OrderReadPort} (Phase 3b); leaving it out here is the deliberate scope boundary
 * between the two phases, not an oversight.
 */
@Service
public class GetCheckoutStatusService implements GetCheckoutStatusUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetCheckoutStatusService.class);

  private final CheckoutIdempotencyPort idempotencyPort;

  /**
   * @param idempotencyPort reads the checkout lock
   */
  public GetCheckoutStatusService(CheckoutIdempotencyPort idempotencyPort) {
    this.idempotencyPort = idempotencyPort;
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
    CheckoutStatusDto result = new CheckoutStatusDto(lock.status(), null, null);
    LOGGER.debug(
        "getCheckoutStatus idempotencyKey={} -> {}",
        Encode.forJava(query.idempotencyKey()),
        lock.status());
    return result;
  }
}
