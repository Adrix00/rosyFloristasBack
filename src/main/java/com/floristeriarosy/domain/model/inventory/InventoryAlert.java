package com.floristeriarosy.domain.model.inventory;

import com.floristeriarosy.domain.exception.inventory.InventoryAlertNotOpenException;
import com.floristeriarosy.domain.model.inventory.valueobject.InventoryAlertId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root of the inventory alerting module (inventory.md, section 3.8; ADR-013). Generic on
 * purpose across its two causes ({@code LOW_STOCK}, {@code RECONCILIATION_MISMATCH}): same
 * lifecycle, same admin actions, different {@code observedValue}/{@code expectedValue} meaning per
 * {@code type}.
 */
public final class InventoryAlert {

  private static final Logger LOGGER = LoggerFactory.getLogger(InventoryAlert.class);

  private final InventoryAlertId id;
  private final InventoryAlertType type;
  private final ProductId productId;
  private final int observedValue;
  private final int expectedValue;
  private InventoryAlertStatus status;
  private UUID resolvedByAdminId;
  private Instant resolvedAt;
  private String note;
  private final Instant createdAt;

  private InventoryAlert(
      InventoryAlertId id,
      InventoryAlertType type,
      ProductId productId,
      int observedValue,
      int expectedValue,
      InventoryAlertStatus status,
      UUID resolvedByAdminId,
      Instant resolvedAt,
      String note,
      Instant createdAt) {
    this.id = id;
    this.type = type;
    this.productId = productId;
    this.observedValue = observedValue;
    this.expectedValue = expectedValue;
    this.status = status;
    this.resolvedByAdminId = resolvedByAdminId;
    this.resolvedAt = resolvedAt;
    this.note = note;
    this.createdAt = createdAt;
  }

  /**
   * A newly-detected alert, born {@code OPEN} (inventory.md, section 3.8: the daily job only ever
   * creates {@code OPEN} alerts, never a resolved or dismissed one).
   *
   * @param id application-generated identifier
   * @param type which condition was detected
   * @param productId the product it was detected on
   * @param observedValue the observed number ({@code LOW_STOCK}: current stock; {@code
   *     RECONCILIATION_MISMATCH}: {@code products.stock})
   * @param expectedValue the number it was compared against ({@code LOW_STOCK}: the threshold;
   *     {@code RECONCILIATION_MISMATCH}: the sum of movements)
   * @return the new, not-yet-persisted alert
   */
  public static InventoryAlert open(
      InventoryAlertId id, InventoryAlertType type, ProductId productId, int observedValue, int expectedValue) {
    LOGGER.debug(
        "open id={} type={} productId={} observedValue={} expectedValue={}",
        id,
        type,
        productId,
        observedValue,
        expectedValue);
    InventoryAlert result =
        new InventoryAlert(
            id, type, productId, observedValue, expectedValue, InventoryAlertStatus.OPEN, null, null, null, null);
    LOGGER.debug("open id={} -> created", id);
    return result;
  }

  /**
   * Rebuilds an alert from persisted state. Used only by the persistence mapper — not logged, it
   * runs once per row loaded from the database.
   *
   * @param id the persisted identifier
   * @param type the persisted alert type
   * @param productId the persisted product reference
   * @param observedValue the persisted observed number
   * @param expectedValue the persisted expected number
   * @param status the persisted lifecycle state
   * @param resolvedByAdminId the persisted admin who closed it, or {@code null}
   * @param resolvedAt when it was closed, or {@code null}
   * @param note the persisted closing note, or {@code null}
   * @param createdAt when the row was created
   * @return the rebuilt alert
   */
  public static InventoryAlert reconstitute(
      InventoryAlertId id,
      InventoryAlertType type,
      ProductId productId,
      int observedValue,
      int expectedValue,
      InventoryAlertStatus status,
      UUID resolvedByAdminId,
      Instant resolvedAt,
      String note,
      Instant createdAt) {
    return new InventoryAlert(
        id, type, productId, observedValue, expectedValue, status, resolvedByAdminId, resolvedAt, note, createdAt);
  }

  /**
   * Closes the alert as fixed (inventory.md, section 3.8: "Resolver"). Only a domain-level
   * pre-check — the real guarantee is {@code chk_inventory_alerts_resolved_consistency}, the same
   * layering {@link com.floristeriarosy.domain.model.discount.Discount} uses for its own database
   * constraints.
   *
   * @param adminUserId who resolved it, or {@code null} — no {@code auth}/{@code admin} module
   *     exists yet to resolve a principal (known gap, see {@code ResolveInventoryAlertService})
   * @param note optional closing note
   * @param now the instant this alert is closed at
   * @throws InventoryAlertNotOpenException this alert is already {@code RESOLVED} or {@code DISMISSED}
   */
  public void resolve(UUID adminUserId, String note, Instant now) {
    LOGGER.debug("resolve id={}", id);
    requireOpen();
    this.status = InventoryAlertStatus.RESOLVED;
    this.resolvedByAdminId = adminUserId;
    this.resolvedAt = now;
    this.note = note;
    LOGGER.debug("resolve id={} -> resolved", id);
  }

  /**
   * Closes the alert as acknowledged, no action needed (inventory.md, section 3.8: "Descartar").
   *
   * @param adminUserId who dismissed it, or {@code null} (known gap, see {@code
   *     DismissInventoryAlertService})
   * @param note optional closing note
   * @param now the instant this alert is closed at
   * @throws InventoryAlertNotOpenException this alert is already {@code RESOLVED} or {@code DISMISSED}
   */
  public void dismiss(UUID adminUserId, String note, Instant now) {
    LOGGER.debug("dismiss id={}", id);
    requireOpen();
    this.status = InventoryAlertStatus.DISMISSED;
    this.resolvedByAdminId = adminUserId;
    this.resolvedAt = now;
    this.note = note;
    LOGGER.debug("dismiss id={} -> dismissed", id);
  }

  /**
   * @throws InventoryAlertNotOpenException {@link #status} is not {@code OPEN}
   */
  private void requireOpen() {
    if (status != InventoryAlertStatus.OPEN) {
      throw new InventoryAlertNotOpenException("Inventory alert " + id + " is not open (status=" + status + ")");
    }
  }

  /**
   * @return the application-generated identifier
   */
  public InventoryAlertId id() {
    return id;
  }

  /**
   * @return which condition was detected
   */
  public InventoryAlertType type() {
    return type;
  }

  /**
   * @return the product it was detected on
   */
  public ProductId productId() {
    return productId;
  }

  /**
   * @return the observed number
   */
  public int observedValue() {
    return observedValue;
  }

  /**
   * @return the number it was compared against
   */
  public int expectedValue() {
    return expectedValue;
  }

  /**
   * @return the current lifecycle state
   */
  public InventoryAlertStatus status() {
    return status;
  }

  /**
   * @return the admin who closed it, or {@code null} if still {@code OPEN} or system-generated
   */
  public UUID resolvedByAdminId() {
    return resolvedByAdminId;
  }

  /**
   * @return when it was closed, or {@code null} if still {@code OPEN}
   */
  public Instant resolvedAt() {
    return resolvedAt;
  }

  /**
   * @return the optional closing note, or {@code null}
   */
  public String note() {
    return note;
  }

  /**
   * @return when the row was created
   */
  public Instant createdAt() {
    return createdAt;
  }
}
