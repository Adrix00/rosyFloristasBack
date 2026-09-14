package com.floristeriarosy.infrastructure.persistence.jpa.order.repository;

import com.floristeriarosy.infrastructure.persistence.entity.order.OrderStatusHistoryEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA access to {@code order_status_history} — insert-only in this phase. */
public interface OrderStatusHistoryJpaRepository
    extends JpaRepository<OrderStatusHistoryEntity, UUID> {}
