package com.floristeriarosy.domain.model.order;

import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import com.floristeriarosy.domain.model.order.valueobject.ShippingBandId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One delivery band of {@code shipping_rates}: the price for addresses from {@code minKm} up to (not
 * including) {@code maxKm}, and the subtotal at or above which shipping is free (order.md, §3.7).
 * Enforces the per-band invariants; whether a whole set of bands fits together is {@link
 * ShippingBands}.
 */
public final class ShippingBand {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShippingBand.class);

  private final ShippingBandId id;
  private BigDecimal minKm;
  private BigDecimal maxKm;
  private BigDecimal price;
  private BigDecimal freeFromAmount;
  private boolean active;
  private final Instant createdAt;
  private Instant updatedAt;

  private ShippingBand(
      ShippingBandId id,
      BigDecimal minKm,
      BigDecimal maxKm,
      BigDecimal price,
      BigDecimal freeFromAmount,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {
    requireValid(minKm, maxKm, price, freeFromAmount);
    this.id = id;
    this.minKm = minKm;
    this.maxKm = maxKm;
    this.price = price;
    this.freeFromAmount = freeFromAmount;
    this.active = active;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * @param id application-generated identifier
   * @param minKm where the band starts, in kilometres
   * @param maxKm where the band ends (exclusive), in kilometres
   * @param price the shipping cost inside the band
   * @param freeFromAmount the subtotal at or above which shipping is free, or {@code null}
   * @param active whether the band is offered to customers
   * @return the new, not-yet-persisted band
   * @throws ShippingRatesInvalidException a per-band invariant is broken
   */
  public static ShippingBand create(
      ShippingBandId id,
      BigDecimal minKm,
      BigDecimal maxKm,
      BigDecimal price,
      BigDecimal freeFromAmount,
      boolean active) {
    LOGGER.debug(
        "create id={} minKm={} maxKm={} price={} freeFromAmount={} active={}",
        id,
        minKm,
        maxKm,
        price,
        freeFromAmount,
        active);
    ShippingBand band = new ShippingBand(id, minKm, maxKm, price, freeFromAmount, active, null, null);
    LOGGER.debug("create id={} -> created", id);
    return band;
  }

  /**
   * Rebuilds a band from persisted state; used only by the persistence mapper, so not logged.
   *
   * @param id the persisted identifier
   * @param minKm the persisted start
   * @param maxKm the persisted end
   * @param price the persisted price
   * @param freeFromAmount the persisted free-shipping threshold, or {@code null}
   * @param active the persisted flag
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt band
   */
  public static ShippingBand reconstitute(
      ShippingBandId id,
      BigDecimal minKm,
      BigDecimal maxKm,
      BigDecimal price,
      BigDecimal freeFromAmount,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {
    return new ShippingBand(id, minKm, maxKm, price, freeFromAmount, active, createdAt, updatedAt);
  }

  /**
   * Replaces every editable field (the {@code PUT} semantics of order.md §4).
   *
   * @param newMinKm the new start
   * @param newMaxKm the new end
   * @param newPrice the new price
   * @param newFreeFromAmount the new free-shipping threshold, or {@code null}
   * @param newActive the new flag
   * @return the database column names of the fields whose value changed, for the audit log
   * @throws ShippingRatesInvalidException a per-band invariant is broken; nothing changed
   */
  public List<String> replaceWith(
      BigDecimal newMinKm,
      BigDecimal newMaxKm,
      BigDecimal newPrice,
      BigDecimal newFreeFromAmount,
      boolean newActive) {
    LOGGER.debug(
        "replaceWith id={} minKm={} maxKm={} price={} freeFromAmount={} active={}",
        id,
        newMinKm,
        newMaxKm,
        newPrice,
        newFreeFromAmount,
        newActive);
    requireValid(newMinKm, newMaxKm, newPrice, newFreeFromAmount);
    List<String> changed = new ArrayList<>();
    if (differs(minKm, newMinKm)) {
      changed.add("min_km");
    }
    if (differs(maxKm, newMaxKm)) {
      changed.add("max_km");
    }
    if (differs(price, newPrice)) {
      changed.add("price");
    }
    if (differs(freeFromAmount, newFreeFromAmount)) {
      changed.add("free_from_amount");
    }
    if (active != newActive) {
      changed.add("active");
    }
    this.minKm = newMinKm;
    this.maxKm = newMaxKm;
    this.price = newPrice;
    this.freeFromAmount = newFreeFromAmount;
    this.active = newActive;
    LOGGER.debug("replaceWith id={} -> changed={}", id, changed);
    return changed;
  }

  /**
   * @param a one value, possibly {@code null}
   * @param b another value, possibly {@code null}
   * @return whether they differ numerically (scale ignored)
   */
  private static boolean differs(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a != b;
    }
    return a.compareTo(b) != 0;
  }

  /**
   * @param minKm where the band starts
   * @param maxKm where the band ends
   * @param price the shipping cost
   * @param freeFromAmount the free-shipping threshold, or {@code null}
   * @throws ShippingRatesInvalidException {@code minKm} is negative, {@code maxKm} is not greater
   *     than {@code minKm}, {@code price} is negative, or {@code freeFromAmount} is not positive
   */
  private static void requireValid(
      BigDecimal minKm, BigDecimal maxKm, BigDecimal price, BigDecimal freeFromAmount) {
    if (minKm == null || maxKm == null || price == null) {
      throw new ShippingRatesInvalidException("minKm, maxKm and price are required");
    }
    if (minKm.signum() < 0) {
      throw new ShippingRatesInvalidException("minKm must not be negative");
    }
    if (maxKm.compareTo(minKm) <= 0) {
      throw new ShippingRatesInvalidException("maxKm must be greater than minKm");
    }
    if (price.signum() < 0) {
      throw new ShippingRatesInvalidException("price must not be negative");
    }
    if (freeFromAmount != null && freeFromAmount.signum() <= 0) {
      throw new ShippingRatesInvalidException("freeFromAmount must be positive when present");
    }
  }

  /** @return the band's identifier */
  public ShippingBandId id() {
    return id;
  }

  /** @return where the band starts, in kilometres */
  public BigDecimal minKm() {
    return minKm;
  }

  /** @return where the band ends (exclusive), in kilometres */
  public BigDecimal maxKm() {
    return maxKm;
  }

  /** @return the shipping cost inside the band */
  public BigDecimal price() {
    return price;
  }

  /** @return the subtotal at or above which shipping is free, or {@code null} */
  public BigDecimal freeFromAmount() {
    return freeFromAmount;
  }

  /** @return whether the band is offered to customers */
  public boolean active() {
    return active;
  }

  /** @return when the row was created, or {@code null} if not yet persisted */
  public Instant createdAt() {
    return createdAt;
  }

  /** @return when the row was last updated, or {@code null} if not yet persisted */
  public Instant updatedAt() {
    return updatedAt;
  }
}
