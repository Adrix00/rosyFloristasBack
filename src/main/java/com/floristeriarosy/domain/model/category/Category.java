package com.floristeriarosy.domain.model.category;

import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import java.time.Instant;
import java.util.UUID;

/** Aggregate root of the category module (ADR-004, reference implementation). */
public final class Category {

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

  /** New category, born {@code ACTIVE} (category.md, section 5). Timestamps set by the DB. */
  public static Category create(
      CategoryId id,
      String name,
      CategorySlug slug,
      String description,
      UUID imageId,
      int position) {
    return new Category(
        id, name, slug, description, CategoryStatus.ACTIVE, imageId, position, null, null);
  }

  /** Rebuilds a category from persisted state. Used only by the persistence mapper. */
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

  /** {@code PUT}: full replace, an absent optional field clears it (category.md, section 5). */
  public void replace(
      String name, CategorySlug slug, String description, UUID imageId, int position) {
    this.name = requireName(name);
    this.slug = slug;
    this.description = description;
    this.imageId = imageId;
    this.position = position;
  }

  /** Idempotent: setting the same status twice is a no-op, not an error (section 10). */
  public void changeStatus(CategoryStatus newStatus) {
    this.status = newStatus;
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    return name;
  }

  public CategoryId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public CategorySlug slug() {
    return slug;
  }

  public String description() {
    return description;
  }

  public CategoryStatus status() {
    return status;
  }

  public UUID imageId() {
    return imageId;
  }

  public int position() {
    return position;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
