package com.floristeriarosy.infrastructure.persistence.adapter.order;

import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.port.out.OrderWritePort;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.infrastructure.persistence.entity.order.OrderEntity;
import com.floristeriarosy.infrastructure.persistence.jpa.order.repository.OrderJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.order.OrderPersistenceMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the order output ports (ADR-003) with plain JPA (ADR-002, order.md section 8): JPA for
 * writes and simple lookups, JDBC reserved for future admin listings and filters.
 */
@Repository
@Transactional
public class OrderPersistenceAdapter implements OrderReadPort, OrderWritePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderPersistenceAdapter.class);

  private final OrderJpaRepository jpaRepository;
  private final OrderPersistenceMapper mapper;

  /**
   * @param jpaRepository writes and simple lookups by id/order number
   * @param mapper converts between the domain {@link Order} and the JPA {@link OrderEntity}
   */
  public OrderPersistenceAdapter(OrderJpaRepository jpaRepository, OrderPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.mapper = mapper;
  }

  /**
   * @param id the order to load
   * @return the order, if it exists
   */
  @Override
  public Optional<Order> findById(OrderId id) {
    LOGGER.debug("findById id={}", id);
    Optional<Order> result = jpaRepository.findById(id.value()).map(mapper::toDomain);
    LOGGER.debug("findById id={} -> found={}", id, result.isPresent());
    return result;
  }

  /**
   * @param orderNumber the business identifier to look up
   * @return the order, if it exists
   */
  @Override
  public Optional<Order> findByOrderNumber(OrderNumber orderNumber) {
    LOGGER.debug("findByOrderNumber orderNumber={}", orderNumber);
    Optional<Order> result = jpaRepository.findByOrderNumber(orderNumber.value()).map(mapper::toDomain);
    LOGGER.debug("findByOrderNumber orderNumber={} -> found={}", orderNumber, result.isPresent());
    return result;
  }

  /**
   * Updates the managed entity in place when {@code order.id()} already exists, so the {@code
   * @Version} Hibernate loaded is what gets checked (ADR-009). Builds a fresh aggregate only for a
   * genuinely new order.
   *
   * @param order the order to insert or update
   * @return the saved order, with timestamps populated by the database
   * @throws ResourceModifiedException the order was changed concurrently
   */
  @Override
  public Order save(Order order) {
    LOGGER.debug("save id={} orderNumber={}", order.id(), order.orderNumber());
    OrderEntity entity =
        jpaRepository
            .findById(order.id().value())
            .map(existing -> mapper.updateEntity(existing, order))
            .orElseGet(() -> mapper.toEntity(order));
    try {
      Order result = mapper.toDomain(jpaRepository.saveAndFlush(entity));
      LOGGER.debug("save id={} -> saved", result.id());
      return result;
    } catch (ObjectOptimisticLockingFailureException conflict) {
      throw new ResourceModifiedException("Order " + order.id() + " was modified concurrently");
    }
  }
}
