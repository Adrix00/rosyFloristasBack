package com.floristeriarosy.infrastructure.persistence.adapter.cart;

import com.floristeriarosy.application.cart.dto.CartCatalogEntryDto;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartProductAvailabilityPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.entity.cart.CartEntity;
import com.floristeriarosy.infrastructure.persistence.entity.cart.CartItemEntity;
import com.floristeriarosy.infrastructure.persistence.jdbc.cart.repository.CartProjectionJdbcRepository;
import com.floristeriarosy.infrastructure.persistence.jpa.cart.repository.CartItemJpaRepository;
import com.floristeriarosy.infrastructure.persistence.jpa.cart.repository.CartJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.cart.CartPersistenceMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/**
 * Implements every cart output port (ADR-003): JPA for writes and simple lookups, JDBC for the
 * batch pricing/display projection and the single-product availability checks (ADR-002). One
 * adapter for all five ports, same pattern as {@code CategoryPersistenceAdapter}.
 */
@Repository
public class CartPersistenceAdapter
    implements CartReadPort, CartWritePort, CartItemWritePort, CartPricingPort, CartProductAvailabilityPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(CartPersistenceAdapter.class);

  private final CartJpaRepository cartJpaRepository;
  private final CartItemJpaRepository cartItemJpaRepository;
  private final CartProjectionJdbcRepository projectionJdbcRepository;
  private final CartPersistenceMapper mapper;

  /**
   * @param cartJpaRepository writes and simple lookups on the {@code carts} row
   * @param cartItemJpaRepository writes and simple lookups on individual {@code cart_items} rows
   * @param projectionJdbcRepository the batch pricing/display projection and availability checks
   * @param mapper converts between the domain {@link Cart} and its JPA entities
   */
  public CartPersistenceAdapter(
      CartJpaRepository cartJpaRepository,
      CartItemJpaRepository cartItemJpaRepository,
      CartProjectionJdbcRepository projectionJdbcRepository,
      CartPersistenceMapper mapper) {
    this.cartJpaRepository = cartJpaRepository;
    this.cartItemJpaRepository = cartItemJpaRepository;
    this.projectionJdbcRepository = projectionJdbcRepository;
    this.mapper = mapper;
  }

  /**
   * @param customerId the owning customer
   * @return the customer's cart, if one exists
   */
  @Override
  public Optional<Cart> findByCustomer(UUID customerId) {
    LOGGER.debug("findByCustomer customerId={}", customerId);
    Optional<Cart> result = cartJpaRepository.findByCustomerId(customerId).map(this::toDomainWithItems);
    LOGGER.debug("findByCustomer customerId={} -> found={}", customerId, result.isPresent());
    return result;
  }

  /**
   * @param sessionToken the cart's session token cookie value
   * @return the matching cart, if one exists
   */
  @Override
  public Optional<Cart> findBySessionToken(String sessionToken) {
    LOGGER.debug("findBySessionToken");
    Optional<Cart> result = cartJpaRepository.findBySessionToken(sessionToken).map(this::toDomainWithItems);
    LOGGER.debug("findBySessionToken -> found={}", result.isPresent());
    return result;
  }

  /**
   * Updates the managed entity in place when {@code cart.id()} already exists, so the {@code
   * @Version} Hibernate loaded is what gets checked (ADR-009 amendment) — a freshly built detached
   * entity would always carry {@code version = 0} and be mistaken for a new row. Builds a fresh
   * entity only for a genuinely new cart. Flushes inside the {@code try} for the reason {@code
   * ProductPersistenceAdapter.save} documents: the id is application-assigned, so Hibernate would
   * otherwise defer the check to the enclosing transaction's commit, after this method's {@code
   * catch} has already returned.
   *
   * @param cart the cart to insert or update
   * @return the saved cart, with its lines reloaded (a save never touches them, see {@link
   *     CartItemWritePort})
   * @throws ResourceModifiedException the cart was changed concurrently
   */
  @Override
  public Cart save(Cart cart) {
    LOGGER.debug("save id={}", cart.id());
    CartEntity entity = cartJpaRepository.findById(cart.id().value()).orElse(null);
    if (entity != null) {
      entity.applyChanges(cart.customerId(), cart.expiresAt());
    } else {
      entity = mapper.toEntity(cart);
    }
    try {
      Cart result = toDomainWithItems(cartJpaRepository.saveAndFlush(entity));
      LOGGER.debug("save id={} -> saved", result.id());
      return result;
    } catch (ObjectOptimisticLockingFailureException conflict) {
      throw new ResourceModifiedException("Cart " + cart.id() + " was modified concurrently");
    }
  }

  /**
   * Goes through the managed entity rather than a bare {@code UPDATE}, so this renewal
   * participates in the {@code @Version} check (ADR-009 amendment) like {@link #save(Cart)} does —
   * a raw JPQL {@code UPDATE} would bypass it entirely.
   *
   * @param id the cart to renew
   * @param expiresAt the new expiry, already computed by the domain
   * @throws ResourceModifiedException the cart was changed concurrently
   */
  @Override
  public void touch(CartId id, Instant expiresAt) {
    LOGGER.debug("touch id={} expiresAt={}", id, expiresAt);
    CartEntity entity =
        cartJpaRepository
            .findById(id.value())
            .orElseThrow(() -> new IllegalStateException("Cart " + id + " not found"));
    entity.renewExpiry(expiresAt);
    try {
      cartJpaRepository.saveAndFlush(entity);
    } catch (ObjectOptimisticLockingFailureException conflict) {
      throw new ResourceModifiedException("Cart " + id + " was modified concurrently");
    }
    LOGGER.debug("touch id={} -> touched", id);
  }

  /**
   * @param id the cart to delete; {@code cart_items} rows cascade
   */
  @Override
  public void delete(CartId id) {
    LOGGER.debug("delete id={}", id);
    cartJpaRepository.deleteById(id.value());
    LOGGER.debug("delete id={} -> deleted", id);
  }

  /**
   * An atomic {@code INSERT ... ON CONFLICT DO UPDATE}, not a find-then-insert: two concurrent
   * {@code POST}s of the same product into the same cart would otherwise both see no existing row
   * and both attempt an insert, one of them hitting {@code uq_cart_items_cart_product} as an
   * untranslated 500.
   *
   * @param cartId the owning cart
   * @param productId the product whose line to write
   * @param quantity the quantity to persist
   */
  @Override
  public void save(CartId cartId, ProductId productId, int quantity) {
    LOGGER.debug("save cartId={} productId={} quantity={}", cartId, productId, quantity);
    cartItemJpaRepository.upsert(UUID.randomUUID(), cartId.value(), productId.value(), quantity);
    LOGGER.debug("save cartId={} productId={} -> saved", cartId, productId);
  }

  /**
   * @param cartId the owning cart
   * @param productId the product whose line to delete
   */
  @Override
  public void delete(CartId cartId, ProductId productId) {
    LOGGER.debug("delete cartId={} productId={}", cartId, productId);
    cartItemJpaRepository.deleteByCartIdAndProductId(cartId.value(), productId.value());
    LOGGER.debug("delete cartId={} productId={} -> deleted", cartId, productId);
  }

  /**
   * @param cartId the cart to empty
   */
  @Override
  public void deleteAll(CartId cartId) {
    LOGGER.debug("deleteAll cartId={}", cartId);
    cartItemJpaRepository.deleteByCartId(cartId.value());
    LOGGER.debug("deleteAll cartId={} -> emptied", cartId);
  }

  /**
   * @param cartId the owning cart
   * @param productIds the products whose lines to delete
   */
  @Override
  public void deleteAll(CartId cartId, Set<ProductId> productIds) {
    LOGGER.debug("deleteAll cartId={} count={}", cartId, productIds.size());
    List<UUID> rawIds = productIds.stream().map(ProductId::value).toList();
    cartItemJpaRepository.deleteByCartIdAndProductIdIn(cartId.value(), rawIds);
    LOGGER.debug("deleteAll cartId={} -> {} lines removed", cartId, rawIds.size());
  }

  /**
   * @param productIds the products to look up
   * @return one entry per found product, with its display data
   */
  @Override
  public List<CartCatalogEntryDto> catalogEntriesFor(Set<ProductId> productIds) {
    LOGGER.debug("catalogEntriesFor count={}", productIds.size());
    Set<UUID> rawIds = productIds.stream().map(ProductId::value).collect(Collectors.toSet());
    List<CartCatalogEntryDto> result = projectionJdbcRepository.findCatalogEntries(rawIds);
    LOGGER.debug("catalogEntriesFor -> count={}", result.size());
    return result;
  }

  /**
   * @param id the product to check
   * @return whether it is visible in the storefront sense (product.md §3.3)
   */
  @Override
  public boolean isVisible(ProductId id) {
    LOGGER.debug("isVisible id={}", id);
    boolean result = projectionJdbcRepository.isVisible(id.value());
    LOGGER.debug("isVisible id={} -> {}", id, result);
    return result;
  }

  /**
   * @param id the product to check
   * @return the current managed stock, or empty if inventory is unmanaged
   */
  @Override
  public Optional<Integer> availableStock(ProductId id) {
    LOGGER.debug("availableStock id={}", id);
    Optional<Integer> result = projectionJdbcRepository.availableStock(id.value());
    LOGGER.debug("availableStock id={} -> {}", id, result.orElse(null));
    return result;
  }

  /**
   * @param entity the persisted {@code carts} row
   * @return the rebuilt domain cart, with its lines loaded
   */
  private Cart toDomainWithItems(CartEntity entity) {
    List<CartItemEntity> items = cartItemJpaRepository.findByCartId(entity.getId());
    return mapper.toDomain(entity, items);
  }
}
