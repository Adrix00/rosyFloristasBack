package com.floristeriarosy.domain.model.category;

import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import com.floristeriarosy.shared.util.LogSanitizer;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Aggregate root of the category module (ADR-004, reference implementation). */
public final class Category {

  private static final Logger LOGGER = LoggerFactory.getLogger(Category.class);

  private final CategoryId id;
  private String name;
  private CategorySlug slug;
  private String description;
  private CategoryStatus status;
  private UUID imageId;
  private int position;
  private final Instant createdAt;
  private Instant updatedAt;

  private Category(
      CategoryId id,
      String name,
      CategorySlug slug,
      String description,
      CategoryStatus status,
      UUID imageId,
      int position,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.name = requireName(name);
    this.slug = slug;
    this.description = description;
    this.status = status;
    this.imageId = imageId;
    this.position = position;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * New category, born {@code ACTIVE} (category.md, section 5). Timestamps are left {@code null} —
   * the database sets them on insert.
   *
   * @param id application-generated identifier
   * @param name the category name
   * @param slug the slug generated from {@code name}
   * @param description optional description
   * @param imageId optional reference to an {@code images} row
   * @param position position in the public catalog
   * @return the new, not-yet-persisted category
   */
  public static Category create(
      CategoryId id,
      String name,
      CategorySlug slug,
      String description,
      UUID imageId,
      int position) {
    LOGGER.debug("create id={} name={} slug={}", id, LogSanitizer.sanitize(name), slug);
    Category result =
        new Category(
            id, name, slug, description, CategoryStatus.ACTIVE, imageId, position, null, null);
    LOGGER.debug("create id={} -> created", id);
    return result;
  }

  /**
   * Rebuilds a category from persisted state. Used only by the persistence mapper — not logged, it
   * runs once per row loaded from the database.
   *
   * @param id the persisted identifier
   * @param name the persisted name
   * @param slug the persisted slug
   * @param description the persisted description
   * @param status the persisted status
   * @param imageId the persisted image reference
   * @param position the persisted position
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt category
   */
  public static Category reconstitute(
      CategoryId id,
      String name,
      CategorySlug slug,
      String description,
      CategoryStatus status,
      UUID imageId,
      int position,
      Instant createdAt,
      Instant updatedAt) {
    return new Category(
        id, name, slug, description, status, imageId, position, createdAt, updatedAt);
  }

  /**
   * {@code PUT}: full replace, an absent optional field clears it (category.md, section 5).
   *
   * @param name the new name
   * @param slug the slug generated from {@code name}
   * @param description the new description, or {@code null} to clear it
   * @param imageId the new image reference, or {@code null} to clear it
   * @param position the new position
   */
  public void replace(
      String name, CategorySlug slug, String description, UUID imageId, int position) {
    LOGGER.debug("replace id={} name={} slug={}", id, LogSanitizer.sanitize(name), slug);
    this.name = requireName(name);
    this.slug = slug;
    this.description = description;
    this.imageId = imageId;
    this.position = position;
    LOGGER.debug("replace id={} -> replaced", id);
  }

  /**
   * Sets the status. Idempotent: setting the same status twice is a no-op, not an error (section
   * 10).
   *
   * @param newStatus the status to set
   */
  public void changeStatus(CategoryStatus newStatus) {
    LOGGER.debug("changeStatus id={} from={} to={}", id, status, newStatus);
    this.status = newStatus;
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
   * @return the application-generated identifier
   */
  public CategoryId id() {
    return id;
  }

  /**
   * @return the category name
   */
  public String name() {
    return name;
  }

  /**
   * @return the slug generated from the name
   */
  public CategorySlug slug() {
    return slug;
  }

  /**
   * @return the description, or {@code null}
   */
  public String description() {
    return description;
  }

  /**
   * @return {@code ACTIVE} or {@code INACTIVE}
   */
  public CategoryStatus status() {
    return status;
  }

  /**
   * @return the referenced {@code images} row id, or {@code null}
   */
  public UUID imageId() {
    return imageId;
  }

  /**
   * @return position in the public catalog
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
