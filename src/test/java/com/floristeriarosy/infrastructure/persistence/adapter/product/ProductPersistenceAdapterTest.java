package com.floristeriarosy.infrastructure.persistence.adapter.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.application.product.dto.ProductAdminListingCriteria;
import com.floristeriarosy.application.product.dto.ProductDeletionImpact;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.exception.product.ProductAlreadyExistsException;
import com.floristeriarosy.domain.exception.product.ProductHasHistoryException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.infrastructure.persistence.adapter.category.CategoryPersistenceAdapter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the migrations against real PostgreSQL, then exercises the product adapter (product.md, ADR-009). */
@Testcontainers
@SpringBootTest
class ProductPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ProductPersistenceAdapter adapter;
  @Autowired private CategoryPersistenceAdapter categoryAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Product newProduct(String name) {
    return Product.create(
        ProductId.newId(), name, ProductSlug.generateFrom(name), "desc", new BigDecimal("9.99"), false, Map.of());
  }

  private CategoryId newCategory() {
    String name = "Cat " + UUID.randomUUID();
    Category category =
        categoryAdapter.save(Category.create(CategoryId.newId(), name, CategorySlug.generateFrom(name), null, null, 0));
    return category.id();
  }

  @Test
  void savesAndFindsByIdAndSlug() {
    Product saved = adapter.save(newProduct("Ramo IT " + UUID.randomUUID()));

    assertThat(adapter.findById(saved.id())).isPresent();
    assertThat(adapter.findBySlug(saved.slug().value())).isPresent();
    assertThat(saved.createdAt()).isNotNull();
  }

  @Test
  void rejectsADuplicateSlug() {
    Product first = newProduct("Duplicado " + UUID.randomUUID());
    Product saved = adapter.save(first);
    Product second =
        Product.create(ProductId.newId(), "otro nombre", saved.slug(), null, BigDecimal.TEN, false, Map.of());

    assertThatThrownBy(() -> adapter.save(second)).isInstanceOf(ProductAlreadyExistsException.class);
  }

  /**
   * {@code save} always re-reads the currently managed entity right before applying changes
   * (ADR-009: the domain {@code Product} itself carries no version), so a version conflict only
   * surfaces when two writes genuinely race — two threads whose internal reads interleave before
   * either commits. A single-threaded "load twice, save twice" sequence can never reproduce it,
   * since the second {@code save} would simply re-read the already-updated row.
   */
  @Test
  void rejectsAConcurrentEditOfTheSameProduct() throws Exception {
    Product saved = adapter.save(newProduct("Concurrente " + UUID.randomUUID()));
    ProductId id = saved.id();
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Boolean>> edits =
          List.of(
              () -> attemptConcurrentEdit(id, barrier, "Editado primero " + UUID.randomUUID()),
              () -> attemptConcurrentEdit(id, barrier, "Editado segundo " + UUID.randomUUID()));
      List<Future<Boolean>> results = executor.invokeAll(edits);

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
   * @param id the product both writers edit
   * @param barrier synchronizes both writers so their internal reads race
   * @param newName the name this writer sets
   * @return {@code true} if the write succeeded, {@code false} if it hit {@link
   *     ResourceModifiedException}
   */
  private boolean attemptConcurrentEdit(ProductId id, CyclicBarrier barrier, String newName) throws Exception {
    Product loaded = adapter.findById(id).orElseThrow();
    loaded.replace(newName, ProductSlug.generateFrom(newName), null, BigDecimal.TEN, false, Map.of());
    barrier.await();
    try {
      adapter.save(loaded);
      return true;
    } catch (ResourceModifiedException conflict) {
      return false;
    }
  }

  @Test
  void updatesStatusGuardedByVersion() {
    Product saved = adapter.save(newProduct("Estado " + UUID.randomUUID()));

    Product updated = adapter.updateStatus(saved.id(), ProductStatus.INACTIVE);

    assertThat(updated.status()).isEqualTo(ProductStatus.INACTIVE);
  }

  @Test
  void deletesAProductWithNoHistory() {
    Product saved = adapter.save(newProduct("Borrar " + UUID.randomUUID()));

    adapter.delete(saved.id());

    assertThat(adapter.existsById(saved.id())).isFalse();
  }

  @Test
  void rejectsDeletingAProductWithStockMovementHistory() {
    Product saved = adapter.save(newProduct("Con historial " + UUID.randomUUID()));
    jdbcTemplate.update(
        "INSERT INTO stock_movements (id, product_id, type, quantity, resulting_stock, created_at) "
            + "VALUES (?, ?, 'INITIAL', 5, 5, now())",
        UUID.randomUUID(),
        saved.id().value());

    assertThatThrownBy(() -> adapter.delete(saved.id())).isInstanceOf(ProductHasHistoryException.class);
  }

  @Test
  void reportsNoDeletionImpactForAFreshProduct() {
    Product saved = adapter.save(newProduct("Sin impacto " + UUID.randomUUID()));

    ProductDeletionImpact impact = adapter.deletionImpact(saved.id());

    assertThat(impact.deletable()).isTrue();
    assertThat(impact.blockedBy()).isEmpty();
  }

  @Test
  void isNotVisibleWithoutAnActiveCategory() {
    Product saved = adapter.save(newProduct("Sin categoria " + UUID.randomUUID()));

    assertThat(adapter.isVisible(saved.id())).isFalse();
  }

  @Test
  void isVisibleWhenActiveWithAnActiveCategory() {
    Product saved = adapter.save(newProduct("Visible " + UUID.randomUUID()));
    CategoryId categoryId = newCategory();
    jdbcTemplate.update(
        "INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)",
        saved.id().value(),
        categoryId.value());

    assertThat(adapter.isVisible(saved.id())).isTrue();
  }

  @Test
  void listsForAdminRegardlessOfStatus() {
    Product saved = adapter.save(newProduct("Admin listado " + UUID.randomUUID()));
    adapter.updateStatus(saved.id(), ProductStatus.INACTIVE);

    ProductAdminListingCriteria criteria = new ProductAdminListingCriteria(ProductStatus.INACTIVE, false, null, 0, 50);
    PageResult<ProductSummaryDto> page = adapter.findAllForAdmin(criteria);

    assertThat(page.items()).extracting("id").contains(saved.id().value());
  }

  @Test
  void existsBySlugReflectsAlreadyUsedSlugs() {
    Product saved = adapter.save(newProduct("Slug existente " + UUID.randomUUID()));

    assertThat(adapter.existsBySlug(saved.slug().value())).isTrue();
    assertThat(adapter.existsBySlug("no-existe-" + UUID.randomUUID())).isFalse();
  }
}
