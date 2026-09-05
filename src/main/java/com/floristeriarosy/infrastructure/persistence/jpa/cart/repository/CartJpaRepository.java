package com.floristeriarosy.infrastructure.persistence.jpa.cart.repository;

import com.floristeriarosy.infrastructure.persistence.entity.cart.CartEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link CartEntity}: writes and simple lookups (ADR-002). */
public interface CartJpaRepository extends JpaRepository<CartEntity, UUID> {

  /**
   * @param customerId the owning customer
   * @return the entity, if it exists
   */
  Optional<CartEntity> findByCustomerId(UUID customerId);

  /**
   * @param sessionToken the cart's session token
   * @return the entity, if it exists
   */
  Optional<CartEntity> findBySessionToken(String sessionToken);

  /**
   * Renews {@code expires_at} without loading and re-saving the whole row (cart.md, rule 3.7).
   *
   * @param id the cart to renew
   * @param expiresAt the new expiry
   * @param updatedAt the new {@code updated_at}
   */
  @Modifying
  @Query("update CartEntity c set c.expiresAt = :expiresAt, c.updatedAt = :updatedAt where c.id = :id")
  void touch(@Param("id") UUID id, @Param("expiresAt") Instant expiresAt, @Param("updatedAt") Instant updatedAt);
}
