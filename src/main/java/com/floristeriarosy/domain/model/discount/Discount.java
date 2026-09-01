package com.floristeriarosy.domain.model.discount;

import com.floristeriarosy.domain.exception.discount.DiscountAlreadyStartedException;
import com.floristeriarosy.domain.exception.discount.DiscountLimitBelowSoldException;
import com.floristeriarosy.domain.exception.discount.DiscountNotEditableException;
import com.floristeriarosy.domain.exception.discount.DiscountPeriodInvalidException;
import com.floristeriarosy.domain.exception.discount.DiscountPriceNotLowerException;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root of the product-discounts module (product-discounts.md). A discount is a
 * promotional price with a vigency window and an optional unit cap; {@code quantitySold} is
 * written only by {@code DiscountReservationPort}'s own conditional updates (product-discounts.md,
 * section 3.5/3.6 — the same reasoning ADR-009 applies to {@code products.stock}), never through a
 * full aggregate save.
 */
public final class Discount {

  private static final Logger LOGGER = LoggerFactory.getLogger(Discount.class);

  private final DiscountId id;
  private final ProductId productId;
  private final BigDecimal originalPrice;
  private BigDecimal salePrice;
  private Instant startsAt;
  private Instant endsAt;
  private Integer quantityLimit;
  private final int quantitySold;
  private final Instant createdAt;
  private Instant updatedAt;

