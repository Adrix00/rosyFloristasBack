package com.floristeriarosy.infrastructure.persistence.adapter.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the migrations against real PostgreSQL, then exercises the product inventory adapter
 * (product.md, section 3.7) — including the {@code ux_stock_movements_initial} fallback to
 * {@code ADJUSTMENT} on a second activation.
 */
@Testcontainers
@SpringBootTest
class ProductInventoryPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ProductInventoryPersistenceAdapter adapter;
  @Autowired private ProductPersistenceAdapter productAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ProductId newProduct() {
    String name = "Producto " + UUID.randomUUID();
    Product saved =
        productAdapter.save(
            Product.create(ProductId.newId(), name, ProductSlug.generateFrom(name), null, BigDecimal.TEN, false, Map.of()));
    return saved.id();
  }

  private Integer currentStock(ProductId id) {
    return jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, id.value());
  }

  private int movementCount(ProductId id, String type) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM stock_movements WHERE product_id = ? AND type = ?",
            Integer.class,
            id.value(),
            type);
    return count == null ? 0 : count;
  }

  @Test
  void initializeStockSetsStockAndWritesAnInitialMovement() {
    ProductId id = newProduct();

    adapter.initializeStock(id, 10, 5, "primera carga");

    assertThat(currentStock(id)).isEqualTo(10);
    assertThat(movementCount(id, "INITIAL")).isEqualTo(1);
  }

  @Test
  void adjustStockRecordsTheDeltaAsAnAdjustment() {
    ProductId id = newProduct();
    adapter.initializeStock(id, 10, null, null);

    adapter.adjustStock(id, 15, null, "reposicion");

    assertThat(currentStock(id)).isEqualTo(15);
    assertThat(movementCount(id, "ADJUSTMENT")).isEqualTo(1);
  }

  @Test
  void adjustStockWithNoChangeWritesNoMovementRow() {
    ProductId id = newProduct();
    adapter.initializeStock(id, 10, null, null);

    adapter.adjustStock(id, 10, 3, null);

    assertThat(currentStock(id)).isEqualTo(10);
    assertThat(movementCount(id, "ADJUSTMENT")).isZero();
  }

  @Test
  void disableStockManagementClearsStockButKeepsHistory() {
    ProductId id = newProduct();
    adapter.initializeStock(id, 10, null, null);

    adapter.disableStockManagement(id);

    assertThat(currentStock(id)).isNull();
    assertThat(movementCount(id, "INITIAL")).isEqualTo(1);
  }

  @Test
  void reactivatingAPreviouslyManagedProductWritesAnAdjustmentNotASecondInitial() {
    ProductId id = newProduct();
    adapter.initializeStock(id, 10, null, null);
    adapter.disableStockManagement(id);

    adapter.initializeStock(id, 7, null, "reactivacion");

    assertThat(currentStock(id)).isEqualTo(7);
    assertThat(movementCount(id, "INITIAL")).isEqualTo(1);
    assertThat(movementCount(id, "ADJUSTMENT")).isEqualTo(1);
  }
}
