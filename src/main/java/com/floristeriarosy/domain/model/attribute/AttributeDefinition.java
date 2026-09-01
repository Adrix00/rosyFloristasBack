package com.floristeriarosy.domain.model.attribute;

import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import com.floristeriarosy.shared.util.LogSanitizer;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root declaring one filterable key a product's {@code attributes} JSONB may use
 * (product.md, section 3.5).
 */
public final class AttributeDefinition {

  private static final Logger LOGGER = LoggerFactory.getLogger(AttributeDefinition.class);

  private final AttributeDefinitionId id;
  private final String attributeKey;
  private String label;
  private final AttributeDataType dataType;
  private boolean filterable;
  private int position;
  private final Instant createdAt;
  private Instant updatedAt;

  private AttributeDefinition(
      AttributeDefinitionId id,
      String attributeKey,
      String label,
      AttributeDataType dataType,
      boolean filterable,
      int position,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.attributeKey = requireKey(attributeKey);
    this.label = requireLabel(label);
    this.dataType = dataType;
    this.filterable = filterable;
    this.position = position;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * New attribute definition. {@code attributeKey} and {@code dataType} are fixed for its lifetime
   * (product.md, section 3.5): every product that already wrote this key depends on both meaning
   * exactly what they meant when created.
   *
   * @param id application-generated identifier
   * @param attributeKey the declared key, immutable once created
   * @param label the visible label
   * @param dataType the declared value type, immutable once created
   * @param filterable whether {@code GET /products} may filter by this key
   * @param position position in the admin's attribute list
   * @return the new, not-yet-persisted attribute definition
   */
  public static AttributeDefinition create(
      AttributeDefinitionId id,
      String attributeKey,
      String label,
      AttributeDataType dataType,
      boolean filterable,
      int position) {
    LOGGER.debug(
        "create id={} attributeKey={} label={} dataType={} filterable={} position={}",
        id,
        LogSanitizer.sanitize(attributeKey),
        LogSanitizer.sanitize(label),
        dataType,
        filterable,
        position);
    AttributeDefinition result =
        new AttributeDefinition(
            id, attributeKey, label, dataType, filterable, position, null, null);
    LOGGER.debug("create id={} -> created", id);
    return result;
  }

  /**
   * Rebuilds an attribute definition from persisted state. Used only by the persistence mapper —
   * not logged, it runs once per row loaded from the database.
   *
   * @param id the persisted identifier
   * @param attributeKey the persisted key
   * @param label the persisted label
   * @param dataType the persisted value type
   * @param filterable the persisted filterable flag
   * @param position the persisted position
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt attribute definition
   */
  public static AttributeDefinition reconstitute(
      AttributeDefinitionId id,
      String attributeKey,
      String label,
      AttributeDataType dataType,
      boolean filterable,
      int position,
      Instant createdAt,
      Instant updatedAt) {
    return new AttributeDefinition(
        id, attributeKey, label, dataType, filterable, position, createdAt, updatedAt);
  }

  /**
   * Renames the visible label and updates the filter flag and position. {@code attributeKey} and
   * {@code dataType} cannot change (product.md, section 3.5).
   *
   * @param label the new label
   * @param filterable the new filterable flag
   * @param position the new position
   */
  public void relabel(String label, boolean filterable, int position) {
    LOGGER.debug(
        "relabel id={} label={} filterable={} position={}",
        id,
        LogSanitizer.sanitize(label),
        filterable,
        position);
    this.label = requireLabel(label);
    this.filterable = filterable;
    this.position = position;
    LOGGER.debug("relabel id={} -> relabeled", id);
  }

  /**
   * @param key candidate key
   * @return {@code key}, unchanged
   * @throws IllegalArgumentException {@code key} is {@code null} or blank
   */
  private static String requireKey(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("attributeKey must not be blank");
    }
    return key;
  }

  /**
   * @param label candidate label
   * @return {@code label}, unchanged
   * @throws IllegalArgumentException {@code label} is {@code null} or blank
   */
  private static String requireLabel(String label) {
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("label must not be blank");
    }
    return label;
  }

  /**
   * @return the application-generated identifier
   */
  public AttributeDefinitionId id() {
    return id;
  }

  /**
   * @return the declared key, immutable once created
   */
  public String attributeKey() {
    return attributeKey;
  }

  /**
   * @return the visible label
   */
  public String label() {
    return label;
  }

  /**
   * @return the declared value type, immutable once created
   */
  public AttributeDataType dataType() {
    return dataType;
  }

  /**
   * @return whether {@code GET /products} may filter by this key
   */
  public boolean filterable() {
    return filterable;
  }

  /**
   * @return position in the admin's attribute list
   */
  public int position() {
    return position;
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
