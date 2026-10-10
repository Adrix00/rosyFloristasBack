package com.floristeriarosy.application.cart.support;

import com.floristeriarosy.domain.exception.ResourceModifiedException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Retries a cart write once when it loses the {@code @Version} race (ADR-009 amendment, 2026-09-06)
 * — never surfaces the conflict to the caller as 409, unlike every other {@code @Version} aggregate.
 * Safe specifically because a cart write is idempotent against itself: "add 2 units of product X"
 * or "set quantity to 5" produce the same end state whether applied against the first-read row or
 * the row re-read after losing a race, unlike an admin's arbitrary field edit on {@code products}.
 *
 * <p>Each attempt runs in its own transaction, so the retry gets a fresh Hibernate session and
 * re-reads the row. Retrying inside the caller's transaction cannot work: the failed attempt leaves
 * its stale, dirty entity in the session, which the retry's first query flushes and fails on again.
 * That is why the cart services are not {@code @Transactional} themselves: this class owns the
 * boundary, and one attempt is one transaction.
 *
 * <p>The transaction is {@code REQUIRES_NEW}, not {@code REQUIRED}: a caller that already has one
 * ({@code ConfirmCheckoutService} clears the cart inside the order confirmation) would otherwise
 * make the attempt join it, so the retry would be just as ineffective and a lost race would mark the
 * caller's whole transaction rollback-only.
 *
 * <p>{@code operation} must re-resolve the cart from scratch on each call — retrying with the same
 * stale in-memory {@code Cart} would just lose the race again. Kept as one small helper rather than
 * repeating this try/retry in five services, same justification as {@link CartFinder}.
 */
@Component
public class CartOptimisticRetry {

  private static final Logger LOGGER = LoggerFactory.getLogger(CartOptimisticRetry.class);

  private final TransactionTemplate transactionTemplate;

  /**
   * @param transactionManager opens each attempt's transaction
   */
  public CartOptimisticRetry(PlatformTransactionManager transactionManager) {
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * @param operation the full resolve-mutate-persist sequence, safe to run twice
   * @param <T> the operation's result type
   * @return the operation's result, from whichever attempt succeeded
   * @throws ResourceModifiedException the retry itself lost the race too — sustained contention,
   *     not the one-off overlap this exists to smooth over
   */
  public <T> T withRetry(Supplier<T> operation) {
    try {
      return attempt(operation);
    } catch (ResourceModifiedException conflict) {
      LOGGER.debug("withRetry -> lost the version race once, retrying");
      return attempt(operation);
    }
  }

  /**
   * {@link #withRetry(Supplier)} for an operation with no result to return.
   *
   * @param operation the full resolve-mutate-persist sequence, safe to run twice
   * @throws ResourceModifiedException the retry itself lost the race too
   */
  public void withRetry(Runnable operation) {
    withRetry(
        () -> {
          operation.run();
          return null;
        });
  }

  /**
   * @param operation the sequence to run
   * @param <T> the operation's result type
   * @return the operation's result, committed in its own transaction
   */
  private <T> T attempt(Supplier<T> operation) {
    return transactionTemplate.execute(status -> operation.get());
  }
}
