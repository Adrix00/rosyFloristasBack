package com.floristeriarosy.infrastructure.persistence.entity.product;

import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.ProductStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA mapping of the {@code products} table. Carries {@code @Version} (ADR-009): several admins
 * may edit the same product concurrently. {@code search_vector} is not mapped — PostgreSQL derives
 * it from {@code search_text} as a {@code STORED} generated column (ADR-006); this entity only
 * writes the text it derives from.
 */
@Entity
@Table(name = "products")
public class ProductEntity {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductEntity.class);

  @Id private UUID id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, length = 220)
  private String slug;

  @Column private String description;

  @Column(nullable = false)
  private BigDecimal price;

  @Column private Integer stock;

  @Column(name = "low_stock_threshold")
  private Integer lowStockThreshold;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProductStatus status;

  @Column(name = "is_extra", nullable = false)
  private boolean isExtra;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes;

  @Column(name = "search_text")
  private String searchText;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA; not for application use. */
  protected ProductEntity() {}

  /**
   * @param id the primary key
   * @param name the product name
   * @param slug the product slug
   * @param description optional description
   * @param price the base price
   * @param stock the current stock, or {@code null} if inventory is unmanaged
   * @param lowStockThreshold the low-stock alert threshold, or {@code null}
   * @param status {@code ACTIVE}, {@code INACTIVE} or {@code DISCONTINUED}
   * @param isExtra whether this product may be offered as a suggested extra
   * @param attributes the attribute values
   * @param searchText the normalized text indexed for search (ADR-006)
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted product
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted
   *     product
   */
  public ProductEntity(
      UUID id,
      String name,
      String slug,
      String description,
      BigDecimal price,
      Integer stock,
      Integer lowStockThreshold,
      ProductStatus status,
      boolean isExtra,
      Map<String, Object> attributes,
      String searchText,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.name = name;
    this.slug = slug;
    this.description = description;
    this.price = price;
    this.stock = stock;
    this.lowStockThreshold = lowStockThreshold;
    this.status = status;
    this.isExtra = isExtra;
    this.attributes = new LinkedHashMap<>(attributes);
    this.searchText = searchText;
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
   * Copies the fields {@link Product#replace} owns onto this managed instance, so the adapter's
   * save updates the row Hibernate already loaded instead of building a detached one with a stale
   * {@code @Version} (ADR-009) — a fresh, unloaded instance would always carry {@code version = 0}
   * and be mistaken for a new row. Never touches {@code stock} or {@code lowStockThreshold}:
   * inventory is written through its own port (product.md, section 3.7).
   *
   * @param domain the domain product carrying the new field values
   * @param searchText the normalized text to index for search (ADR-006)
   */
  public void applyChanges(Product domain, String searchText) {
    this.name = domain.name();
    this.slug = domain.slug().value();
    this.description = domain.description();
    this.price = domain.price();
    this.isExtra = domain.isExtra();
    this.attributes = domain.attributes();
    this.searchText = searchText;
  }

  /**
   * Sets {@code status} on this managed instance, for the same reason {@link #applyChanges}
   * mutates in place rather than replacing the entity.
   *
   * @param status the new status
   */
  public void changeStatus(ProductStatus status) {
    this.status = status;
  }

  /**
   * @return the primary key
   */
  public UUID getId() {
    return id;
  }

  /**
   * @return the product name
   */
  public String getName() {
    return name;
  }

  /**
   * @return the product slug
   */
  public String getSlug() {
    return slug;
  }

  /**
   * @return the description, or {@code null}
   */
  public String getDescription() {
    return description;
  }

  /**
   * @return the base price
   */
  public BigDecimal getPrice() {
    return price;
  }

  /**
   * @return the current stock, or {@code null} if inventory is unmanaged
   */
  public Integer getStock() {
    return stock;
  }

  /**
   * @return the low-stock alert threshold, or {@code null}
   */
  public Integer getLowStockThreshold() {
    return lowStockThreshold;
  }

  /**
   * @return {@code ACTIVE}, {@code INACTIVE} or {@code DISCONTINUED}
   */
  public ProductStatus getStatus() {
    return status;
  }

  /**
   * @return whether this product may be offered as a suggested extra
   */
  public boolean isExtra() {
    return isExtra;
  }

  /**
   * @return the attribute values
   */
  public Map<String, Object> getAttributes() {
    return Collections.unmodifiableMap(attributes);
  }

  /**
   * @return the normalized text indexed for search (ADR-006)
   */
  public String getSearchText() {
    return searchText;
  }

  /**
   * @return the optimistic-locking version (ADR-009)
   */
  public long getVersion() {
    return version;
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
