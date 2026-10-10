package com.floristeriarosy.infrastructure.persistence.jpa.order.repository;

import com.floristeriarosy.infrastructure.persistence.entity.order.IdempotencyKeyEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Spring Data JPA repository for {@code idempotency_keys} (ADR-011). */
public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

  /**
   * @param idempotencyKey the client-supplied key
   * @param endpoint the endpoint this lock guards
   * @return the row, if one exists for this key on this endpoint
   */
  Optional<IdempotencyKeyEntity> findByIdempotencyKeyAndEndpoint(String idempotencyKey, String endpoint);

  /**
   * Same lookup, but takes the row lock ({@code SELECT ... FOR UPDATE}) for the rest of the caller's
   * transaction, so two writers resolving the same lock serialize: the second sees the first's
   * outcome.
   *
   * @param idempotencyKey the client-supplied key
   * @param endpoint the endpoint this lock guards
   * @return the row, locked, if one exists for this key on this endpoint
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<IdempotencyKeyEntity> findForUpdateByIdempotencyKeyAndEndpoint(
      String idempotencyKey, String endpoint);
}
