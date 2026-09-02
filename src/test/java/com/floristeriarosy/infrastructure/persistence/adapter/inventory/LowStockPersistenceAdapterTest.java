package com.floristeriarosy.infrastructure.persistence.adapter.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.inventory.dto.LowStockCandidate;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.infrastructure.persistence.adapter.product.ProductPersistenceAdapter;
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
 * Runs the migrations against real PostgreSQL, then exercises the {@code LOW_STOCK} detection
 * query (inventory.md, section 3.8, section 10; ADR-013): every edge case in the query's own
 * {@code WHERE} clause.
 */
@Testcontainers
@SpringBootTest
class LowStockPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private LowStockPersistenceAdapter adapter;
  @Autowired private ProductPersistenceAdapter productAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ProductId newProduct() {
    String name = "Producto " + UUID.randomUUID();
    Product saved =
        productAdapter.save(
            Product.create(ProductId.newId(), name, ProductSlug.generateFrom(name), null, BigDecimal.TEN, false, Map.of()));
    return saved.id();
  }

  private void setStockAndThreshold(ProductId id, Integer stock, Integer threshold) {
    jdbcTemplate.update("UPDATE products SET stock = ?, low_stock_threshold = ? WHERE id = ?", stock, threshold, id.value());
  }

  @Test
  void includesAProductAtOrBelowItsThreshold() {
    ProductId id = newProduct();
    setStockAndThreshold(id, 5, 5);

    assertThat(adapter.findBelowThreshold()).extracting(LowStockCandidate::productId).contains(id.value());
  }

  @Test
  void excludesAProductAboveItsThreshold() {
    ProductId id = newProduct();
    setStockAndThreshold(id, 20, 5);

    assertThat(adapter.findBelowThreshold()).extracting(LowStockCandidate::productId).doesNotContain(id.value());
  }

  /** inventory.md, section 10: a threshold configured on an unmanaged product never alerts. */
  @Test
  void excludesAProductWithAThresholdButNoManagedStock() {
    ProductId id = newProduct();
    setStockAndThreshold(id, null, 5);

    assertThat(adapter.findBelowThreshold()).extracting(LowStockCandidate::productId).doesNotContain(id.value());
  }

  @Test
  void excludesAManagedProductWithNoThresholdConfigured() {
    ProductId id = newProduct();
    setStockAndThreshold(id, 1, null);

    assertThat(adapter.findBelowThreshold()).extracting(LowStockCandidate::productId).doesNotContain(id.value());
  }
}
