package com.floristeriarosy.domain.model.payment;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root of the payment module (payment.md). The source of truth of an order's payment,
 * independent of {@code orders.status} (payment.md, section 1).
 */
public final class Payment {

  private static final Logger LOGGER = LoggerFactory.getLogger(Payment.class);

  private final PaymentId id;
  private final OrderId orderId;
  private final PaymentMethod method;
  private final PaymentProvider provider;
  private String providerPaymentIntentId;
  private String providerReference;
  private final BigDecimal amount;
  private PaymentStatus status;
  private BigDecimal refundedAmount;
  private Instant authorizedAt;
  private Instant capturedAt;
  private Instant canceledAt;
  private Instant refundedAt;
  private String failureReason;
  private String refundReason;
  private final Instant createdAt;
  private Instant updatedAt;

  /**
   * Private constructor. Use {@link #initiate} to create a new payment, or {@link #reconstitute} to
   * rebuild one from persisted state.
   *
   * @param id application-generated identifier
   * @param orderId the order this payment belongs to
   * @param method how the customer pays
   * @param provider who actually captures the charge
   * @param providerPaymentIntentId the gateway's charge id, or {@code null} until captured
   * @param providerReference the gateway's refund id, or {@code null} until refunded
   * @param amount the amount to charge
   * @param status the lifecycle status
   * @param refundedAmount the amount refunded so far
   * @param authorizedAt when the charge was authorized, or {@code null}
   * @param capturedAt when the charge was captured, or {@code null}
   * @param canceledAt when the charge was canceled, or {@code null}
   * @param refundedAt when it was last refunded, or {@code null}
   * @param failureReason why the charge failed, or {@code null}
   * @param refundReason the manual refund's reason, or {@code null}
   * @param createdAt when the row was created, or {@code null} before the first save
   * @param updatedAt when the row was last updated, or {@code null} before the first save
   */
  private Payment(
      PaymentId id,
      OrderId orderId,
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
    this.id = Objects.requireNonNull(id, "id");
    this.orderId = Objects.requireNonNull(orderId, "orderId");
    this.method = Objects.requireNonNull(method, "method");
    this.provider = Objects.requireNonNull(provider, "provider");
    this.providerPaymentIntentId = providerPaymentIntentId;
    this.providerReference = providerReference;
    this.amount = Objects.requireNonNull(amount, "amount");
    this.status = Objects.requireNonNull(status, "status");
    this.refundedAmount = Objects.requireNonNull(refundedAmount, "refundedAmount");
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
   * Initiates a new payment for an order, {@code PENDING}, nothing captured yet (payment.md,
   * section 3.2). Capturing, failing or refunding it is added in Phase 2, alongside the capability
   * that implements {@code OrderPaymentPort}.
   *
   * @param id application-generated identifier
   * @param orderId the order this payment belongs to
   * @param method how the customer pays
   * @param provider {@code REDSYS} for {@code CARD_ONLINE}, {@code MANUAL} for the rest
   * @param amount the amount to charge
   * @return the new, {@code PENDING} payment
   */
  public static Payment initiate(
      PaymentId id, OrderId orderId, PaymentMethod method, PaymentProvider provider, BigDecimal amount) {
    LOGGER.debug(
        "initiate id={} orderId={} method={} provider={} amount={}", id, orderId, method, provider,
        amount);
    Payment result =
        new Payment(
            id,
            orderId,
            method,
            provider,
            null,
            null,
            amount,
            PaymentStatus.PENDING,
            BigDecimal.ZERO,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    LOGGER.debug("initiate id={} -> initiated", id);
    return result;
  }

  /**
   * Rebuilds a payment from persisted state. Used only by the persistence mapper.
   *
   * @param id the persisted identifier
   * @param orderId the persisted order reference
   * @param method the persisted payment method
   * @param provider the persisted provider
   * @param providerPaymentIntentId the persisted gateway charge id, or {@code null}
   * @param providerReference the persisted gateway refund id, or {@code null}
   * @param amount the persisted amount
   * @param status the persisted status
   * @param refundedAmount the persisted refunded amount so far
   * @param authorizedAt when the charge was authorized, or {@code null}
   * @param capturedAt when the charge was captured, or {@code null}
   * @param canceledAt when the charge was canceled, or {@code null}
   * @param refundedAt when it was last refunded, or {@code null}
   * @param failureReason why the charge failed, or {@code null}
   * @param refundReason the manual refund's reason, or {@code null}
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt payment
   */
  public static Payment reconstitute(
      PaymentId id,
      OrderId orderId,
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
    return new Payment(
        id,
        orderId,
        method,
        provider,
        providerPaymentIntentId,
        providerReference,
        amount,
        status,
        refundedAmount,
        authorizedAt,
        capturedAt,
        canceledAt,
        refundedAt,
        failureReason,
        refundReason,
        createdAt,
        updatedAt);
  }

  /**
   * Marks the charge captured — the money has moved (payment.md, section 3.1/3.2).
   *
   * @param providerPaymentIntentId the gateway's charge id, or {@code null} for {@code MANUAL}
   * @param capturedAt when the capture happened
   * @throws IllegalStateException the payment is not {@code PENDING}
   */
  public void capture(String providerPaymentIntentId, Instant capturedAt) {
    LOGGER.debug("capture id={} providerPaymentIntentId={}", id, providerPaymentIntentId);
    requireStatus(PaymentStatus.PENDING);
    this.providerPaymentIntentId = providerPaymentIntentId;
    this.status = PaymentStatus.CAPTURED;
    this.capturedAt = capturedAt;
    LOGGER.debug("capture id={} -> captured", id);
  }

  /**
   * Marks the charge failed — the gateway declined it, or it was never attempted for {@code
   * MANUAL} reasons that never reach this far in practice (payment.md, section 3.2).
   *
   * @param reason why the charge failed
   * @throws IllegalStateException the payment is not {@code PENDING}
   */
  public void fail(String reason) {
    LOGGER.debug("fail id={}", id);
    requireStatus(PaymentStatus.PENDING);
    this.status = PaymentStatus.FAILED;
    this.failureReason = reason;
    LOGGER.debug("fail id={} -> failed", id);
  }

  /**
   * Refunds the whole amount — the only refund this module ever produces automatically (payment.md,
   * section 3.3). {@code refundReason} is deliberately left {@code null}: the order's status
   * transition is already the reason.
   *
   * @param providerReference the gateway's refund id, or {@code null} for {@code MANUAL}
   * @param refundedAt when the refund happened
   * @throws IllegalStateException the payment is not {@code CAPTURED}
   */
  public void refundInFull(String providerReference, Instant refundedAt) {
    LOGGER.debug("refundInFull id={} providerReference={}", id, providerReference);
    requireStatus(PaymentStatus.CAPTURED);
    this.providerReference = providerReference;
    this.refundedAmount = amount;
    this.refundedAt = refundedAt;
    LOGGER.debug("refundInFull id={} -> refunded {}", id, amount);
  }

  /**
   * @param expected the status this payment must currently have
   * @throws IllegalStateException the current status is not {@code expected}
   */
  private void requireStatus(PaymentStatus expected) {
    if (status != expected) {
      throw new IllegalStateException("Payment " + id + " is " + status + ", expected " + expected);
    }
  }

  /**
   * @return the application-generated identifier
   */
  public PaymentId id() {
    return id;
  }

  /**
   * @return the order this payment belongs to
   */
  public OrderId orderId() {
    return orderId;
  }

  /**
   * @return how the customer pays
   */
  public PaymentMethod method() {
    return method;
  }

  /**
   * @return who actually captures the charge
   */
  public PaymentProvider provider() {
    return provider;
  }

  /**
   * @return the gateway's charge id, or {@code null} until captured
   */
  public String providerPaymentIntentId() {
    return providerPaymentIntentId;
  }

  /**
   * @return the gateway's refund id, or {@code null} until refunded
   */
  public String providerReference() {
    return providerReference;
  }

  /**
   * @return the amount to charge
   */
  public BigDecimal amount() {
    return amount;
  }

  /**
   * @return the lifecycle status
   */
  public PaymentStatus status() {
    return status;
  }

  /**
   * @return the amount refunded so far, zero if none
   */
  public BigDecimal refundedAmount() {
    return refundedAmount;
  }

  /**
   * @return when the charge was authorized, or {@code null}
   */
  public Instant authorizedAt() {
    return authorizedAt;
  }

  /**
   * @return when the charge was captured, or {@code null}
   */
  public Instant capturedAt() {
    return capturedAt;
  }

  /**
   * @return when the charge was canceled, or {@code null}
   */
  public Instant canceledAt() {
    return canceledAt;
  }

  /**
   * @return when it was last refunded, or {@code null}
   */
  public Instant refundedAt() {
    return refundedAt;
  }

  /**
   * @return why the charge failed, or {@code null}
   */
  public String failureReason() {
    return failureReason;
  }

  /**
   * @return the manual refund's reason, or {@code null} for an automatic refund
   */
  public String refundReason() {
    return refundReason;
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
