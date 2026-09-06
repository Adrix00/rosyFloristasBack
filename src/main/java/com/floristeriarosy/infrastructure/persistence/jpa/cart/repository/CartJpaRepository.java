package com.floristeriarosy.infrastructure.persistence.jpa.cart.repository;

import com.floristeriarosy.infrastructure.persistence.entity.cart.CartEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
