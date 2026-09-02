package com.floristeriarosy.infrastructure.persistence.jpa.inventory.repository;

import com.floristeriarosy.infrastructure.persistence.entity.inventory.InventoryAlertEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link InventoryAlertEntity}: writes and simple lookups (ADR-002). */
public interface InventoryAlertJpaRepository extends JpaRepository<InventoryAlertEntity, UUID> {}
