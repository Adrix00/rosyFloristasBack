package com.floristeriarosy.application.cart.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.ResourceModifiedException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** {@link CartOptimisticRetry}: ADR-009 amendment (2026-09-06). */
class CartOptimisticRetryTest {

  @Test
  void returnsTheResultWhenTheFirstAttemptSucceeds() {
    AtomicInteger attempts = new AtomicInteger();
    String result =
        CartOptimisticRetry.withRetry(
            () -> {
              attempts.incrementAndGet();
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void retriesExactlyOnceAfterLosingTheVersionRaceThenSucceeds() {
    AtomicInteger attempts = new AtomicInteger();
    String result =
        CartOptimisticRetry.withRetry(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw new ResourceModifiedException("lost the race");
              }
              return "ok-on-retry";
            });

    assertThat(result).isEqualTo("ok-on-retry");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void propagatesTheConflictWhenTheRetryLosesTheRaceTooInsteadOfLoopingForever() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                CartOptimisticRetry.withRetry(
                    () -> {
                      attempts.incrementAndGet();
                      throw new ResourceModifiedException("still losing");
                    }))
        .isInstanceOf(ResourceModifiedException.class);
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void aRunnableOverloadRetriesTheSameWayAsTheSupplierOne() {
    AtomicInteger attempts = new AtomicInteger();

    CartOptimisticRetry.withRetry(
        (Runnable)
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw new ResourceModifiedException("lost the race");
              }
            });

    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void doesNotCatchAnUnrelatedException() {
    assertThatThrownBy(
            () ->
                CartOptimisticRetry.withRetry(
                    () -> {
                      throw new IllegalStateException("not a version conflict");
                    }))
        .isInstanceOf(IllegalStateException.class);
  }
}
