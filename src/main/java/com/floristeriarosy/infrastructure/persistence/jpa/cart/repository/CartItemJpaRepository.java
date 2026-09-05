package com.floristeriarosy.infrastructure.persistence.jpa.cart.repository;

import com.floristeriarosy.infrastructure.persistence.entity.cart.CartItemEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link CartItemEntity}: writes and simple lookups (ADR-002). */
public interface CartItemJpaRepository extends JpaRepository<CartItemEntity, UUID> {

  /**
   * @param cartId the owning cart
   * @return every line of that cart
   */
  List<CartItemEntity> findByCartId(UUID cartId);

  /**
   * @param cartId the owning cart
   * @param productId the product the line refers to
   * @return the matching line, if it exists
   */
  Optional<CartItemEntity> findByCartIdAndProductId(UUID cartId, UUID productId);

  /**
   * @param cartId the owning cart
   * @param productId the product whose line to delete
   */
  void deleteByCartIdAndProductId(UUID cartId, UUID productId);

  /**
   * @param cartId the owning cart
   * @param productIds the products whose lines to delete
   */
  void deleteByCartIdAndProductIdIn(UUID cartId, Collection<UUID> productIds);

  /**
   * @param cartId the cart to empty
   */
  void deleteByCartId(UUID cartId);
}
