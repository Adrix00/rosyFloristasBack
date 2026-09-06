package com.floristeriarosy.infrastructure.persistence.adapter.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.floristeriarosy.application.cart.dto.CartCatalogEntryDto;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.infrastructure.persistence.adapter.category.CategoryPersistenceAdapter;
import com.floristeriarosy.infrastructure.persistence.adapter.discount.DiscountPersistenceAdapter;
import com.floristeriarosy.infrastructure.persistence.adapter.inventory.ProductStockPersistenceAdapter;
import com.floristeriarosy.infrastructure.persistence.adapter.product.ProductCategoryPersistenceAdapter;
import com.floristeriarosy.infrastructure.persistence.adapter.product.ProductPersistenceAdapter;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the migrations against real PostgreSQL, then exercises the cart adapter (cart.md §8, rules
 * 3.5 and 3.6).
 *
 * <p>{@code CartWritePort#touch} and {@code CartItemWritePort}'s delete methods are backed by a
 * custom {@code @Modifying} query and derived {@code deleteBy...} queries (ADR-002): neither is
 * self-transactional the way an inherited {@code JpaRepository} method is, so calling them needs
 * an explicit transaction — exactly what every real caller already provides, since all cart write
 * use cases are {@code @Transactional} services (07-transaction-conventions.md). {@link
 * #inTransaction} recreates that ambient transaction for the handful of tests that call these
 * methods directly.
 */
@Testcontainers
@SpringBootTest
class CartPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private CartPersistenceAdapter adapter;
  @Autowired private ProductPersistenceAdapter productAdapter;
  @Autowired private CategoryPersistenceAdapter categoryAdapter;
  @Autowired private ProductCategoryPersistenceAdapter productCategoryAdapter;
  @Autowired private ProductStockPersistenceAdapter stockAdapter;
  @Autowired private DiscountPersistenceAdapter discountAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  private void inTransaction(Runnable action) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
  }

  private ProductId newProduct(BigDecimal price) {
    String name = "Producto " + UUID.randomUUID();
    Product saved =
        productAdapter.save(
            Product.create(ProductId.newId(), name, ProductSlug.generateFrom(name), null, price, false, Map.of()));
    return saved.id();
  }

  private ProductId newVisibleProduct(BigDecimal price) {
    ProductId productId = newProduct(price);
    String name = "Cat " + UUID.randomUUID();
    Category category =
        categoryAdapter.save(Category.create(CategoryId.newId(), name, CategorySlug.generateFrom(name), null, null, 0));
    productCategoryAdapter.replaceCategories(productId, List.of(category.id()));
    return productId;
  }

  private UUID newRegisteredCustomer() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customers (id, type, status, email_hash, password_hash) VALUES (?, 'REGISTERED', 'ACTIVE', ?, 'hash')",
        id,
        UUID.randomUUID().toString().getBytes());
    return id;
  }

  private Cart newCart(UUID customerId) {
    return Cart.createEmpty(CartId.newId(), customerId, UUID.randomUUID().toString());
  }

  @Test
  void savesAGuestCartAndFindsItByItsSessionToken() {
    Cart cart = newCart(null);

    Cart saved = adapter.save(cart);

    assertThat(adapter.findBySessionToken(saved.sessionToken())).isPresent();
    assertThat(saved.createdAt()).isNotNull();
  }

  @Test
  void savesACustomerCartAndFindsItByCustomerId() {
    UUID customerId = newRegisteredCustomer();
    Cart cart = newCart(customerId);

    adapter.save(cart);

    Cart found = adapter.findByCustomer(customerId).orElseThrow();
    assertThat(found.customerId()).isEqualTo(customerId);
  }

  @Test
  void touchRenewsTheExpiryWithoutRewritingTheWholeRow() {
    Cart cart = adapter.save(newCart(null));
    Instant newExpiry = Instant.now().plusSeconds(999_999);

    inTransaction(() -> adapter.touch(cart.id(), newExpiry));

    Cart reloaded = adapter.findBySessionToken(cart.sessionToken()).orElseThrow();
    assertThat(reloaded.expiresAt()).isCloseTo(newExpiry, within(Duration.ofMillis(1000)));
  }

  /**
   * ADR-009 amendment (2026-09-06): two devices renewing the same cart's expiry at once — the
   * second writer must be told, not silently overwritten or, worse, silently compounded.
   */
  @Test
  void twoConcurrentTouchesOfTheSameCartRaceAndOneLoses() throws Exception {
    Cart cart = adapter.save(newCart(null));
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Boolean>> touches =
          List.of(
              () -> attemptConcurrentTouch(cart.id(), barrier, Instant.now().plusSeconds(100)),
              () -> attemptConcurrentTouch(cart.id(), barrier, Instant.now().plusSeconds(200)));
      List<Future<Boolean>> results = executor.invokeAll(touches);

      long successCount = 0;
      long conflictCount = 0;
      for (Future<Boolean> result : results) {
        if (result.get()) {
          successCount++;
        } else {
          conflictCount++;
        }
      }
      assertThat(successCount).isEqualTo(1);
      assertThat(conflictCount).isEqualTo(1);
    } finally {
      executor.shutdown();
    }
  }

  /**
   * @param id the cart both writers touch
   * @param barrier synchronizes both writers so their internal reads race
   * @param newExpiry this writer's new expiry
   * @return {@code true} if the write succeeded, {@code false} if it hit {@link
   *     ResourceModifiedException}
   */
  private boolean attemptConcurrentTouch(CartId id, CyclicBarrier barrier, Instant newExpiry) throws Exception {
    barrier.await();
    try {
      inTransaction(() -> adapter.touch(id, newExpiry));
      return true;
    } catch (ResourceModifiedException conflict) {
      return false;
    }
  }

  @Test
  void deletingACartCascadesToItsLinesUsedByTheLoginMergeOfRule32() {
    Cart cart = adapter.save(newCart(null));
    ProductId productId = newVisibleProduct(new BigDecimal("5.00"));
    inTransaction(() -> adapter.save(cart.id(), productId, 2));

    adapter.delete(cart.id());

    assertThat(adapter.findBySessionToken(cart.sessionToken())).isEmpty();
    Integer remainingItems =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", Integer.class, cart.id().value());
    assertThat(remainingItems).isZero();
  }

  @Test
  void itemWriteSaveUpsertsInsertingThenUpdatingTheSameLine() {
    Cart cart = adapter.save(newCart(null));
    ProductId productId = newVisibleProduct(new BigDecimal("5.00"));

    inTransaction(() -> adapter.save(cart.id(), productId, 2));
    inTransaction(() -> adapter.save(cart.id(), productId, 7));

    Cart reloaded = adapter.findBySessionToken(cart.sessionToken()).orElseThrow();
    assertThat(reloaded.items()).containsEntry(productId, 7);
  }

  /**
   * cart #2: two concurrent {@code POST}s of the same product into the same cart used to race a
   * find-then-insert — neither request's read saw the other's not-yet-committed insert, so both
   * attempted an insert and the loser hit {@code uq_cart_items_cart_product} as an untranslated
   * 500. The {@code ON CONFLICT} upsert makes the check-and-write one statement: both requests now
   * succeed, one row survives, and it holds whichever quantity committed last.
   */
  @Test
  void itemWriteSaveIsAtomicUnderConcurrentUpsertsOfTheSameLine() throws Exception {
    Cart cart = adapter.save(newCart(null));
    ProductId productId = newVisibleProduct(new BigDecimal("5.00"));
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Void>> writes =
          List.of(
              () -> concurrentUpsert(cart.id(), productId, 3, barrier),
              () -> concurrentUpsert(cart.id(), productId, 9, barrier));
      List<Future<Void>> results = executor.invokeAll(writes);
      for (Future<Void> result : results) {
        result.get(); // rethrows if either writer saw an exception instead of a clean upsert
      }
    } finally {
      executor.shutdown();
    }

    Integer rowCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cart_items WHERE cart_id = ? AND product_id = ?",
            Integer.class,
            cart.id().value(),
            productId.value());
    assertThat(rowCount).isEqualTo(1);
    Cart reloaded = adapter.findBySessionToken(cart.sessionToken()).orElseThrow();
    assertThat(reloaded.items().get(productId)).isIn(3, 9);
  }

  /**
   * @param cartId the cart both writers upsert into
   * @param productId the product both writers upsert
   * @param quantity this writer's quantity
   * @param barrier synchronizes both writers so their upserts race
   */
  private Void concurrentUpsert(CartId cartId, ProductId productId, int quantity, CyclicBarrier barrier)
      throws Exception {
    barrier.await();
    inTransaction(() -> adapter.save(cartId, productId, quantity));
    return null;
  }

  @Test
  void itemWriteDeleteRemovesOnlyTheGivenLine() {
    Cart cart = adapter.save(newCart(null));
    ProductId keep = newVisibleProduct(new BigDecimal("5.00"));
    ProductId drop = newVisibleProduct(new BigDecimal("5.00"));
    inTransaction(() -> adapter.save(cart.id(), keep, 1));
    inTransaction(() -> adapter.save(cart.id(), drop, 1));

    inTransaction(() -> adapter.delete(cart.id(), drop));

    Cart reloaded = adapter.findBySessionToken(cart.sessionToken()).orElseThrow();
    assertThat(reloaded.items()).containsOnlyKeys(keep);
  }

  @Test
  void itemWriteDeleteAllEmptiesTheWholeCart() {
    Cart cart = adapter.save(newCart(null));
    ProductId productId = newVisibleProduct(new BigDecimal("5.00"));
    inTransaction(() -> adapter.save(cart.id(), productId, 1));

    inTransaction(() -> adapter.deleteAll(cart.id()));

    Cart reloaded = adapter.findBySessionToken(cart.sessionToken()).orElseThrow();
    assertThat(reloaded.items()).isEmpty();
  }

  @Test
  void itemWriteDeleteAllWithASubsetRemovesOnlyThoseLines() {
    Cart cart = adapter.save(newCart(null));
    ProductId keep = newVisibleProduct(new BigDecimal("5.00"));
    ProductId drop = newVisibleProduct(new BigDecimal("5.00"));
    inTransaction(() -> adapter.save(cart.id(), keep, 1));
    inTransaction(() -> adapter.save(cart.id(), drop, 1));

    inTransaction(() -> adapter.deleteAll(cart.id(), Set.of(drop)));

    Cart reloaded = adapter.findBySessionToken(cart.sessionToken()).orElseThrow();
    assertThat(reloaded.items()).containsOnlyKeys(keep);
  }

  @Test
  void catalogEntriesForReflectsARealActiveDiscountAsTheEffectivePrice() {
    ProductId productId = newVisibleProduct(new BigDecimal("20.00"));
    Instant now = Instant.now();
    discountAdapter.save(
        Discount.create(
            DiscountId.newId(),
            productId,
            new BigDecimal("20.00"),
            new BigDecimal("15.00"),
            now.minusSeconds(3600),
            now.plusSeconds(3600),
            null));

    List<CartCatalogEntryDto> entries = adapter.catalogEntriesFor(Set.of(productId));

    CartCatalogEntryDto entry = entries.get(0);
    assertThat(entry.effectivePrice()).isEqualByComparingTo("15.00");
    assertThat(entry.unitPrice()).isEqualByComparingTo("20.00");
    assertThat(entry.onSale()).isTrue();
  }

  @Test
  void catalogEntriesForReturnsTheBasePriceWhenThereIsNoActiveDiscount() {
    ProductId productId = newVisibleProduct(new BigDecimal("20.00"));

    List<CartCatalogEntryDto> entries = adapter.catalogEntriesFor(Set.of(productId));

    CartCatalogEntryDto entry = entries.get(0);
    assertThat(entry.effectivePrice()).isEqualByComparingTo("20.00");
    assertThat(entry.onSale()).isFalse();
  }

  @Test
  void isVisibleIsTrueOnlyForAnActiveProductWithAtLeastOneActiveCategory() {
    ProductId visible = newVisibleProduct(new BigDecimal("5.00"));
    ProductId withoutCategory = newProduct(new BigDecimal("5.00"));

    assertThat(adapter.isVisible(visible)).isTrue();
    assertThat(adapter.isVisible(withoutCategory)).isFalse();
  }

  @Test
  void isVisibleIsFalseForAnInactiveProductEvenWithAnActiveCategory() {
    ProductId productId = newVisibleProduct(new BigDecimal("5.00"));
    productAdapter.updateStatus(productId, ProductStatus.INACTIVE);

    assertThat(adapter.isVisible(productId)).isFalse();
  }

  @Test
  void availableStockIsEmptyForAProductWithNoManagedInventory() {
    ProductId productId = newVisibleProduct(new BigDecimal("5.00"));

    Optional<Integer> stock = adapter.availableStock(productId);

    assertThat(stock).isEmpty();
  }

  @Test
  void availableStockReturnsTheManagedStockWhenItIsSet() {
    ProductId productId = newVisibleProduct(new BigDecimal("5.00"));
    stockAdapter.setInitial(productId, 12);

    Optional<Integer> stock = adapter.availableStock(productId);

    assertThat(stock).contains(12);
  }
}
