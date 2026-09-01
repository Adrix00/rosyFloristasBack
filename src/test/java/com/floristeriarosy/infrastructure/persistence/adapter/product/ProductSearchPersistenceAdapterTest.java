package com.floristeriarosy.infrastructure.persistence.adapter.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductSearchCriteria;
import com.floristeriarosy.application.product.dto.ProductSuggestionDto;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.infrastructure.persistence.adapter.category.CategoryPersistenceAdapter;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** Runs the migrations against real PostgreSQL, then exercises search and autocomplete (ADR-006). */
@Testcontainers
@SpringBootTest
class ProductSearchPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ProductSearchPersistenceAdapter adapter;
  @Autowired private ProductPersistenceAdapter productAdapter;
  @Autowired private CategoryPersistenceAdapter categoryAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private CategoryId newActiveCategory() {
    String name = "Cat " + UUID.randomUUID();
    Category saved =
        categoryAdapter.save(Category.create(CategoryId.newId(), name, CategorySlug.generateFrom(name), null, null, 0));
    return saved.id();
  }

  private ProductId newVisibleProduct(String name, BigDecimal price, Map<String, Object> attributes, CategoryId categoryId) {
    Product saved =
        productAdapter.save(
            Product.create(ProductId.newId(), name, ProductSlug.generateFrom(name), "descripcion", price, false, attributes));
    jdbcTemplate.update(
        "INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)",
        saved.id().value(),
        categoryId.value());
    return saved.id();
  }

  private void insertActiveDiscount(ProductId productId, BigDecimal originalPrice, BigDecimal salePrice) {
    jdbcTemplate.update(
        "INSERT INTO product_discounts (id, product_id, original_price, sale_price, starts_at, ends_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        productId.value(),
        originalPrice,
        salePrice,
        Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
        Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)));
  }

  private ProductSearchCriteria criteria(
      String q, String category, BigDecimal minPrice, BigDecimal maxPrice, boolean onSale, Map<String, Object> attrs) {
    return new ProductSearchCriteria(q, category, minPrice, maxPrice, onSale, attrs, 0, 20);
  }

  @Test
  void searchWithoutFiltersReturnsOnlyVisibleProducts() {
    CategoryId categoryId = newActiveCategory();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    ProductId visible = newVisibleProduct("Visible " + suffix, BigDecimal.TEN, Map.of(), categoryId);
    // An invisible product: never linked to any category.
    productAdapter.save(
        Product.create(
            ProductId.newId(), "Invisible " + suffix, ProductSlug.generateFrom("Invisible " + suffix), null,
            BigDecimal.ONE, false, Map.of()));

    PageResult<ProductSummaryDto> page = adapter.search(criteria(null, null, null, null, false, Map.of()));

    assertThat(page.items()).extracting(ProductSummaryDto::id).contains(visible.value());
  }

  @Test
  void searchByFullTextMatchesTheProductName() {
    CategoryId categoryId = newActiveCategory();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    ProductId matching = newVisibleProduct("Girasoles amarillos " + suffix, BigDecimal.TEN, Map.of(), categoryId);
    newVisibleProduct("Tulipanes morados " + suffix, BigDecimal.TEN, Map.of(), categoryId);

    PageResult<ProductSummaryDto> page = adapter.search(criteria("girasoles", null, null, null, false, Map.of()));

    assertThat(page.items()).extracting(ProductSummaryDto::id).containsExactly(matching.value());
  }

  @Test
  void searchByCategoryFiltersToThatCategoryOnly() {
    CategoryId categoryA = newActiveCategory();
    CategoryId categoryB = newActiveCategory();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    ProductId inA = newVisibleProduct("En A " + suffix, BigDecimal.TEN, Map.of(), categoryA);
    newVisibleProduct("En B " + suffix, BigDecimal.TEN, Map.of(), categoryB);

    PageResult<ProductSummaryDto> page =
        adapter.search(criteria(null, categoryA.value().toString(), null, null, false, Map.of()));

    assertThat(page.items()).extracting(ProductSummaryDto::id).containsExactly(inA.value());
  }

  @Test
  void searchByPriceRangeFiltersOnEffectivePrice() {
    CategoryId categoryId = newActiveCategory();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    ProductId cheap = newVisibleProduct("Barato " + suffix, new BigDecimal("5.00"), Map.of(), categoryId);
    newVisibleProduct("Caro " + suffix, new BigDecimal("500.00"), Map.of(), categoryId);

    PageResult<ProductSummaryDto> page =
        adapter.search(criteria(null, null, new BigDecimal("1.00"), new BigDecimal("10.00"), false, Map.of()));

    assertThat(page.items()).extracting(ProductSummaryDto::id).containsExactly(cheap.value());
  }

  @Test
  void onSaleFiltersToProductsWithACurrentlyActiveDiscount() {
    CategoryId categoryId = newActiveCategory();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    ProductId onSale = newVisibleProduct("Con descuento " + suffix, new BigDecimal("20.00"), Map.of(), categoryId);
    newVisibleProduct("Sin descuento " + suffix, new BigDecimal("20.00"), Map.of(), categoryId);
    insertActiveDiscount(onSale, new BigDecimal("20.00"), new BigDecimal("15.00"));

    PageResult<ProductSummaryDto> page = adapter.search(criteria(null, null, null, null, true, Map.of()));

    assertThat(page.items()).extracting(ProductSummaryDto::id).containsExactly(onSale.value());
    assertThat(page.items().get(0).effectivePrice()).isEqualByComparingTo("15.00");
    assertThat(page.items().get(0).onSale()).isTrue();
  }

  @Test
  void searchByAnAttributeFilterMatchesItsJsonbValue() {
    CategoryId categoryId = newActiveCategory();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    ProductId red = newVisibleProduct("Rojo " + suffix, BigDecimal.TEN, Map.of("color", "rojo"), categoryId);
    newVisibleProduct("Azul " + suffix, BigDecimal.TEN, Map.of("color", "azul"), categoryId);

    PageResult<ProductSummaryDto> page =
        adapter.search(criteria(null, null, null, null, false, Map.of("color", "rojo")));

    assertThat(page.items()).extracting(ProductSummaryDto::id).containsExactly(red.value());
  }

  @Test
  void searchPaginatesResultsInNameOrder() {
    CategoryId categoryId = newActiveCategory();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    ProductId first = newVisibleProduct("AAA Primero " + suffix, BigDecimal.TEN, Map.of(), categoryId);
    ProductId second = newVisibleProduct("BBB Segundo " + suffix, BigDecimal.TEN, Map.of(), categoryId);
    ProductId third = newVisibleProduct("CCC Tercero " + suffix, BigDecimal.TEN, Map.of(), categoryId);

    PageResult<ProductSummaryDto> firstPage =
        adapter.search(new ProductSearchCriteria(null, categoryId.value().toString(), null, null, false, Map.of(), 0, 2));
    PageResult<ProductSummaryDto> secondPage =
        adapter.search(new ProductSearchCriteria(null, categoryId.value().toString(), null, null, false, Map.of(), 1, 2));

    assertThat(firstPage.items()).hasSize(2);
    assertThat(firstPage.totalElements()).isEqualTo(3);
    assertThat(firstPage.items()).extracting(ProductSummaryDto::id).containsExactly(first.value(), second.value());
    assertThat(secondPage.items()).extracting(ProductSummaryDto::id).containsExactly(third.value());
  }

  @Test
  void autocompleteToleratesAPrefixOfTheProductName() {
    CategoryId categoryId = newActiveCategory();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    newVisibleProduct("Zzrosaszz " + suffix, BigDecimal.TEN, Map.of(), categoryId);

    List<ProductSuggestionDto> suggestions = adapter.autocomplete("zzrosas", 10);

    assertThat(suggestions).extracting(ProductSuggestionDto::name).anyMatch(name -> name.contains(suffix));
  }
}
