package com.floristeriarosy.application.cart.support;

import com.floristeriarosy.domain.exception.ResourceModifiedException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries a cart write once when it loses the {@code @Version} race (ADR-009 amendment, 2026-09-06)
 * — never surfaces the conflict to the caller as 409, unlike every other {@code @Version} aggregate.
 * Safe specifically because a cart write is idempotent against itself: "add 2 units of product X"
 * or "set quantity to 5" produce the same end state whether applied against the first-read row or
 * the row re-read after losing a race, unlike an admin's arbitrary field edit on {@code products}.
 *
 * <p>{@code operation} must re-resolve the cart from scratch on each call — retrying with the same
 * stale in-memory {@code Cart} would just lose the race again. Kept as one small helper rather than
 * repeating this try/retry in five services, same justification as {@link CartFinder}.
 */
public final class CartOptimisticRetry {

  private static final Logger LOGGER = LoggerFactory.getLogger(CartOptimisticRetry.class);

  private CartOptimisticRetry() {}

  /**
   * @param operation the full resolve-mutate-persist sequence, safe to run twice
   * @param <T> the operation's result type
   * @return the operation's result, from whichever attempt succeeded
   * @throws ResourceModifiedException the retry itself lost the race too — sustained contention,
   *     not the one-off overlap this exists to smooth over
   */
  public static <T> T withRetry(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (ResourceModifiedException conflict) {
      LOGGER.debug("withRetry -> lost the version race once, retrying");
      return operation.get();
    }
  }

  /**
   * {@link #withRetry(Supplier)} for an operation with no result to return.
   *
   * @param operation the full resolve-mutate-persist sequence, safe to run twice
   * @throws ResourceModifiedException the retry itself lost the race too
   */
  public static void withRetry(Runnable operation) {
    withRetry(
        () -> {
          operation.run();
          return null;
        });
  }
}
