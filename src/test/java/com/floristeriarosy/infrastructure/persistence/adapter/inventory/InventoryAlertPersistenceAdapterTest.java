package com.floristeriarosy.infrastructure.persistence.adapter.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.inventory.dto.InventoryAlertCriteria;
import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.domain.model.inventory.InventoryAlert;
import com.floristeriarosy.domain.model.inventory.InventoryAlertStatus;
import com.floristeriarosy.domain.model.inventory.InventoryAlertType;
import com.floristeriarosy.domain.model.inventory.valueobject.InventoryAlertId;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.infrastructure.persistence.adapter.product.ProductPersistenceAdapter;
import java.math.BigDecimal;
import java.time.Instant;
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
 * Runs the migrations against real PostgreSQL, then exercises {@code InventoryAlertPersistenceAdapter}
 * — most importantly the no-duplicate-open-alert guarantee {@code ux_inventory_alerts_open} enforces
 * (inventory.md, section 3.8; ADR-013).
 */
@Testcontainers
@SpringBootTest
class InventoryAlertPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private InventoryAlertPersistenceAdapter adapter;
  @Autowired private ProductPersistenceAdapter productAdapter;

  private ProductId newProduct() {
    String name = "Producto " + UUID.randomUUID();
    Product saved =
        productAdapter.save(
            Product.create(ProductId.newId(), name, ProductSlug.generateFrom(name), null, BigDecimal.TEN, false, Map.of()));
    return saved.id();
  }

  private InventoryAlert openLowStockAlert(ProductId productId) {
    return InventoryAlert.open(InventoryAlertId.newId(), InventoryAlertType.LOW_STOCK, productId, 2, 5);
  }

  @Test
  void savingTheFirstAlertForAProductAndTypeCreatesIt() {
    ProductId productId = newProduct();

    boolean created = adapter.save(openLowStockAlert(productId));

    assertThat(created).isTrue();
  }

  /**
   * The core of ADR-013's "no duplicate open alerts": the daily job re-running while the same
   * condition persists must not create a second {@code OPEN} row for the same product and type.
   */
  @Test
  void savingASecondOpenAlertForTheSameProductAndTypeIsSkipped() {
    ProductId productId = newProduct();
    adapter.save(openLowStockAlert(productId));

    boolean createdAgain = adapter.save(openLowStockAlert(productId));

    assertThat(createdAgain).isFalse();
    PageResult<InventoryAlertDto> all =
        adapter.findAll(new InventoryAlertCriteria(InventoryAlertType.LOW_STOCK, null, productId.value(), 0, 20));
    assertThat(all.totalElements()).isEqualTo(1);
  }

  @Test
  void closingTheOpenAlertFreesTheSlotForANewOne() {
    ProductId productId = newProduct();
    InventoryAlert first = openLowStockAlert(productId);
    adapter.save(first);
    first.resolve(null, "repuesto", Instant.now());
    adapter.resolve(first);

    boolean createdAfterClosing = adapter.save(openLowStockAlert(productId));

    assertThat(createdAfterClosing).isTrue();
  }

  @Test
  void findByIdReturnsAPersistedAlert() {
    ProductId productId = newProduct();
    InventoryAlert alert = openLowStockAlert(productId);
    adapter.save(alert);

    Optional<InventoryAlert> found = adapter.findById(alert.id());

    assertThat(found).isPresent();
    assertThat(found.orElseThrow().status()).isEqualTo(InventoryAlertStatus.OPEN);
  }

  @Test
  void findByIdIsEmptyForAnUnknownId() {
    assertThat(adapter.findById(InventoryAlertId.newId())).isEmpty();
  }

  @Test
  void findOpenListsOnlyAlertsStillOpen() {
    ProductId productId = newProduct();
    InventoryAlert alert = openLowStockAlert(productId);
    adapter.save(alert);

    assertThat(adapter.findOpen()).extracting(a -> a.id().value()).contains(alert.id().value());

    alert.dismiss(null, null, Instant.now());
    adapter.dismiss(alert);

    assertThat(adapter.findOpen()).extracting(a -> a.id().value()).doesNotContain(alert.id().value());
  }

  @Test
  void findAllFiltersByStatus() {
    ProductId productId = newProduct();
    InventoryAlert alert = openLowStockAlert(productId);
    adapter.save(alert);

    PageResult<InventoryAlertDto> resolvedOnly =
        adapter.findAll(new InventoryAlertCriteria(null, InventoryAlertStatus.RESOLVED, productId.value(), 0, 20));
    PageResult<InventoryAlertDto> openOnly =
        adapter.findAll(new InventoryAlertCriteria(null, InventoryAlertStatus.OPEN, productId.value(), 0, 20));

    assertThat(resolvedOnly.totalElements()).isZero();
    assertThat(openOnly.totalElements()).isEqualTo(1);
    assertThat(openOnly.items().get(0).productName()).isNotNull();
  }

  @Test
  void resolveClosesTheAlertAndRecordsTheAdminNote() {
    ProductId productId = newProduct();
    InventoryAlert alert = openLowStockAlert(productId);
    adapter.save(alert);
    alert.resolve(null, "repuesto", Instant.now());

    InventoryAlert resolved = adapter.resolve(alert);

    assertThat(resolved.status()).isEqualTo(InventoryAlertStatus.RESOLVED);
    assertThat(resolved.note()).isEqualTo("repuesto");
    assertThat(resolved.resolvedAt()).isNotNull();
  }

  @Test
  void dismissClosesTheAlertAsAcknowledged() {
    ProductId productId = newProduct();
    InventoryAlert alert = openLowStockAlert(productId);
    adapter.save(alert);
    alert.dismiss(null, "umbral conservador", Instant.now());

    InventoryAlert dismissed = adapter.dismiss(alert);

    assertThat(dismissed.status()).isEqualTo(InventoryAlertStatus.DISMISSED);
  }
}
