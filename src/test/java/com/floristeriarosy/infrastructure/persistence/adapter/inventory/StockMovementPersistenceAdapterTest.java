package com.floristeriarosy.infrastructure.persistence.adapter.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.application.inventory.dto.ReconciliationMismatch;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.domain.exception.inventory.InventoryAlreadyInitializedException;
import com.floristeriarosy.domain.model.inventory.StockMovement;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import com.floristeriarosy.domain.model.inventory.valueobject.StockMovementId;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.infrastructure.persistence.adapter.product.ProductPersistenceAdapter;
import java.math.BigDecimal;
import java.util.List;
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
 * Runs the migrations against real PostgreSQL, then exercises {@code StockMovementPersistenceAdapter}
 * (inventory.md, section 1, section 3.2, section 3.8).
 */
@Testcontainers
@SpringBootTest
class StockMovementPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private StockMovementPersistenceAdapter adapter;
  @Autowired private ProductPersistenceAdapter productAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ProductId newProduct() {
    String name = "Producto " + UUID.randomUUID();
    Product saved =
        productAdapter.save(
            Product.create(ProductId.newId(), name, ProductSlug.generateFrom(name), null, BigDecimal.TEN, false, Map.of()));
    return saved.id();
  }

  private void setStock(ProductId id, int stock) {
    jdbcTemplate.update("UPDATE products SET stock = ? WHERE id = ?", stock, id.value());
  }

  private StockMovement movement(ProductId productId, StockMovementType type, int quantity, int resultingStock) {
    return StockMovement.create(StockMovementId.newId(), productId, type, quantity, resultingStock, null, "nota");
  }

  @Test
  void saveInsertsTheExactResultingStockPassedIn() {
    ProductId productId = newProduct();
    setStock(productId, 10);

    StockMovement saved = adapter.save(movement(productId, StockMovementType.SALE, -3, 7));

    assertThat(saved.id()).isNotNull();
    assertThat(saved.resultingStock()).isEqualTo(7);
    assertThat(saved.createdAt()).isNotNull();
  }

  @Test
  void aSecondInitialForTheSameProductIsRejectedByTheUniqueIndex() {
    ProductId productId = newProduct();
    setStock(productId, 10);
    adapter.save(movement(productId, StockMovementType.INITIAL, 10, 10));

    assertThatThrownBy(() -> adapter.save(movement(productId, StockMovementType.INITIAL, 5, 5)))
        .isInstanceOf(InventoryAlreadyInitializedException.class);
  }

  @Test
  void findByProductListsMostRecentFirstAndCountsTheTotal() {
    ProductId productId = newProduct();
    setStock(productId, 10);
    adapter.save(movement(productId, StockMovementType.INITIAL, 10, 10));
    adapter.save(movement(productId, StockMovementType.SALE, -2, 8));

    PageResult<StockMovementDto> page = adapter.findByProduct(productId, 0, 20);

    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.items())
        .extracting(StockMovementDto::type)
        .containsExactly(StockMovementType.SALE, StockMovementType.INITIAL);
  }

  @Test
  void findReconciliationMismatchesDetectsAProductWhoseStockDisagreesWithItsMovements() {
    ProductId productId = newProduct();
    setStock(productId, 10);
    adapter.save(movement(productId, StockMovementType.INITIAL, 10, 10));
    // Simulates a write that bypassed RegisterStockMovementService (inventory.md, section 3.8):
    // products.stock changes without a matching stock_movements row.
    setStock(productId, 999);

    List<ReconciliationMismatch> mismatches = adapter.findReconciliationMismatches();
    ReconciliationMismatch mismatch =
        mismatches.stream().filter(candidate -> candidate.productId().equals(productId.value())).findFirst().orElseThrow();

    assertThat(mismatch.observedStock()).isEqualTo(999);
    assertThat(mismatch.expectedStock()).isEqualTo(10);
  }

  @Test
  void findReconciliationMismatchesIsEmptyWhenStockMatchesItsMovements() {
    ProductId productId = newProduct();
    setStock(productId, 10);
    adapter.save(movement(productId, StockMovementType.INITIAL, 10, 10));

    List<ReconciliationMismatch> mismatches = adapter.findReconciliationMismatches();

    assertThat(mismatches).noneMatch(mismatch -> mismatch.productId().equals(productId.value()));
  }
}
