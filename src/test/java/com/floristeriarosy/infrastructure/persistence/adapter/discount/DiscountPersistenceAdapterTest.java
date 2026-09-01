package com.floristeriarosy.infrastructure.persistence.adapter.discount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.discount.DiscountOverlapException;
import com.floristeriarosy.domain.exception.discount.DiscountPeriodInvalidException;
import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.DiscountState;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.infrastructure.persistence.adapter.product.ProductPersistenceAdapter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
 * Runs the migrations against real PostgreSQL, then exercises the discount adapter
 * (product-discounts.md, sections 2, 3.1, 3.4, 8, 9).
 */
@Testcontainers
@SpringBootTest
class DiscountPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private DiscountPersistenceAdapter adapter;
  @Autowired private ProductPersistenceAdapter productAdapter;

  private ProductId newProduct() {
    String name = "Producto " + UUID.randomUUID();
    Product product =
        productAdapter.save(
            Product.create(
                ProductId.newId(), name, ProductSlug.generateFrom(name), null, new BigDecimal("20.00"), false, Map.of()));
    return product.id();
  }

  private Discount newDiscount(ProductId productId, Instant startsAt, Instant endsAt) {
    return Discount.create(
        DiscountId.newId(), productId, new BigDecimal("20.00"), new BigDecimal("15.00"), startsAt, endsAt, null);
  }

  @Test
  void savesAndFindsDiscountById() {
    ProductId productId = newProduct();
    Instant now = Instant.now();

    Discount saved = adapter.save(newDiscount(productId, now.plusSeconds(3600), now.plusSeconds(7200)));

    assertThat(adapter.findById(saved.id())).isPresent();
    assertThat(saved.createdAt()).isNotNull();
    assertThat(saved.state()).isEqualTo(DiscountState.SCHEDULED);
  }

  @Test
  void findsTheCompleteHistoryOfAProductMostRecentFirst() {
    ProductId productId = newProduct();
    Instant now = Instant.now();
    Discount older = adapter.save(newDiscount(productId, now.minusSeconds(20000), now.minusSeconds(15000)));
    Discount newer = adapter.save(newDiscount(productId, now.minusSeconds(3600), now.plusSeconds(3600)));

    List<Discount> history = adapter.findByProduct(productId);

    assertThat(history).extracting(discount -> discount.id().value()).containsExactly(
        newer.id().value(), older.id().value());
  }

  @Test
  void findsTheCurrentlyActiveDiscountForAProduct() {
    ProductId productId = newProduct();
    Instant now = Instant.now();
    Discount active = adapter.save(newDiscount(productId, now.minusSeconds(3600), now.plusSeconds(3600)));

    assertThat(adapter.findActiveForProduct(productId)).map(discount -> discount.id().value()).contains(active.id().value());
  }

  @Test
  void findActiveForProductIsEmptyWhenTheOnlyDiscountIsStillScheduled() {
    ProductId productId = newProduct();
    Instant now = Instant.now();
    adapter.save(newDiscount(productId, now.plusSeconds(3600), now.plusSeconds(7200)));

    assertThat(adapter.findActiveForProduct(productId)).isEmpty();
  }

  @Test
  void rejectsAnOverlappingDiscountForTheSameProduct() {
    ProductId productId = newProduct();
    Instant now = Instant.now();
    adapter.save(newDiscount(productId, now.plusSeconds(3600), now.plusSeconds(10800)));
    Discount overlapping = newDiscount(productId, now.plusSeconds(7200), now.plusSeconds(14400));

    assertThatThrownBy(() -> adapter.save(overlapping)).isInstanceOf(DiscountOverlapException.class);
  }

  @Test
  void acceptsTwoConsecutiveNonOverlappingDiscounts() {
    ProductId productId = newProduct();
    Instant now = Instant.now();
    Instant boundary = now.plusSeconds(7200);
    adapter.save(newDiscount(productId, now.plusSeconds(3600), boundary));
    Discount consecutive = newDiscount(productId, boundary, now.plusSeconds(10800));

    assertThatCode(() -> adapter.save(consecutive)).doesNotThrowAnyException();
  }

  @Test
  void endNowClosesAnActiveDiscountWithoutDeletingIt() {
    ProductId productId = newProduct();
    Instant now = Instant.now();
    Discount saved = adapter.save(newDiscount(productId, now.minusSeconds(3600), now.plusSeconds(3600)));

    Discount ended = adapter.endNow(saved.id());

    assertThat(ended.endsAt()).isBeforeOrEqualTo(Instant.now());
    assertThat(adapter.findById(saved.id())).isPresent();
  }

  @Test
  void deletesAScheduledDiscount() {
    ProductId productId = newProduct();
    Instant now = Instant.now();
    Discount saved = adapter.save(newDiscount(productId, now.plusSeconds(3600), now.plusSeconds(7200)));

    adapter.delete(saved.id());

    assertThat(adapter.findById(saved.id())).isEmpty();
  }

  /**
   * {@link Discount#update} cross-checks the resulting {@code startsAt}/{@code endsAt} pair after
   * applying each field's own isolated validation, so moving {@code startsAt} past the unchanged
   * {@code endsAt} is rejected by the aggregate itself, before ever reaching {@code
   * chk_product_discounts_period}.
   */
  @Test
  void rejectsAPeriodMadeInvalidByMovingStartsAtPastEndsAt() {
    ProductId productId = newProduct();
    Instant now = Instant.now();
    Discount saved = adapter.save(newDiscount(productId, now.plusSeconds(3600), now.plusSeconds(7200)));

    assertThatThrownBy(() -> saved.update(now.plusSeconds(10800), null, null, null))
        .isInstanceOf(DiscountPeriodInvalidException.class);
  }

  /**
   * In the real flow {@code EndDiscountService} calls {@link Discount#end()} first, which already
   * rejects a scheduled discount — this test calls the adapter directly to verify its own {@code
   * chk_product_discounts_period} translation independently of that earlier guard.
   */
  @Test
  void endNowOnAScheduledDiscountSurfacesPeriodInvalidFromTheAdapterItself() {
    ProductId productId = newProduct();
    Instant now = Instant.now();
    Discount saved = adapter.save(newDiscount(productId, now.plusSeconds(3600), now.plusSeconds(7200)));

    assertThatThrownBy(() -> adapter.endNow(saved.id())).isInstanceOf(DiscountPeriodInvalidException.class);
  }
}