  private Discount(
      DiscountId id,
      ProductId productId,
      BigDecimal originalPrice,
      BigDecimal salePrice,
      Instant startsAt,
      Instant endsAt,
      Integer quantityLimit,
      int quantitySold,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.productId = productId;
    this.originalPrice = originalPrice;
    this.salePrice = requireLowerThanOriginal(salePrice, originalPrice);
    this.startsAt = startsAt;
    this.endsAt = requireAfter(endsAt, startsAt);
    this.quantityLimit = requirePositiveOrNull(quantityLimit);
    this.quantitySold = requireWithinLimit(quantitySold, quantityLimit);
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * New discount, born with no units sold. The caller (service layer) is responsible for the
   * cross-aggregate business validations in product-discounts.md, section 5 — {@code endsAt} in
   * the future and {@code salePrice} lower than the product's current price — since those need
   * data this aggregate does not hold on its own; the checks below are only the structural
   * invariants every {@code Discount} must satisfy regardless of caller.
   *
   * @param id application-generated identifier
   * @param productId the product this discount applies to
   * @param originalPrice the product's price at the moment of creation, frozen (product-discounts.md, section 2)
   * @param salePrice the promotional price
   * @param startsAt when the discount becomes active
   * @param endsAt when the discount stops being active
   * @param quantityLimit the maximum number of promotional units, or {@code null} for no limit
   * @return the new, not-yet-persisted discount
   */
  public static Discount create(
      DiscountId id,
      ProductId productId,
      BigDecimal originalPrice,
      BigDecimal salePrice,
      Instant startsAt,
      Instant endsAt,
      Integer quantityLimit) {
    LOGGER.debug(
        "create id={} productId={} originalPrice={} salePrice={} startsAt={} endsAt={} quantityLimit={}",
        id,
        productId,
        originalPrice,
        salePrice,
        startsAt,
        endsAt,
        quantityLimit);
    Discount result =
        new Discount(id, productId, originalPrice, salePrice, startsAt, endsAt, quantityLimit, 0, null, null);
    LOGGER.debug("create id={} -> created", id);
    return result;
  }

  /**
   * Rebuilds a discount from persisted state. Used only by the persistence mapper — not logged, it
   * runs once per row loaded from the database.
   *
   * @param id the persisted identifier
   * @param productId the persisted product reference
   * @param originalPrice the persisted frozen "before" price
   * @param salePrice the persisted promotional price
   * @param startsAt the persisted vigency start
   * @param endsAt the persisted vigency end
   * @param quantityLimit the persisted unit cap, or {@code null}
   * @param quantitySold the persisted units already sold under this discount
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt discount
   */
  public static Discount reconstitute(
      DiscountId id,
      ProductId productId,
      BigDecimal originalPrice,
      BigDecimal salePrice,
      Instant startsAt,
      Instant endsAt,
      Integer quantityLimit,
      int quantitySold,
      Instant createdAt,
      Instant updatedAt) {
    return new Discount(
        id, productId, originalPrice, salePrice, startsAt, endsAt, quantityLimit, quantitySold, createdAt, updatedAt);
  }

  /**
   * Applies a partial edit, per the editability table in product-discounts.md, section 3.3. A
   * {@code null} argument means "leave this field unchanged"; a non-{@code null} argument equal to
   * the current value is a no-op even in a state that would otherwise forbid editing it. A
   * non-{@code null} argument that differs from the current value, in a state that forbids editing
   * that field, is rejected — never silently ignored.
   *
   * @param newStartsAt the requested new vigency start, or {@code null} to leave it unchanged
   * @param newEndsAt the requested new vigency end, or {@code null} to leave it unchanged
   * @param newQuantityLimit the requested new unit cap, or {@code null} to leave it unchanged
   * @param newSalePrice the requested new promotional price, or {@code null} to leave it unchanged
   * @throws DiscountNotEditableException a changed field is not editable in the current state
   * @throws DiscountPeriodInvalidException the new {@code endsAt} is not in the future, or the
   *     resulting {@code startsAt}/{@code endsAt} pair would leave {@code startsAt} not strictly
   *     before {@code endsAt}
   * @throws DiscountLimitBelowSoldException the new {@code quantityLimit} is below {@code quantitySold}
   * @throws DiscountPriceNotLowerException the new {@code salePrice} is not lower than {@code originalPrice}
   */
  public void update(Instant newStartsAt, Instant newEndsAt, Integer newQuantityLimit, BigDecimal newSalePrice) {
    LOGGER.debug(
        "update id={} startsAt={} endsAt={} quantityLimit={} salePrice={}",
        id,
        newStartsAt,
        newEndsAt,
        newQuantityLimit,
        newSalePrice);
    Instant now = Instant.now();
    boolean notStarted = startsAt.isAfter(now);
    boolean ended = !notStarted && !endsAt.isAfter(now);
    boolean hasSales = quantitySold > 0;

    applyStartsAt(newStartsAt, notStarted);
    applyEndsAt(newEndsAt, ended, now);
    applyQuantityLimit(newQuantityLimit, ended);
    applySalePrice(newSalePrice, ended || hasSales);
    // applyStartsAt and applyEndsAt each validate their own field in isolation (against "now"
    // and against editability), never against each other — moving startsAt alone can still push
    // it past an unchanged endsAt. Catch that combination here rather than leaving it for
    // chk_product_discounts_period to reject at save time, several layers away from this method.
    if (!startsAt.isBefore(endsAt)) {
      throw new DiscountPeriodInvalidException("endsAt must be after startsAt for discount " + id);
    }
    LOGGER.debug("update id={} -> updated", id);
  }

  /**
   * Closes the discount now: sets {@code endsAt} to the current instant instead of physically
   * deleting the row, so its history and any {@code order_items.discount_id} references survive
   * (product-discounts.md, section 3.4). Only a currently-active discount can be ended this way — a
   * not-yet-started one has no history to preserve and is deleted instead (see {@link
   * #requireNotStarted()}), and an already-ended one has nothing left to close.
   *
   * @throws DiscountPeriodInvalidException this discount has not started yet, or has already ended
   */
  public void end() {
    LOGGER.debug("end id={}", id);
    Instant now = Instant.now();
    if (startsAt.isAfter(now)) {
      throw new DiscountPeriodInvalidException(
          "Discount " + id + " has not started yet; delete it instead of ending it");
    }
    if (!endsAt.isAfter(now)) {
      throw new DiscountPeriodInvalidException("Discount " + id + " has already ended");
    }
    this.endsAt = now;
    LOGGER.debug("end id={} -> endsAt={}", id, endsAt);
  }

  /**
   * Guards the physical {@code DELETE} path: only a discount that has not started yet may be
   * removed (product-discounts.md, section 3.4). One that has already started must be closed via
   * {@link #end()} instead, to preserve its history.
   *
   * @throws DiscountAlreadyStartedException this discount has already started
   */
  public void requireNotStarted() {
    if (!startsAt.isAfter(Instant.now())) {
      throw new DiscountAlreadyStartedException(
          "Discount " + id + " has already started; end it instead of deleting it");
    }
  }

  /**
   * Computes the derived lifecycle state (product-discounts.md, section 6), never persisted.
   *
   * @return the discount's current state
   */
  public DiscountState state() {
    LOGGER.debug("state id={}", id);
    Instant now = Instant.now();
    DiscountState result;
    if (startsAt.isAfter(now)) {
      result = DiscountState.SCHEDULED;
    } else if (!endsAt.isAfter(now)) {
      result = DiscountState.ENDED;
    } else if (quantityLimit != null && quantitySold >= quantityLimit) {
      result = DiscountState.SOLD_OUT;
    } else {
      result = DiscountState.ACTIVE;
    }
    LOGGER.debug("state id={} -> {}", id, result);
    return result;
  }

  /**
   * @param newValue the requested new value, or {@code null} to leave it unchanged
   * @param editable whether {@code startsAt} may be changed in the current state
   * @throws DiscountNotEditableException {@code newValue} differs from the current value and
   *     {@code editable} is {@code false}
   */
  private void applyStartsAt(Instant newValue, boolean editable) {
    if (newValue == null || newValue.equals(startsAt)) {
      return;
    }
    if (!editable) {
      throw new DiscountNotEditableException(
          "startsAt is not editable once discount " + id + " has started");
    }
    this.startsAt = newValue;
  }

  /**
   * @param newValue the requested new value, or {@code null} to leave it unchanged
   * @param ended whether this discount has already ended
   * @param now the instant this edit is being applied at
   * @throws DiscountNotEditableException {@code newValue} differs from the current value and this
   *     discount has already ended
   * @throws DiscountPeriodInvalidException {@code newValue} is not in the future
   */
  private void applyEndsAt(Instant newValue, boolean ended, Instant now) {
    if (newValue == null || newValue.equals(endsAt)) {
      return;
    }
    if (ended) {
      throw new DiscountNotEditableException("endsAt is not editable once discount " + id + " has ended");
    }
    if (!newValue.isAfter(now)) {
      throw new DiscountPeriodInvalidException(
          "endsAt must be in the future; use POST /discounts/{id}/end to close discount " + id + " now");
    }
    this.endsAt = newValue;
  }

  /**
   * @param newValue the requested new value, or {@code null} to leave it unchanged
   * @param ended whether this discount has already ended
   * @throws DiscountNotEditableException {@code newValue} differs from the current value and this
   *     discount has already ended
   * @throws DiscountLimitBelowSoldException {@code newValue} is lower than {@code quantitySold}
   */
  private void applyQuantityLimit(Integer newValue, boolean ended) {
    if (newValue == null || newValue.equals(quantityLimit)) {
      return;
    }
    if (ended) {
      throw new DiscountNotEditableException(
          "quantityLimit is not editable once discount " + id + " has ended");
    }
    if (newValue < quantitySold) {
      throw new DiscountLimitBelowSoldException(
          "quantityLimit cannot drop below quantitySold=" + quantitySold + " for discount " + id);
    }
    this.quantityLimit = newValue;
  }

  /**
   * @param newValue the requested new value, or {@code null} to leave it unchanged
   * @param notEditable whether {@code salePrice} is not editable in the current state (already
   *     ended, or active with sales)
   * @throws DiscountNotEditableException {@code newValue} differs from the current value and {@code
   *     notEditable} is {@code true}
   * @throws DiscountPriceNotLowerException {@code newValue} is not lower than {@code originalPrice}
   */
  private void applySalePrice(BigDecimal newValue, boolean notEditable) {
    if (newValue == null || newValue.compareTo(salePrice) == 0) {
      return;
    }
    if (notEditable) {
      throw new DiscountNotEditableException(
          "salePrice is not editable once discount " + id + " has sales or has ended");
    }
    if (newValue.compareTo(originalPrice) >= 0) {
      throw new DiscountPriceNotLowerException(
          "salePrice must be lower than originalPrice=" + originalPrice + " for discount " + id);
    }
    this.salePrice = newValue;
  }

  /**
   * @param salePrice candidate sale price
   * @param originalPrice the price it is compared against
   * @return {@code salePrice}, unchanged
   * @throws IllegalArgumentException {@code salePrice} is {@code null}, negative, or not strictly
   *     lower than {@code originalPrice}
   */
  private static BigDecimal requireLowerThanOriginal(BigDecimal salePrice, BigDecimal originalPrice) {
    boolean invalid =
        salePrice == null
            || salePrice.signum() < 0
            || originalPrice == null
            || salePrice.compareTo(originalPrice) >= 0;
    if (invalid) {
      throw new IllegalArgumentException("salePrice must be non-negative and lower than originalPrice");
    }
    return salePrice;
  }

  /**
   * @param endsAt candidate vigency end
   * @param startsAt the vigency start it is compared against
   * @return {@code endsAt}, unchanged
   * @throws IllegalArgumentException {@code endsAt} is {@code null} or not strictly after {@code
   *     startsAt}
   */
  private static Instant requireAfter(Instant endsAt, Instant startsAt) {
    if (endsAt == null || startsAt == null || !endsAt.isAfter(startsAt)) {
      throw new IllegalArgumentException("endsAt must be after startsAt");
    }
    return endsAt;
  }

  /**
   * @param quantityLimit candidate unit cap
   * @return {@code quantityLimit}, unchanged
   * @throws IllegalArgumentException {@code quantityLimit} is present and not positive
   */
  private static Integer requirePositiveOrNull(Integer quantityLimit) {
    if (quantityLimit != null && quantityLimit <= 0) {
      throw new IllegalArgumentException("quantityLimit must be positive when present");
    }
    return quantityLimit;
  }

  /**
   * @param quantitySold candidate units already sold
   * @param quantityLimit the unit cap it is compared against, or {@code null}
   * @return {@code quantitySold}, unchanged
   * @throws IllegalArgumentException {@code quantitySold} is negative, or exceeds {@code
   *     quantityLimit} when present
   */
  private static int requireWithinLimit(int quantitySold, Integer quantityLimit) {
    if (quantitySold < 0 || (quantityLimit != null && quantitySold > quantityLimit)) {
      throw new IllegalArgumentException("quantitySold must be non-negative and within quantityLimit");
    }
    return quantitySold;
  }

  /**
   * @return the application-generated identifier
   */
  public DiscountId id() {
    return id;
  }

  /**
   * @return the product this discount applies to
   */
  public ProductId productId() {
    return productId;
  }

  /**
   * @return the product's price at the moment of creation, frozen
   */
  public BigDecimal originalPrice() {
    return originalPrice;
  }

  /**
   * @return the promotional price
   */
  public BigDecimal salePrice() {
    return salePrice;
  }

  /**
   * @return when this discount becomes active
   */
  public Instant startsAt() {
    return startsAt;
  }

  /**
   * @return when this discount stops being active
   */
  public Instant endsAt() {
    return endsAt;
  }

  /**
   * @return the maximum number of promotional units, or {@code null} for no limit
   */
  public Integer quantityLimit() {
    return quantityLimit;
  }

  /**
   * @return units already sold under this discount
   */
  public int quantitySold() {
    return quantitySold;
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
