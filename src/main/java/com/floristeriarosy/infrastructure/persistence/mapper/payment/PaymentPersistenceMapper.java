package com.floristeriarosy.infrastructure.persistence.mapper.payment;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import com.floristeriarosy.infrastructure.persistence.entity.payment.PaymentEntity;
import org.springframework.stereotype.Component;

/** Domain ↔ JPA entity conversions (ADR-002: Persistence Mapper). */
@Component
public class PaymentPersistenceMapper {

  /**
   * @param payment the domain payment to persist
   * @return its JPA entity shape
   */
  public PaymentEntity toEntity(Payment payment) {
    return new PaymentEntity(
        payment.id().value(),
        payment.orderId().value(),
        payment.method(),
        payment.provider(),
        payment.providerPaymentIntentId(),
        payment.providerReference(),
        payment.amount(),
        payment.status(),
        payment.refundedAmount(),
        payment.authorizedAt(),
        payment.capturedAt(),
        payment.canceledAt(),
        payment.refundedAt(),
        payment.failureReason(),
        payment.refundReason(),
        payment.createdAt(),
        payment.updatedAt());
  }

  /**
   * @param entity the persisted JPA entity
   * @return the rebuilt domain payment ({@link Payment#reconstitute})
   */
  public Payment toDomain(PaymentEntity entity) {
    return Payment.reconstitute(
        PaymentId.of(entity.getId()),
        OrderId.of(entity.getOrderId()),
        entity.getMethod(),
        entity.getProvider(),
        entity.getProviderPaymentIntentId(),
        entity.getProviderReference(),
        entity.getAmount(),
        entity.getStatus(),
        entity.getRefundedAmount(),
        entity.getAuthorizedAt(),
        entity.getCapturedAt(),
        entity.getCanceledAt(),
        entity.getRefundedAt(),
        entity.getFailureReason(),
        entity.getRefundReason(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
