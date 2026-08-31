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

@Entity
@Table(name = "categories")
public class CategoryEntity {

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

  protected CategoryEntity() {}

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

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public String getDescription() {
    return description;
  }

  public CategoryStatus getStatus() {
    return status;
  }

  public UUID getImageId() {
    return imageId;
  }

  public int getPosition() {
    return position;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
