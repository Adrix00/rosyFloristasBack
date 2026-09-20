package com.floristeriarosy.application.cart.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.floristeriarosy.domain.exception.ResourceModifiedException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/** {@link CartOptimisticRetry}: ADR-009 amendment (2026-09-06). */
class CartOptimisticRetryTest {

  private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
  private final CartOptimisticRetry retry = new CartOptimisticRetry(transactionManager);

  @Test
  void returnsTheResultWhenTheFirstAttemptSucceeds() {
    AtomicInteger attempts = new AtomicInteger();
    String result =
        retry.withRetry(
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
        retry.withRetry(
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
  void runsEachAttemptInItsOwnTransaction() {
    AtomicInteger attempts = new AtomicInteger();

    retry.withRetry(
        () -> {
          if (attempts.incrementAndGet() == 1) {
            throw new ResourceModifiedException("lost the race");
          }
          return "ok-on-retry";
        });

    verify(transactionManager, times(2)).getTransaction(any(TransactionDefinition.class));
    verify(transactionManager).rollback(any());
    verify(transactionManager).commit(any());
  }

  @Test
  void propagatesTheConflictWhenTheRetryLosesTheRaceTooInsteadOfLoopingForever() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                retry.withRetry(
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

    retry.withRetry(
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
                retry.withRetry(
                    () -> {
                      throw new IllegalStateException("not a version conflict");
                    }))
        .isInstanceOf(IllegalStateException.class);
  }
}
