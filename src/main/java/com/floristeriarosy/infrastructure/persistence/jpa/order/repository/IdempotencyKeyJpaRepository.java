package com.floristeriarosy.infrastructure.persistence.jpa.order.repository;

import com.floristeriarosy.infrastructure.persistence.entity.order.IdempotencyKeyEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@code idempotency_keys} (ADR-011). */
public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

  /**
   * @param idempotencyKey the client-supplied key
   * @param endpoint the endpoint this lock guards
   * @return the row, if one exists for this key on this endpoint
   */
  Optional<IdempotencyKeyEntity> findByIdempotencyKeyAndEndpoint(String idempotencyKey, String endpoint);
}
