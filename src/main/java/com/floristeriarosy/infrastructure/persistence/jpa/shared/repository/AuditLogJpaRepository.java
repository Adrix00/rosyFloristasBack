package com.floristeriarosy.infrastructure.persistence.jpa.shared.repository;

import com.floristeriarosy.infrastructure.persistence.entity.shared.AuditLogEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link AuditLogEntity}: insert-only (ADR-010). */
public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, UUID> {}
