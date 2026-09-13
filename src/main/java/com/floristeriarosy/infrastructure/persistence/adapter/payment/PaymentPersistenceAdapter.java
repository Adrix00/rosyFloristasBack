package com.floristeriarosy.infrastructure.persistence.adapter.payment;

import com.floristeriarosy.application.payment.port.out.PaymentReadPort;
import com.floristeriarosy.application.payment.port.out.PaymentWritePort;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import com.floristeriarosy.infrastructure.persistence.entity.payment.PaymentEntity;
import com.floristeriarosy.infrastructure.persistence.jpa.payment.repository.PaymentJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.payment.PaymentPersistenceMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Implements the payment output ports (ADR-003) with plain JPA (ADR-002, payment.md section 8).
 */
@Repository
public class PaymentPersistenceAdapter implements PaymentReadPort, PaymentWritePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(PaymentPersistenceAdapter.class);

  private final PaymentJpaRepository jpaRepository;
  private final PaymentPersistenceMapper mapper;

  /**
   * @param jpaRepository writes and simple lookups
   * @param mapper converts between the domain {@link Payment} and the JPA entity
   */
  public PaymentPersistenceAdapter(PaymentJpaRepository jpaRepository, PaymentPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.mapper = mapper;
  }

  /**
   * @param id the payment to load
   * @return the payment, if it exists
   */
  @Override
  public Optional<Payment> findById(PaymentId id) {
    LOGGER.debug("findById id={}", id);
    Optional<Payment> result = jpaRepository.findById(id.value()).map(mapper::toDomain);
    LOGGER.debug("findById id={} -> found={}", id, result.isPresent());
    return result;
  }

  /**
   * @param orderId the order whose payment to load
   * @return the order's payment, if one was captured
   */
  @Override
  public Optional<Payment> findByOrderId(OrderId orderId) {
    LOGGER.debug("findByOrderId orderId={}", orderId);
    Optional<Payment> result = jpaRepository.findByOrderId(orderId.value()).map(mapper::toDomain);
    LOGGER.debug("findByOrderId orderId={} -> found={}", orderId, result.isPresent());
    return result;
  }

  /**
   * @param payment the payment to insert or update
   * @return the saved payment, with timestamps populated by the database
   */
  @Override
  public Payment save(Payment payment) {
    LOGGER.debug("save id={} status={}", payment.id(), payment.status());
    PaymentEntity entity = mapper.toEntity(payment);
    Payment result = mapper.toDomain(jpaRepository.saveAndFlush(entity));
    LOGGER.debug("save id={} -> saved", result.id());
    return result;
  }
}
