package com.floristeriarosy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.application.discount.command.CreateDiscountCommand;
import com.floristeriarosy.application.product.command.ChangeInventoryModeCommand;
import com.floristeriarosy.application.discount.port.in.CreateDiscountUseCase;
import com.floristeriarosy.application.product.command.DeleteProductCommand;
import com.floristeriarosy.application.product.port.in.ChangeInventoryModeUseCase;
import com.floristeriarosy.application.product.port.in.DeleteProductUseCase;
import com.floristeriarosy.domain.exception.discount.DiscountOverlapException;
import com.floristeriarosy.domain.exception.product.ProductHasHistoryException;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.infrastructure.persistence.adapter.product.ProductPersistenceAdapter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The constraint violations the specs promise as 4xx, exercised through the {@code @Transactional}
 * services rather than through the persistence adapters.
 *
 * <p>This distinction is the whole point of the class. A persistence adapter called directly runs
 * inside {@code SimpleJpaRepository}'s own transaction, so Hibernate flushes — and the adapter's
 * {@code catch} translates — before the call returns. Under a service, the transaction is the
 * service's, and a plain {@code save} defers the {@code INSERT} to a commit that happens after the
 * {@code catch} has already returned: the translation never runs and the client gets a 500. Only a
 * test that enters through the service can tell the two apart, which is why the adapter tests were
 * green while {@code POST /products/{id}/discounts} answered 500 on an overlap.
 *
 * <p>Inventory reactivation is here for the same reason, plus one of its own: {@code
 * ux_stock_movements_initial} is a constraint the old code deliberately provoked as control flow,
 * which aborts the enclosing PostgreSQL transaction — invisible to an adapter test that has no
 * enclosing transaction to abort.
 */
@Testcontainers
@SpringBootTest
class TransactionalConstraintTranslationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private CreateDiscountUseCase createDiscountUseCase;
  @Autowired private DeleteProductUseCase deleteProductUseCase;
  @Autowired private ChangeInventoryModeUseCase changeInventoryModeUseCase;
  @Autowired private ProductPersistenceAdapter productAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Product givenProduct() {
    String name = "Producto TX " + UUID.randomUUID();
    return productAdapter.save(
        Product.create(
            ProductId.newId(),
            name,
            ProductSlug.generateFrom(name),
            "desc",
            new BigDecimal("20.00"),
            false,
            Map.of()));
  }

  private Integer stockOf(UUID productId) {
    return jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
  }

  private Integer movementsTotalOf(UUID productId) {
    return jdbcTemplate.queryForObject(
        "SELECT COALESCE(SUM(quantity), 0) FROM stock_movements WHERE product_id = ?", Integer.class, productId);
  }

  private Integer initialMovementCount(UUID productId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM stock_movements WHERE product_id = ? AND type = 'INITIAL'", Integer.class, productId);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void anOverlappingDiscountIsA409NotA500() {
    Product product = givenProduct();
    Instant startsAt = Instant.now().plus(1, ChronoUnit.DAYS);
    Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
    createDiscountUseCase.execute(
        new CreateDiscountCommand(product.id().value(), new BigDecimal("15.00"), startsAt, endsAt, null));

    assertThatThrownBy(
            () ->
                createDiscountUseCase.execute(
                    new CreateDiscountCommand(
                        product.id().value(),
                        new BigDecimal("14.00"),
                        startsAt.plus(1, ChronoUnit.DAYS),
                        endsAt.plus(1, ChronoUnit.DAYS),
                        null)))
        .isInstanceOf(DiscountOverlapException.class);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void deletingAProductWithHistoryIsA409ThatNamesTheReason() {
    Product product = givenProduct();
    jdbcTemplate.update(
        "INSERT INTO stock_movements (id, product_id, type, quantity, resulting_stock, created_at) "
            + "VALUES (?, ?, 'INITIAL', 5, 5, now())",
        UUID.randomUUID(),
        product.id().value());

    assertThatThrownBy(() -> deleteProductUseCase.execute(new DeleteProductCommand(product.id().value())))
        .isInstanceOf(ProductHasHistoryException.class)
        .hasMessageContaining("STOCK_MOVEMENTS");
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void reactivatingInventoryOnAPreviouslyManagedProductSucceeds() {
    Product product = givenProduct();
    UUID id = product.id().value();
    changeInventoryModeUseCase.execute(new ChangeInventoryModeCommand(id, true, 10, null, null));
    changeInventoryModeUseCase.execute(new ChangeInventoryModeCommand(id, false, null, null, null));

    changeInventoryModeUseCase.execute(new ChangeInventoryModeCommand(id, true, 7, null, "reactivacion"));

    assertThat(stockOf(id)).isEqualTo(7);
    assertThat(initialMovementCount(id)).isEqualTo(1);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void reactivatingInventoryKeepsStockEqualToTheSumOfItsMovements() {
    Product product = givenProduct();
    UUID id = product.id().value();
    changeInventoryModeUseCase.execute(new ChangeInventoryModeCommand(id, true, 10, null, null));
    changeInventoryModeUseCase.execute(new ChangeInventoryModeCommand(id, false, null, null, null));

    changeInventoryModeUseCase.execute(new ChangeInventoryModeCommand(id, true, 7, null, "reactivacion"));

    // The invariant the daily RECONCILIATION_MISMATCH job checks (inventory.md, section 3.8).
    // Writing the absolute stock as the ADJUSTMENT quantity gives 10 + 7 = 17 against a stock of 7,
    // and a false alert every day thereafter.
    assertThat(movementsTotalOf(id)).isEqualTo(stockOf(id));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void deletingAProductWithoutHistorySucceeds() {
    Product product = givenProduct();

    deleteProductUseCase.execute(new DeleteProductCommand(product.id().value()));

    assertThat(productAdapter.existsById(product.id())).isFalse();
  }
}
