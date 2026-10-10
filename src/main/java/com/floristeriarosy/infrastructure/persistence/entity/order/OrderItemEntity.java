package com.floristeriarosy.infrastructure.persistence.entity.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA mapping of the {@code order_items} table (order.md, rule 3.5). No {@code @Version}: the child
 * is always persisted through its owning {@link OrderEntity}.
 */
@Entity
@Table(name = "order_items")
public class OrderItemEntity {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private OrderEntity order;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "product_name", nullable = false, length = 200)
  private String productName;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "product_attributes", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> productAttributes;

  @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "discount_price", precision = 10, scale = 2)
  private BigDecimal discountPrice;

  @Column(name = "discount_id")
  private UUID discountId;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** Required by JPA; not for application use. */
  protected OrderItemEntity() {}

  /**
   * @param id the primary key
   * @param order the owning order
   * @param productId the sold product
   * @param productName the product's name at the moment of sale
   * @param productAttributes the product's attribute values at the moment of sale
   * @param unitPrice the base price at the moment of sale
   * @param discountPrice the discounted price at the moment of sale, or {@code null}
   * @param discountId the applied promotion's reference, or {@code null}
   * @param quantity units sold
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted entity
   */
  public OrderItemEntity(
      UUID id,
      OrderEntity order,
      UUID productId,
      String productName,
      Map<String, Object> productAttributes,
      BigDecimal unitPrice,
      BigDecimal discountPrice,
      UUID discountId,
      Integer quantity,
      Instant createdAt) {
    this.id = id;
    this.order = order;
    this.productId = productId;
    this.productName = productName;
    this.productAttributes = productAttributes == null ? Map.of() : new LinkedHashMap<>(productAttributes);
    this.unitPrice = unitPrice;
    this.discountPrice = discountPrice;
    this.discountId = discountId;
    this.quantity = quantity;
    this.createdAt = createdAt;
  }

  /** Sets {@code createdAt} if the application code left it unset. */
  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  /** @return the primary key */
  public UUID getId() {
    return id;
  }

  /** @return the owning order */
  public OrderEntity getOrder() {
    return order;
  }

  /**
   * @param order the owning order
   */
  public void setOrder(OrderEntity order) {
    this.order = order;
  }

  /** @return the sold product */
  public UUID getProductId() {
    return productId;
  }

  /** @return the product's name at the moment of sale */
  public String getProductName() {
    return productName;
  }

  /** @return the product's attribute values at the moment of sale */
  public Map<String, Object> getProductAttributes() {
    return Map.copyOf(productAttributes);
  }

  /** @return the base price at the moment of sale */
  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  /** @return the discounted price at the moment of sale, or {@code null} */
  public BigDecimal getDiscountPrice() {
    return discountPrice;
  }

  /** @return the applied promotion's reference, or {@code null} */
  public UUID getDiscountId() {
    return discountId;
  }

  /** @return units sold */
  public Integer getQuantity() {
    return quantity;
  }

  /** @return when the row was created */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
