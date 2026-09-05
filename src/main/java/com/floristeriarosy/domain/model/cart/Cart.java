package com.floristeriarosy.domain.model.cart;

import com.floristeriarosy.domain.exception.cart.CartItemLimitExceededException;
import com.floristeriarosy.domain.exception.cart.CartItemNotFoundException;
import com.floristeriarosy.domain.exception.cart.CartLineLimitExceededException;
import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root of the cart module (cart.md). Holds only {@code (productId, quantity)} lines —
 * never a price: {@code effectivePrice} is always computed live by {@code CartPricingPort} (rule
 * 3.5). {@code customerId} is a plain, nullable foreign reference, not a value object of its own:
 * the customer module has no aggregate yet for this branch to depend on (same reasoning
 * {@code Category.imageId} uses for the image module).
 */
public final class Cart {

  private static final Logger LOGGER = LoggerFactory.getLogger(Cart.class);

  /** Payload limit, defensive only, no business meaning (00-security-validation-integrity.md §4). */
  public static final int MAX_QUANTITY_PER_LINE = 99;

  /** Payload limit, defensive only, no business meaning (00-security-validation-integrity.md §4). */
  public static final int MAX_LINES = 50;

  private static final Duration GUEST_TTL = Duration.ofDays(30);
  private static final Duration CUSTOMER_TTL = Duration.ofDays(3650);

  private final CartId id;
  private UUID customerId;
  private final String sessionToken;
  private Instant expiresAt;
  private final Map<ProductId, Integer> items;
  private final Instant createdAt;
  private Instant updatedAt;

