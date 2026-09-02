package com.floristeriarosy.infrastructure.persistence.entity.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping of the {@code stock_movements} table. Insert-only (inventory.md, section 1): no
 * {@code applyChanges}/update method, since nothing ever edits a movement once written.
 */
@Entity
@Table(name = "stock_movements")
public class StockMovementEntity {

  @Id private UUID id;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Column(name = "resulting_stock", nullable = false)
  private int resultingStock;

  @Column(name = "admin_user_id")
  private UUID adminUserId;

  @Column(name = "note")
  private String note;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** Required by JPA; not for application use. */
  protected StockMovementEntity() {}

  /**
   * @param id the primary key
   * @param productId the product this movement belongs to
   * @param type the kind of movement
   * @param quantity the signed quantity
   * @param resultingStock the product's stock immediately after this movement
   * @param adminUserId who triggered it, or {@code null}
   * @param note optional note, or {@code null}
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted movement
   */
  public StockMovementEntity(
      UUID id, UUID productId, String type, int quantity, int resultingStock, UUID adminUserId, String note,
      Instant createdAt) {
    this.id = id;
    this.productId = productId;
    this.type = type;
    this.quantity = quantity;
    this.resultingStock = resultingStock;
    this.adminUserId = adminUserId;
    this.note = note;
    this.createdAt = createdAt;
  }

  /**
   * {@code V1} only gives {@code created_at} a DB-side {@code DEFAULT now()}; set it here too,
   * matching product-discounts.md's own entity.
   */
  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  /**
   * @return the primary key
   */
  public UUID getId() {
    return id;
  }

  /**
   * @return the product this movement belongs to
   */
  public UUID getProductId() {
    return productId;
  }

  /**
   * @return the kind of movement
   */
  public String getType() {
    return type;
  }

  /**
   * @return the signed quantity
   */
  public int getQuantity() {
    return quantity;
  }

  /**
   * @return the product's stock immediately after this movement
   */
  public int getResultingStock() {
    return resultingStock;
  }

  /**
   * @return who triggered it, or {@code null}
   */
  public UUID getAdminUserId() {
    return adminUserId;
  }

  /**
   * @return the optional note, or {@code null}
   */
  public String getNote() {
    return note;
  }

  /**
   * @return when the row was created
   */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
