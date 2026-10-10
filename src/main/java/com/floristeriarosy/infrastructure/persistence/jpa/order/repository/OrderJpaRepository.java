package com.floristeriarosy.infrastructure.persistence.jpa.order.repository;

import com.floristeriarosy.infrastructure.persistence.entity.order.OrderEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA access to {@code orders} and simple lookups by business identifier. */
public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

  /**
   * @param orderNumber the business identifier to look up
   * @return the order entity, if it exists
   */
  Optional<OrderEntity> findByOrderNumber(String orderNumber);

  /**
   * @param customerId the customer whose orders to list
   * @return the customer's orders, newest first
   */
  List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
