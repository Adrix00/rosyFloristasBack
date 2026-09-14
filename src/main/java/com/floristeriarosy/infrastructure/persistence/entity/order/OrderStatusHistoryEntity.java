package com.floristeriarosy.infrastructure.persistence.entity.order;

import com.floristeriarosy.domain.model.order.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * JPA mapping of {@code order_status_history} (order.md, section 3.9). Each row is an insert-only
 * audit of one status transition; {@code changed_at} is defaulted by the database and is left out
 * of the write path.
 */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity {

  @Id private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private OrderStatus status;

  @Column(name = "changed_by_admin_id")
  private UUID changedByAdminId;

  @Column(name = "changed_at", insertable = false, updatable = false)
  @Generated(event = EventType.INSERT)
  private Instant changedAt;

  @Column(name = "reason", length = 500)
  private String reason;

  /** Required by JPA; not for application use. */
  protected OrderStatusHistoryEntity() {}

  /**
   * @param id the primary key
   * @param orderId the order that changed
   * @param status the status it changed to
   * @param changedByAdminId the admin who triggered it, or {@code null} when the customer did
   * @param reason the admin's stated reason for {@code REJECTED}/{@code CANCELLED}, or {@code null}
   */
  public OrderStatusHistoryEntity(
      UUID id,
      UUID orderId,
      OrderStatus status,
      UUID changedByAdminId,
      String reason) {
    this.id = id;
    this.orderId = orderId;
    this.status = status;
    this.changedByAdminId = changedByAdminId;
    this.reason = reason;
  }

  /**
   * @return the primary key
   */
  public UUID getId() {
    return id;
  }

  /**
   * @return the order that changed
   */
  public UUID getOrderId() {
    return orderId;
  }

  /**
   * @return the status it changed to
   */
  public OrderStatus getStatus() {
    return status;
  }

  /**
   * @return the admin who triggered it, or {@code null} when the customer did
   */
  public UUID getChangedByAdminId() {
    return changedByAdminId;
  }

  /**
   * @return when the transition happened, populated by the database default
   */
  public Instant getChangedAt() {
    return changedAt;
  }

  /**
   * @return the admin's stated reason, or {@code null}
   */
  public String getReason() {
    return reason;
  }
}
