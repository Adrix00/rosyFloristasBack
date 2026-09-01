package com.floristeriarosy.domain.model.product;

import com.floristeriarosy.domain.exception.product.ProductDiscontinuedException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.shared.util.LogSanitizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root of the product module (product.md). {@code stock} and {@code lowStockThreshold}
 * are populated only by {@link #reconstitute}: inventory is written through {@code
 * ProductInventoryPort}'s own conditional updates, never through a full aggregate save (ADR-009 —
 * the same reasoning that keeps {@code stock}'s writes outside the {@code @Version}-guarded save
 * cycle). Categories, images and suggested extras are associations owned by their own ports, not
 * fields of this aggregate.
 */
public final class Product {

  private static final Logger LOGGER = LoggerFactory.getLogger(Product.class);

  private final ProductId id;
  private String name;
  private ProductSlug slug;
  private String description;
  private BigDecimal price;
  private final Integer stock;
  private final Integer lowStockThreshold;
  private ProductStatus status;
  private boolean isExtra;
  private Map<String, Object> attributes;
  private final Instant createdAt;
  private Instant updatedAt;

  private Product(
      ProductId id,
      String name,
      ProductSlug slug,
      String description,
      BigDecimal price,
      Integer stock,
      Integer lowStockThreshold,
      ProductStatus status,
      boolean isExtra,
      Map<String, Object> attributes,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.name = requireName(name);
    this.slug = slug;
    this.description = description;
    this.price = requirePositive(price);
    this.stock = stock;
    this.lowStockThreshold = lowStockThreshold;
    this.status = status;
    this.isExtra = isExtra;
    this.attributes = copyAttributes(attributes);
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * New product, born {@code ACTIVE} with no inventory management (product.md, section 5): {@code
   * stock} starts {@code null} regardless of any initial stock the caller requested — the caller
   * applies it afterward through {@code ProductInventoryPort}, in the same transaction.
   *
   * @param id application-generated identifier
   * @param name the product name
   * @param slug the slug generated from {@code name}
   * @param description optional description
   * @param price the base price
   * @param isExtra whether this product may be offered as a suggested extra
   * @param attributes already-validated attribute values (product.md, section 3.5)
   * @return the new, not-yet-persisted product
   */
  public static Product create(
      ProductId id,
      String name,
      ProductSlug slug,
      String description,
      BigDecimal price,
      boolean isExtra,
      Map<String, Object> attributes) {
    LOGGER.debug(
        "create id={} name={} slug={} price={} isExtra={} attributes={}",
        id,
        LogSanitizer.sanitize(name),
        slug,
        price,
        isExtra,
        LogSanitizer.sanitize(String.valueOf(attributes)));
    Product result =
        new Product(
            id,
            name,
            slug,
            description,
            price,
            null,
            null,
            ProductStatus.ACTIVE,
            isExtra,
            attributes,
            null,
            null);
    LOGGER.debug("create id={} -> created", id);
    return result;
  }

  /**
   * Rebuilds a product from persisted state. Used only by the persistence mapper — not logged, it
   * runs once per row loaded from the database.
   *
   * @param id the persisted identifier
   * @param name the persisted name
   * @param slug the persisted slug
   * @param description the persisted description
   * @param price the persisted base price
   * @param stock the persisted stock, or {@code null} if inventory is unmanaged
   * @param lowStockThreshold the persisted low-stock alert threshold, or {@code null}
   * @param status the persisted status
   * @param isExtra the persisted extra flag
   * @param attributes the persisted attribute values
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt product
   */
  public static Product reconstitute(
      ProductId id,
      String name,
      ProductSlug slug,
      String description,
      BigDecimal price,
      Integer stock,
      Integer lowStockThreshold,
      ProductStatus status,
      boolean isExtra,
      Map<String, Object> attributes,
      Instant createdAt,
      Instant updatedAt) {
    return new Product(
        id,
        name,
        slug,
        description,
        price,
        stock,
        lowStockThreshold,
        status,
        isExtra,
        attributes,
        createdAt,
        updatedAt);
  }

  /**
   * {@code PUT}: full replace of the fields this endpoint owns (product.md, section 5). Stock,
   * categories, images and suggested extras each have their own endpoint.
   *
   * @param name the new name
   * @param slug the slug regenerated from {@code name} (product.md, section 3.1: renaming a
   *     product regenerates its slug and breaks the previous link, same as category)
   * @param description the new description, or {@code null} to clear it
   * @param price the new base price
   * @param isExtra the new extra flag
   * @param attributes the new, already-validated attribute values
   * @throws ProductDiscontinuedException this product is {@code DISCONTINUED} (product.md, section
   *     10)
   */
  public void replace(
      String name,
      ProductSlug slug,
      String description,
      BigDecimal price,
      boolean isExtra,
      Map<String, Object> attributes) {
    LOGGER.debug(
        "replace id={} name={} slug={} price={} isExtra={} attributes={}",
        id,
        LogSanitizer.sanitize(name),
        slug,
        price,
        isExtra,
        LogSanitizer.sanitize(String.valueOf(attributes)));
    requireNotDiscontinued();
    this.name = requireName(name);
    this.slug = slug;
    this.description = description;
    this.price = requirePositive(price);
    this.isExtra = isExtra;
    this.attributes = copyAttributes(attributes);
    LOGGER.debug("replace id={} -> replaced", id);
  }

  /**
   * Sets the status. Idempotent: setting the same status twice is a no-op, not an error
   * (product.md, section 10).
   *
   * @param newStatus the status to set
   * @throws ProductDiscontinuedException this product is already {@code DISCONTINUED} and {@code
   *     newStatus} is different — that state is terminal (product.md, section 3.2)
   */
  public void changeStatus(ProductStatus newStatus) {
    LOGGER.debug("changeStatus id={} from={} to={}", id, status, newStatus);
    if (status == ProductStatus.DISCONTINUED && newStatus != ProductStatus.DISCONTINUED) {
      throw new ProductDiscontinuedException(
          "Product " + id + " is DISCONTINUED, which is terminal");
    }
    this.status = newStatus;
  }

  /**
   * @throws ProductDiscontinuedException this product is {@code DISCONTINUED}
   */
  private void requireNotDiscontinued() {
    if (status == ProductStatus.DISCONTINUED) {
      throw new ProductDiscontinuedException("Product " + id + " is DISCONTINUED");
    }
  }

  /**
   * Defensively copies {@code attributes} into an unmodifiable map (SpotBugs EI_EXPOSE_REP2). Uses
   * a plain {@link LinkedHashMap} rather than {@link Map#copyOf}, which rejects a {@code null}
   * value — JSONB may legitimately hold one.
   *
   * @param attributes the attribute values to copy
   * @return an unmodifiable copy, or an empty map if {@code attributes} is {@code null}
   */
  private static Map<String, Object> copyAttributes(Map<String, Object> attributes) {
    return attributes == null
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
  }

  /**
   * @param name candidate name
   * @return {@code name}, unchanged
   * @throws IllegalArgumentException {@code name} is {@code null} or blank
   */
  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    return name;
  }

