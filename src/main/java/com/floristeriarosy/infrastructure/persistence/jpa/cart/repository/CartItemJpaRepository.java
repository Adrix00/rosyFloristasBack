package com.floristeriarosy.infrastructure.persistence.jpa.cart.repository;

import com.floristeriarosy.infrastructure.persistence.entity.cart.CartItemEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link CartItemEntity}: writes and simple lookups (ADR-002). */
public interface CartItemJpaRepository extends JpaRepository<CartItemEntity, UUID> {

  /**
   * Atomic upsert on {@code uq_cart_items_cart_product}: the find-then-insert this replaces raced
   * two concurrent {@code POST}s of the same product into the same cart into a duplicate-key 500,
   * because neither request's {@code findByCartIdAndProductId} saw the other's not-yet-committed
   * insert. {@code ON CONFLICT} makes the check-and-write one statement, closing the window.
   *
   * <p>{@code newId} is only used the first time this product is added to this cart — a concurrent
   * loser's own random id is simply discarded, the row keeps whichever id won.
   *
   * @param newId a freshly generated id, used only if this is a new line
   * @param cartId the owning cart
   * @param productId the product whose line to write
   * @param quantity the quantity to persist — replaces any existing value, never added to it
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO cart_items (id, cart_id, product_id, quantity, created_at, updated_at) "
              + "VALUES (:newId, :cartId, :productId, :quantity, now(), now()) "
              + "ON CONFLICT (cart_id, product_id) "
              + "DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = now()",
      nativeQuery = true)
  void upsert(
      @Param("newId") UUID newId,
      @Param("cartId") UUID cartId,
      @Param("productId") UUID productId,
      @Param("quantity") int quantity);

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
