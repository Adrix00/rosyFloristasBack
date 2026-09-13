package com.floristeriarosy.infrastructure.persistence.jpa.payment.repository;

import com.floristeriarosy.infrastructure.persistence.entity.payment.PaymentEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@code payments} (ADR-002). */
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

  /**
   * @param orderId the order to look up
   * @return the order's payment, if one was captured
   */
  Optional<PaymentEntity> findByOrderId(UUID orderId);
}
