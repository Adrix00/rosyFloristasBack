package com.floristeriarosy.infrastructure.persistence.entity.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA mapping of the {@code cart_items} table. Written independently of {@link CartEntity} — no
 * JPA collection mapping on the cart side — so a single line change never rewrites the whole
 * aggregate ({@code CartItemWritePort}).
 */
@Entity
@Table(name = "cart_items")
public class CartItemEntity {

  private static final Logger LOGGER = LoggerFactory.getLogger(CartItemEntity.class);

  @Id private UUID id;

  @Column(name = "cart_id", nullable = false)
  private UUID cartId;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA; not for application use. */
  protected CartItemEntity() {}

  /**
   * @param id the primary key
   * @param cartId the owning cart
   * @param productId the product this line refers to
   * @param quantity the quantity, always positive ({@code chk_cart_items_quantity})
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted line
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted line
   */
  public CartItemEntity(
      UUID id, UUID cartId, UUID productId, int quantity, Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.cartId = cartId;
    this.productId = productId;
    this.quantity = quantity;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * @param quantity the new quantity to persist for this line
   */
  public void changeQuantity(int quantity) {
    this.quantity = quantity;
    LOGGER.debug("changeQuantity id={} -> quantity={}", id, quantity);
  }

  /** See {@link CartEntity#onCreate()}: same reasoning, applied to this entity. */
  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
    LOGGER.debug("onCreate id={} createdAt={}", id, createdAt);
  }

  /** See {@link CartEntity#onUpdate()}. */
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
   * @return the owning cart
   */
  public UUID getCartId() {
    return cartId;
  }

  /**
   * @return the product this line refers to
   */
  public UUID getProductId() {
    return productId;
  }

  /**
   * @return the quantity
   */
  public int getQuantity() {
    return quantity;
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
