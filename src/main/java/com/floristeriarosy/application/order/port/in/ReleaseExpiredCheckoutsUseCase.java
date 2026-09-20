package com.floristeriarosy.application.order.port.in;

/**
 * The abandoned-checkout cleanup (ADR-017, scheduled-tasks.md §3.3): a {@code POST /checkout}
 * still {@code PENDING} past its 10-minute expiry never got a Redsys notification, so its Fase-1
 * reservation (stock, discount units) is given back and the row is marked {@code FAILED}. Internal:
 * invoked by a scheduler, never by an HTTP request.
 */
public interface ReleaseExpiredCheckoutsUseCase {

  /**
   * Releases up to one batch of expired checkouts, each in its own transaction, so a row that fails
   * neither blocks nor rolls back the others.
   *
   * @return how many checkouts were released
   */
  int execute();
}
