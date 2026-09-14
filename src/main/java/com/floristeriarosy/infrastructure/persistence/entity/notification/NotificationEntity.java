package com.floristeriarosy.infrastructure.persistence.entity.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping of the {@code notifications} table (notification.md, section 2/3.4: transactional
 * outbox). No {@code @Version}: rows are inserted and later updated only by the future scheduled
 * sender task, never concurrently from two requests.
 */
@Entity
@Table(name = "notifications")
public class NotificationEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 40)
  private String type;

  @Column(name = "order_id")
  private UUID orderId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false)
  private Integer attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** Required by JPA; not for application use. */
  protected NotificationEntity() {}

  /**
   * @param id the primary key
   * @param type the notification catalog type
   * @param orderId the referenced order, or {@code null}
   * @param customerId the referenced customer, or {@code null}
   * @param status the lifecycle status ({@code PENDING}, {@code SENT}, or {@code FAILED})
   * @param attempts how many send attempts have been made
   * @param nextAttemptAt when the next send attempt should happen
   * @param lastError the last failure reason, or {@code null}
   * @param sentAt when the notification was successfully sent, or {@code null}
   * @param createdAt when the row was created
   */
  public NotificationEntity(
      UUID id,
      String type,
      UUID orderId,
      UUID customerId,
      String status,
      Integer attempts,
      Instant nextAttemptAt,
      String lastError,
      Instant sentAt,
      Instant createdAt) {
    this.id = id;
    this.type = type;
    this.orderId = orderId;
    this.customerId = customerId;
    this.status = status;
    this.attempts = attempts;
    this.nextAttemptAt = nextAttemptAt;
    this.lastError = lastError;
    this.sentAt = sentAt;
    this.createdAt = createdAt;
  }

  /** Fills {@code createdAt}/{@code nextAttemptAt} if the caller left them unset. */
  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (nextAttemptAt == null) {
      nextAttemptAt = now;
    }
  }

  /** @return the primary key */
  public UUID getId() {
    return id;
  }

  /** @return the notification catalog type */
  public String getType() {
    return type;
  }

  /** @return the referenced order, or {@code null} */
  public UUID getOrderId() {
    return orderId;
  }

  /** @return the referenced customer, or {@code null} */
  public UUID getCustomerId() {
    return customerId;
  }

  /** @return the lifecycle status */
  public String getStatus() {
    return status;
  }

  /** @return how many send attempts have been made */
  public Integer getAttempts() {
    return attempts;
  }

  /** @return when the next send attempt should happen */
  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  /** @return the last failure reason, or {@code null} */
  public String getLastError() {
    return lastError;
  }

  /** @return when the notification was successfully sent, or {@code null} */
  public Instant getSentAt() {
    return sentAt;
  }

  /** @return when the row was created */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
