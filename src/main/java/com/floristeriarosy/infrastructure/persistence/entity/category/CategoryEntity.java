package com.floristeriarosy.infrastructure.persistence.entity.category;

import com.floristeriarosy.domain.model.category.CategoryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA mapping of the {@code categories} table. No {@code version} column, deliberately: this
 * aggregate is excluded from ADR-009's optimistic-locking list (category.md, section 2).
 */
@Entity
@Table(name = "categories")
public class CategoryEntity {

  private static final Logger LOGGER = LoggerFactory.getLogger(CategoryEntity.class);

  @Id private UUID id;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 170)
  private String slug;

  @Column private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CategoryStatus status;

  @Column(name = "image_id")
  private UUID imageId;

  @Column(nullable = false)
  private int position;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA; not for application use. */
  protected CategoryEntity() {}

  /**
   * @param id the primary key
   * @param name the category name
   * @param slug the category slug
   * @param description optional description
   * @param status {@code ACTIVE} or {@code INACTIVE}
   * @param imageId optional reference to an {@code images} row
   * @param position position in the public catalog
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted category
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted
   *     category
   */
  public CategoryEntity(
      UUID id,
      String name,
      String slug,
      String description,
      CategoryStatus status,
      UUID imageId,
      int position,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.name = name;
    this.slug = slug;
    this.description = description;
    this.status = status;
    this.imageId = imageId;
    this.position = position;
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
   * @return the primary key
   */
  public UUID getId() {
    return id;
  }

  /**
   * @return the category name
   */
  public String getName() {
    return name;
  }

  /**
   * @return the category slug
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
   * @return {@code ACTIVE} or {@code INACTIVE}
   */
  public CategoryStatus getStatus() {
    return status;
  }

  /**
   * @return the referenced {@code images} row id, or {@code null}
   */
  public UUID getImageId() {
    return imageId;
  }

  /**
   * @return position in the public catalog
   */
  public int getPosition() {
    return position;
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
