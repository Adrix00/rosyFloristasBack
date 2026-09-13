package com.floristeriarosy.infrastructure.persistence.entity.payment;

import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.payment.PaymentProvider;
import com.floristeriarosy.domain.model.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping of the {@code payments} table (payment.md, section 2). No {@code @Version}: a payment
 * is written once (capture) and updated at most once more (refund), both driven by this module's
 * own use cases, never concurrently from two requests for the same payment.
 */
@Entity
@Table(name = "payments")
public class PaymentEntity {

  @Id private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentMethod method;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PaymentProvider provider;

  @Column(name = "provider_payment_intent_id", length = 255)
  private String providerPaymentIntentId;

  @Column(name = "provider_reference", length = 255)
  private String providerReference;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  @Column(name = "refunded_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal refundedAmount;

  @Column(name = "authorized_at")
  private Instant authorizedAt;

  @Column(name = "captured_at")
  private Instant capturedAt;

  @Column(name = "canceled_at")
  private Instant canceledAt;

  @Column(name = "refunded_at")
  private Instant refundedAt;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Column(name = "refund_reason", length = 500)
  private String refundReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA; not for application use. */
  protected PaymentEntity() {}

  /**
   * @param id the primary key
   * @param orderId the order this payment belongs to
   * @param method how the customer pays
   * @param provider who actually captures the charge
   * @param providerPaymentIntentId the gateway's charge id, or {@code null}
   * @param providerReference the gateway's refund id, or {@code null}
   * @param amount the amount to charge
   * @param status the lifecycle status
   * @param refundedAmount the amount refunded so far
   * @param authorizedAt when the charge was authorized, or {@code null}
   * @param capturedAt when the charge was captured, or {@code null}
   * @param canceledAt when the charge was canceled, or {@code null}
   * @param refundedAt when it was last refunded, or {@code null}
   * @param failureReason why the charge failed, or {@code null}
   * @param refundReason the manual refund's reason, or {@code null}
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted payment
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted payment
   */
  public PaymentEntity(
      UUID id,
      UUID orderId,
      PaymentMethod method,
      PaymentProvider provider,
      String providerPaymentIntentId,
      String providerReference,
      BigDecimal amount,
      PaymentStatus status,
      BigDecimal refundedAmount,
      Instant authorizedAt,
      Instant capturedAt,
      Instant canceledAt,
      Instant refundedAt,
      String failureReason,
      String refundReason,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.orderId = orderId;
    this.method = method;
    this.provider = provider;
    this.providerPaymentIntentId = providerPaymentIntentId;
    this.providerReference = providerReference;
    this.amount = amount;
    this.status = status;
    this.refundedAmount = refundedAmount;
    this.authorizedAt = authorizedAt;
    this.capturedAt = capturedAt;
    this.canceledAt = canceledAt;
    this.refundedAt = refundedAt;
    this.failureReason = failureReason;
    this.refundReason = refundReason;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Sets {@code createdAt}/{@code updatedAt} in application code (same reasoning as {@code
   * AdminUserEntity}: {@code V1} only gives {@code created_at} a DB default).
   */
  @jakarta.persistence.PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  /** Refreshes {@code updatedAt} on every update; see {@link #onCreate()}. */
  @jakarta.persistence.PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  /** @return the primary key */
  public UUID getId() {
    return id;
  }

  /** @return the order this payment belongs to */
  public UUID getOrderId() {
    return orderId;
  }

  /** @return how the customer pays */
  public PaymentMethod getMethod() {
    return method;
  }

  /** @return who actually captures the charge */
  public PaymentProvider getProvider() {
    return provider;
  }

  /** @return the gateway's charge id, or {@code null} */
  public String getProviderPaymentIntentId() {
    return providerPaymentIntentId;
  }

  /** @return the gateway's refund id, or {@code null} */
  public String getProviderReference() {
    return providerReference;
  }

  /** @return the amount to charge */
  public BigDecimal getAmount() {
    return amount;
  }

  /** @return the lifecycle status */
  public PaymentStatus getStatus() {
    return status;
  }

  /** @return the amount refunded so far */
  public BigDecimal getRefundedAmount() {
    return refundedAmount;
  }

  /** @return when the charge was authorized, or {@code null} */
  public Instant getAuthorizedAt() {
    return authorizedAt;
  }

  /** @return when the charge was captured, or {@code null} */
  public Instant getCapturedAt() {
    return capturedAt;
  }

  /** @return when the charge was canceled, or {@code null} */
  public Instant getCanceledAt() {
    return canceledAt;
  }

  /** @return when it was last refunded, or {@code null} */
  public Instant getRefundedAt() {
    return refundedAt;
  }

  /** @return why the charge failed, or {@code null} */
  public String getFailureReason() {
    return failureReason;
  }

  /** @return the manual refund's reason, or {@code null} */
  public String getRefundReason() {
    return refundReason;
  }

  /** @return when the row was created */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** @return when the row was last updated */
  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
