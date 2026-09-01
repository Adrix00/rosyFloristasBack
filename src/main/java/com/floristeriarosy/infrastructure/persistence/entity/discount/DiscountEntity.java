package com.floristeriarosy.infrastructure.persistence.entity.discount;

import com.floristeriarosy.domain.model.discount.Discount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA mapping of the {@code product_discounts} table. Carries no {@code @Version} (ADR-009): this
 * table's own concurrency story is the two conditional {@code UPDATE}s behind {@code
 * DiscountReservationPort} plus its database {@code CHECK}s, not optimistic locking.
 */
@Entity
@Table(name = "product_discounts")
public class DiscountEntity {

  private static final Logger LOGGER = LoggerFactory.getLogger(DiscountEntity.class);

  @Id private UUID id;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "original_price", nullable = false)
  private BigDecimal originalPrice;

  @Column(name = "sale_price", nullable = false)
  private BigDecimal salePrice;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "quantity_limit")
  private Integer quantityLimit;

  @Column(name = "quantity_sold", nullable = false)
  private int quantitySold;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA; not for application use. */
  protected DiscountEntity() {}

  /**
   * @param id the primary key
   * @param productId the product this discount applies to
   * @param originalPrice the product's price at the moment of creation, frozen
   * @param salePrice the promotional price
   * @param startsAt when this discount becomes active
   * @param endsAt when this discount stops being active
   * @param quantityLimit the maximum number of promotional units, or {@code null} for no limit
   * @param quantitySold units already sold under this discount
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted discount
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted
   *     discount
   */
  public DiscountEntity(
      UUID id,
      UUID productId,
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
    this.salePrice = salePrice;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.quantityLimit = quantityLimit;
    this.quantitySold = quantitySold;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Sets {@code createdAt}/{@code updatedAt} in application code, since {@code V1} only gives
   * {@code created_at} a DB-side {@code DEFAULT now()} and nothing updates {@code updated_at} on
   * its own.
   */
  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
    LOGGER.debug("onCreate id={} createdAt={}", id, createdAt);
  }

  /** Refreshes {@code updatedAt} on every update; see {@link #onCreate()}. */
  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
    LOGGER.debug("onUpdate id={} updatedAt={}", id, updatedAt);
  }

  /**
   * Copies the fields {@link Discount#update} owns onto this managed instance. Never touches
   * {@code quantitySold}: that is written only through {@code DiscountReservationPort}'s own
   * conditional updates (product-discounts.md, section 3.5/3.6), and {@code originalPrice} is
   * frozen for the lifetime of the row.
   *
   * @param domain the domain discount carrying the new field values
   */
  public void applyChanges(Discount domain) {
    this.startsAt = domain.startsAt();
    this.endsAt = domain.endsAt();
    this.quantityLimit = domain.quantityLimit();
    this.salePrice = domain.salePrice();
  }

  /**
   * Sets {@code endsAt} on this managed instance, for {@code DiscountWritePort#endNow}
   * (product-discounts.md, section 3.4).
   *
   * @param now the instant to close this discount at
   */
  public void endNow(Instant now) {
    this.endsAt = now;
  }

  /**
   * @return the primary key
   */
  public UUID getId() {
    return id;
  }

  /**
   * @return the product this discount applies to
   */
  public UUID getProductId() {
    return productId;
  }

  /**
   * @return the product's price at the moment of creation, frozen
   */
  public BigDecimal getOriginalPrice() {
    return originalPrice;
  }

  /**
   * @return the promotional price
   */
  public BigDecimal getSalePrice() {
    return salePrice;
  }

  /**
   * @return when this discount becomes active
   */
  public Instant getStartsAt() {
    return startsAt;
  }

  /**
   * @return when this discount stops being active
   */
  public Instant getEndsAt() {
    return endsAt;
  }

  /**
   * @return the maximum number of promotional units, or {@code null} for no limit
   */
  public Integer getQuantityLimit() {
    return quantityLimit;
  }

  /**
   * @return units already sold under this discount
   */
  public int getQuantitySold() {
    return quantitySold;
  }

  /**
   * @return when the row was created
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * @return when the row was last updated
   */
  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
