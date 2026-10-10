package com.floristeriarosy.infrastructure.scheduler;

import com.floristeriarosy.application.order.port.in.ReleaseExpiredCheckoutsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the abandoned-checkout cleanup (ADR-017, scheduled-tasks.md §3.3) every 60 seconds, 60
 * seconds after the previous run finished: a customer who left Redsys without paying keeps their
 * stock reserved for at most the 10-minute expiry plus this delay.
 *
 * <p>On by default. The test resources switch it off ({@code
 * app.checkout-expiry.scheduler-enabled=false}) so a background run cannot claim the expired rows an
 * integration test seeds on purpose.
 */
@Component
@ConditionalOnProperty(
    name = "app.checkout-expiry.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CheckoutExpiryScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(CheckoutExpiryScheduler.class);

  private final ReleaseExpiredCheckoutsUseCase releaseExpiredCheckoutsUseCase;

  /**
   * @param releaseExpiredCheckoutsUseCase releases the reservation of every expired pending checkout
   */
  public CheckoutExpiryScheduler(ReleaseExpiredCheckoutsUseCase releaseExpiredCheckoutsUseCase) {
    this.releaseExpiredCheckoutsUseCase = releaseExpiredCheckoutsUseCase;
  }

  /** Runs 60 seconds after the previous run ended. A failure is logged, never thrown. */
  @Scheduled(fixedDelay = 60_000)
  public void releaseExpiredCheckouts() {
    LOGGER.debug("releaseExpiredCheckouts");
    try {
      int released = releaseExpiredCheckoutsUseCase.execute();
      LOGGER.debug("releaseExpiredCheckouts -> released={}", released);
    } catch (RuntimeException e) {
      LOGGER.error("releaseExpiredCheckouts -> run failed, will retry on the next tick", e);
    }
  }
}
