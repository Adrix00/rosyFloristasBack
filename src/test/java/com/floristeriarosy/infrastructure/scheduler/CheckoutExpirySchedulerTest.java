package com.floristeriarosy.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.port.in.ReleaseExpiredCheckoutsUseCase;
import org.junit.jupiter.api.Test;

/** {@link CheckoutExpiryScheduler} — a scheduled call must never throw out of the scheduler thread. */
class CheckoutExpirySchedulerTest {

  private final ReleaseExpiredCheckoutsUseCase useCase = mock(ReleaseExpiredCheckoutsUseCase.class);
  private final CheckoutExpiryScheduler scheduler = new CheckoutExpiryScheduler(useCase);

  @Test
  void runsTheCleanup() {
    when(useCase.execute()).thenReturn(3);

    scheduler.releaseExpiredCheckouts();

    verify(useCase).execute();
  }

  @Test
  void aFailingRunIsSwallowedSoTheNextTickStillHappens() {
    when(useCase.execute()).thenThrow(new IllegalStateException("db down"));

    assertThatCode(scheduler::releaseExpiredCheckouts).doesNotThrowAnyException();
  }
}