  private Cart(
      CartId id,
      UUID customerId,
      String sessionToken,
      Instant expiresAt,
      Map<ProductId, Integer> items,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.customerId = customerId;
    this.sessionToken = sessionToken;
    this.expiresAt = expiresAt;
    this.items = new LinkedHashMap<>(items);
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * A brand-new, empty cart (cart.md, rule 3.1: created lazily, only when the first item is
   * added). Its expiry is set from the {@code GUEST}/customer TTL depending on {@code customerId}.
   *
   * @param id application-generated identifier
   * @param customerId the owning customer, or {@code null} for a guest cart
   * @param sessionToken the backend-generated token identifying this cart while there is no
   *     session (rule 3.1)
   * @return the new, not-yet-persisted cart
   */
  public static Cart createEmpty(CartId id, UUID customerId, String sessionToken) {
    LOGGER.debug("createEmpty id={} hasCustomer={}", id, customerId != null);
    Instant now = Instant.now();
    Cart result =
        new Cart(
            id,
            customerId,
            sessionToken,
            now.plus(customerId != null ? CUSTOMER_TTL : GUEST_TTL),
            Map.of(),
            null,
            null);
    LOGGER.debug("createEmpty id={} -> created", id);
    return result;
  }

  /**
   * Rebuilds a cart from persisted state. Used only by the persistence mapper — not logged, it
   * runs once per cart loaded from the database.
   *
   * @param id the persisted identifier
   * @param customerId the persisted owning customer, or {@code null}
   * @param sessionToken the persisted session token
   * @param expiresAt the persisted expiry
   * @param items the persisted lines, keyed by product
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt cart
   */
  public static Cart reconstitute(
      CartId id,
      UUID customerId,
      String sessionToken,
      Instant expiresAt,
      Map<ProductId, Integer> items,
      Instant createdAt,
      Instant updatedAt) {
    return new Cart(id, customerId, sessionToken, expiresAt, items, createdAt, updatedAt);
  }

  /**
   * Adds a product or, if already present, sums {@code quantityToAdd} to its line (rule 3.3): the
   * {@code UNIQUE (cart_id, product_id)} constraint means one row per product, never a duplicate
   * line.
   *
   * @param productId the product to add
   * @param quantityToAdd the quantity to add, always positive
   * @throws CartLineLimitExceededException adding a new line would exceed {@link #MAX_LINES}
   * @throws CartItemLimitExceededException the resulting quantity would exceed {@link
   *     #MAX_QUANTITY_PER_LINE}
   */
  public void addOrIncrementItem(ProductId productId, int quantityToAdd) {
    LOGGER.debug("addOrIncrementItem id={} productId={} quantityToAdd={}", id, productId, quantityToAdd);
    Integer current = items.get(productId);
    if (current == null && items.size() >= MAX_LINES) {
      throw new CartLineLimitExceededException(
          "Cart " + id + " already has the maximum of " + MAX_LINES + " lines");
    }
    int updated = (current == null ? 0 : current) + quantityToAdd;
    requireWithinLineLimit(updated);
    items.put(productId, updated);
    LOGGER.debug("addOrIncrementItem id={} productId={} -> quantity={}", id, productId, updated);
  }

  /**
   * Sets a line's quantity to an exact value (rule 5, {@code PATCH}): fixes, does not add.
   *
   * @param productId the product to update
   * @param quantity the exact quantity to set, always positive
   * @throws CartItemNotFoundException {@code productId} has no line in this cart
   * @throws CartItemLimitExceededException {@code quantity} exceeds {@link #MAX_QUANTITY_PER_LINE}
   */
  public void setItemQuantity(ProductId productId, int quantity) {
    LOGGER.debug("setItemQuantity id={} productId={} quantity={}", id, productId, quantity);
    requireLinePresent(productId);
    requireWithinLineLimit(quantity);
    items.put(productId, quantity);
    LOGGER.debug("setItemQuantity id={} productId={} -> set", id, productId);
  }

  /**
   * Removes a line entirely.
   *
   * @param productId the product to remove
   * @throws CartItemNotFoundException {@code productId} has no line in this cart
   */
  public void removeItem(ProductId productId) {
    LOGGER.debug("removeItem id={} productId={}", id, productId);
    requireLinePresent(productId);
    items.remove(productId);
    LOGGER.debug("removeItem id={} productId={} -> removed", id, productId);
  }

  /**
   * Removes every line, without deleting the cart itself ({@code DELETE /cart}, rule per section
   * 4: it empties, never deletes).
   */
  public void clear() {
    LOGGER.debug("clear id={} lines={}", id, items.size());
    items.clear();
    LOGGER.debug("clear id={} -> emptied", id);
  }

  /**
   * Removes a specific subset of lines (rule 3.4: the automatic removal of no-longer-{@code
   * ACTIVE} products during validation).
   *
   * @param productIds the products whose lines to remove
   */
  public void removeItems(Set<ProductId> productIds) {
    LOGGER.debug("removeItems id={} count={}", id, productIds.size());
    items.keySet().removeAll(productIds);
    LOGGER.debug("removeItems id={} -> {} lines left", id, items.size());
  }

  /**
   * Adds a product or sums to its existing line, capping (never rejecting) at {@link
   * #MAX_QUANTITY_PER_LINE} (rule 3.2: a login merge sums quantities "con el máximo por línea como
   * tope" — silently, since there is no request to reject). The defensive {@link #MAX_LINES} cap
   * is not enforced here: rule 3.2 is silent on what happens if a merge would exceed it, and
   * dropping a product the customer already had during their own login would be surprising
   * (documented design decision, cart.md ambiguity).
   *
   * @param productId the product to add or increment
   * @param quantityToAdd the quantity to add, always positive
   */
  public void mergeItem(ProductId productId, int quantityToAdd) {
    LOGGER.debug("mergeItem id={} productId={} quantityToAdd={}", id, productId, quantityToAdd);
    Integer current = items.get(productId);
    int updated = Math.min((current == null ? 0 : current) + quantityToAdd, MAX_QUANTITY_PER_LINE);
    items.put(productId, updated);
    LOGGER.debug("mergeItem id={} productId={} -> quantity={}", id, productId, updated);
  }

  /**
   * Reassigns this cart to a customer (rule 3.2: a guest cart with no prior customer cart simply
   * becomes theirs).
   *
   * @param customerId the customer this cart now belongs to
   */
  public void assignToCustomer(UUID customerId) {
    LOGGER.debug("assignToCustomer id={} customerId={}", id, customerId);
    this.customerId = customerId;
  }

  /**
   * Renews {@link #expiresAt} from now, per the owner's TTL (rule 3.7): every write renews it, a
   * customer cart's renewal is effectively "no expiry" since it always happens before the far-off
   * date is ever reached.
   */
  public void renewExpiry() {
    Instant now = Instant.now();
    this.expiresAt = now.plus(customerId != null ? CUSTOMER_TTL : GUEST_TTL);
    LOGGER.debug("renewExpiry id={} -> expiresAt={}", id, expiresAt);
  }

  /**
   * @return whether this cart is past its expiry — treated as if it did not exist (rule 3.7)
   */
  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  /**
   * @param productId the product to require
   * @throws CartItemNotFoundException {@code productId} has no line in this cart
   */
  private void requireLinePresent(ProductId productId) {
    if (!items.containsKey(productId)) {
      throw new CartItemNotFoundException("Cart " + id + " has no line for product " + productId);
    }
  }

  /**
   * @param quantity the candidate quantity
   * @throws CartItemLimitExceededException {@code quantity} exceeds {@link #MAX_QUANTITY_PER_LINE}
   */
  private void requireWithinLineLimit(int quantity) {
    if (quantity > MAX_QUANTITY_PER_LINE) {
      throw new CartItemLimitExceededException(
          "Quantity " + quantity + " exceeds the maximum of " + MAX_QUANTITY_PER_LINE + " per line");
    }
  }

  /**
   * @return the application-generated identifier
   */
  public CartId id() {
    return id;
  }

  /**
   * @return the owning customer, or {@code null} for a guest cart
   */
  public UUID customerId() {
    return customerId;
  }

  /**
   * @return the token identifying this cart while there is no session
   */
  public String sessionToken() {
    return sessionToken;
  }

  /**
   * @return when this cart expires
   */
  public Instant expiresAt() {
    return expiresAt;
  }

  /**
   * @return the lines of this cart, keyed by product, unmodifiable
   */
  public Map<ProductId, Integer> items() {
    return Collections.unmodifiableMap(items);
  }

  /**
   * @return when the row was created, or {@code null} before the first save
   */
  public Instant createdAt() {
    return createdAt;
  }

  /**
   * @return when the row was last updated, or {@code null} before the first save
   */
  public Instant updatedAt() {
    return updatedAt;
  }
}
