package com.floristeriarosy.infrastructure.persistence.adapter.discount;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.infrastructure.persistence.adapter.product.ProductPersistenceAdapter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
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
 * Runs the migrations against real PostgreSQL, then exercises the two conditional {@code UPDATE}s
 * behind {@code DiscountReservationPort} (product-discounts.md, sections 3.5, 3.6).
 */
@Testcontainers
@SpringBootTest
class DiscountReservationPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private DiscountReservationPersistenceAdapter reservationAdapter;
  @Autowired private DiscountPersistenceAdapter discountAdapter;
  @Autowired private ProductPersistenceAdapter productAdapter;

  private ProductId newProduct() {
    String name = "Producto " + UUID.randomUUID();
    Product product =
        productAdapter.save(
            Product.create(
                ProductId.newId(), name, ProductSlug.generateFrom(name), null, new BigDecimal("20.00"), false, Map.of()));
    return product.id();
  }

  private DiscountId newActiveLimitedDiscount(int quantityLimit) {
    ProductId productId = newProduct();
    Instant now = Instant.now();
    Discount saved =
        discountAdapter.save(
            Discount.create(
                DiscountId.newId(),
                productId,
                new BigDecimal("20.00"),
                new BigDecimal("15.00"),
                now.minusSeconds(3600),
                now.plusSeconds(3600),
                quantityLimit));
    return saved.id();
  }

  @Test
  void reserveSucceedsWithinTheQuantityLimit() {
    DiscountId id = newActiveLimitedDiscount(5);

    boolean reserved = reservationAdapter.reserve(id, 3);

    assertThat(reserved).isTrue();
    assertThat(discountAdapter.findById(id).orElseThrow().quantitySold()).isEqualTo(3);
  }

  @Test
  void reserveFailsWithoutAffectingAnyRowWhenItWouldExceedTheLimit() {
    DiscountId id = newActiveLimitedDiscount(5);
    reservationAdapter.reserve(id, 4);

    boolean reserved = reservationAdapter.reserve(id, 2);

    assertThat(reserved).isFalse();
    assertThat(discountAdapter.findById(id).orElseThrow().quantitySold()).isEqualTo(4);
  }

  @Test
  void releaseReturnsPreviouslyReservedUnits() {
    DiscountId id = newActiveLimitedDiscount(5);
    reservationAdapter.reserve(id, 4);

    reservationAdapter.release(id, 3);

    assertThat(discountAdapter.findById(id).orElseThrow().quantitySold()).isEqualTo(1);
  }

  @Test
  void releaseIsANoOpWhenReleasingMoreUnitsThanWereReserved() {
    DiscountId id = newActiveLimitedDiscount(5);
    reservationAdapter.reserve(id, 2);

    reservationAdapter.release(id, 3);

    assertThat(discountAdapter.findById(id).orElseThrow().quantitySold()).isEqualTo(2);
  }
}
