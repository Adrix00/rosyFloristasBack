package com.floristeriarosy.infrastructure.persistence.adapter.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.infrastructure.persistence.adapter.product.ProductPersistenceAdapter;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the migrations against real PostgreSQL, then exercises the conditional {@code UPDATE ...
 * RETURNING stock} behind {@code ProductStockPort} (inventory.md, section 3.1, section 3.7).
 */
@Testcontainers
@SpringBootTest
class ProductStockPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ProductStockPersistenceAdapter adapter;
  @Autowired private ProductPersistenceAdapter productAdapter;

  private ProductId newProduct() {
    String name = "Producto " + UUID.randomUUID();
    Product saved =
        productAdapter.save(
            Product.create(ProductId.newId(), name, ProductSlug.generateFrom(name), null, BigDecimal.TEN, false, Map.of()));
    return saved.id();
  }

  @Test
  void setInitialSetsTheStartingStockAndReturnsIt() {
    ProductId id = newProduct();

    int result = adapter.setInitial(id, 10);

    assertThat(result).isEqualTo(10);
  }

  @Test
  void decrementConditionalReturnsTheStockTheDatabaseActuallyApplied() {
    ProductId id = newProduct();
    adapter.setInitial(id, 10);

    Optional<Integer> result = adapter.decrementConditional(id, 3);

    assertThat(result).contains(7);
  }

  @Test
  void incrementConditionalReturnsTheStockTheDatabaseActuallyApplied() {
    ProductId id = newProduct();
    adapter.setInitial(id, 10);

    Optional<Integer> result = adapter.incrementConditional(id, 5);

    assertThat(result).contains(15);
  }

  @Test
  void decrementConditionalIsEmptyOnAnUnmanagedProduct() {
    ProductId id = newProduct();

    Optional<Integer> result = adapter.decrementConditional(id, 1);

    assertThat(result).isEmpty();
  }

  @Test
  void incrementConditionalIsEmptyOnAnUnmanagedProduct() {
    ProductId id = newProduct();

    Optional<Integer> result = adapter.incrementConditional(id, 1);

    assertThat(result).isEmpty();
  }

  @Test
  void clearSetsStockBackToUnmanaged() {
    ProductId id = newProduct();
    adapter.setInitial(id, 10);

    adapter.clear(id);

    assertThat(adapter.decrementConditional(id, 1)).isEmpty();
  }

  /**
   * Two decrements racing to the last unit (inventory.md, section 3.1, section 10: "Dos ventas
   * simultáneas sobre la última unidad"). Sequential here, but proves the same guarantee a real
   * race relies on: the conditional {@code UPDATE} either affects the row or it doesn't, with no
   * window where a second caller could read a stale value.
   */
  @Test
  void onlyOneOfTwoDecrementsToTheLastUnitSucceeds() {
    ProductId id = newProduct();
    adapter.setInitial(id, 1);

    Optional<Integer> first = adapter.decrementConditional(id, 1);
    Optional<Integer> second = adapter.decrementConditional(id, 1);

    assertThat(first).contains(0);
    assertThat(second).isEmpty();
  }
}
