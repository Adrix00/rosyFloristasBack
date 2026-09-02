package com.floristeriarosy.infrastructure.persistence.jpa.inventory.repository;

import com.floristeriarosy.infrastructure.persistence.entity.inventory.StockMovementEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link StockMovementEntity}: insert-only (ADR-002). */
public interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, UUID> {}
