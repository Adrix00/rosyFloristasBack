package com.floristeriarosy.infrastructure.persistence.adapter.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.domain.exception.product.ProductSuggestsItselfException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import com.floristeriarosy.infrastructure.persistence.adapter.category.CategoryPersistenceAdapter;
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

/** Runs the migrations against real PostgreSQL, then exercises the product-suggestion adapter (product.md, 3.6). */
@Testcontainers
@SpringBootTest
class ProductSuggestionPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ProductSuggestionPersistenceAdapter adapter;
  @Autowired private ProductPersistenceAdapter productAdapter;
  @Autowired private CategoryPersistenceAdapter categoryAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ProductId newProduct() {
    String name = "Producto " + UUID.randomUUID();
    Product saved =
        productAdapter.save(
            Product.create(ProductId.newId(), name, ProductSlug.generateFrom(name), null, BigDecimal.TEN, true, Map.of()));
    return saved.id();
  }

  private ProductId newVisibleProduct() {
    ProductId id = newProduct();
    String name = "Cat " + UUID.randomUUID();
    Category category =
        categoryAdapter.save(Category.create(CategoryId.newId(), name, CategorySlug.generateFrom(name), null, null, 0));
    jdbcTemplate.update(
        "INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)", id.value(), category.id().value());
    return id;
  }

  @Test
  void replacesAndFindsVisibleSuggestions() {
    ProductId productId = newProduct();
    ProductId extraId = newVisibleProduct();

    adapter.replaceSuggestions(productId, List.of(extraId));

    List<ProductSummaryDto> suggestions = adapter.findVisibleSuggestions(productId);
    assertThat(suggestions).extracting(ProductSummaryDto::id).containsExactly(extraId.value());
  }

  @Test
  void excludesASuggestionThatIsNotCurrentlyVisible() {
    ProductId productId = newProduct();
    ProductId extraId = newProduct();
    adapter.replaceSuggestions(productId, List.of(extraId));

    List<ProductSummaryDto> suggestions = adapter.findVisibleSuggestions(productId);

    assertThat(suggestions).isEmpty();
  }

  @Test
  void replaceOverwritesThePreviousSuggestionSet() {
    ProductId productId = newProduct();
    ProductId first = newVisibleProduct();
    ProductId second = newVisibleProduct();
    adapter.replaceSuggestions(productId, List.of(first));

    adapter.replaceSuggestions(productId, List.of(second));

    List<ProductSummaryDto> suggestions = adapter.findVisibleSuggestions(productId);
    assertThat(suggestions).extracting(ProductSummaryDto::id).containsExactly(second.value());
  }

  @Test
  void rejectsAProductSuggestingItself() {
    ProductId productId = newProduct();

    assertThatThrownBy(() -> adapter.replaceSuggestions(productId, List.of(productId)))
        .isInstanceOf(ProductSuggestsItselfException.class);
  }

  @Test
  void aProductWithNoSuggestionsReturnsAnEmptyList() {
    ProductId productId = newProduct();

    assertThat(adapter.findVisibleSuggestions(productId)).isEmpty();
  }
}