  /**
   * @param price candidate price
   * @return {@code price}, unchanged
   * @throws IllegalArgumentException {@code price} is {@code null} or negative
   */
  private static BigDecimal requirePositive(BigDecimal price) {
    if (price == null || price.signum() < 0) {
      throw new IllegalArgumentException("price must not be null or negative");
    }
    return price;
  }

  /**
   * @return the application-generated identifier
   */
  public ProductId id() {
    return id;
  }

  /**
   * @return the product name
   */
  public String name() {
    return name;
  }

  /**
   * @return the slug generated from the name
   */
  public ProductSlug slug() {
    return slug;
  }

  /**
   * @return the description, or {@code null}
   */
  public String description() {
    return description;
  }

  /**
   * @return the base price
   */
  public BigDecimal price() {
    return price;
  }

  /**
   * @return the current stock, or {@code null} if inventory is unmanaged (product.md, section 3.7)
   */
  public Integer stock() {
    return stock;
  }

  /**
   * @return the low-stock alert threshold, or {@code null} if unset
   */
  public Integer lowStockThreshold() {
    return lowStockThreshold;
  }

  /**
   * @return {@code ACTIVE}, {@code INACTIVE} or {@code DISCONTINUED}
   */
  public ProductStatus status() {
    return status;
  }

  /**
   * @return whether this product may be offered as a suggested extra
   */
  public boolean isExtra() {
    return isExtra;
  }

  /**
   * @return the attribute values, keyed by declared attribute key
   */
  public Map<String, Object> attributes() {
    return Collections.unmodifiableMap(attributes);
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
