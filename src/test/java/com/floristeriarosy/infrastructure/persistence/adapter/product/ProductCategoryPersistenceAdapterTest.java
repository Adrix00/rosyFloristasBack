package com.floristeriarosy.infrastructure.persistence.adapter.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.product.dto.ProductCategoryRef;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the migrations against real PostgreSQL, then exercises the product/category association adapter. */
@Testcontainers
@SpringBootTest
class ProductCategoryPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ProductCategoryPersistenceAdapter adapter;
  @Autowired private ProductPersistenceAdapter productAdapter;
  @Autowired private CategoryPersistenceAdapter categoryAdapter;

  private ProductId newProduct() {
    String name = "Producto " + UUID.randomUUID();
    Product saved =
        productAdapter.save(
            Product.create(ProductId.newId(), name, ProductSlug.generateFrom(name), null, BigDecimal.TEN, false, Map.of()));
    return saved.id();
  }

  private CategoryId newCategory() {
    String name = "Cat " + UUID.randomUUID();
    Category saved =
        categoryAdapter.save(Category.create(CategoryId.newId(), name, CategorySlug.generateFrom(name), null, null, 0));
    return saved.id();
  }

  @Test
  void replacesAndFindsCategories() {
    ProductId productId = newProduct();
    CategoryId categoryId = newCategory();

    adapter.replaceCategories(productId, List.of(categoryId));

    List<ProductCategoryRef> found = adapter.findCategories(productId);
    assertThat(found).extracting(ProductCategoryRef::id).containsExactly(categoryId.value());
  }

  @Test
  void replaceOverwritesThePreviousSet() {
    ProductId productId = newProduct();
    CategoryId first = newCategory();
    CategoryId second = newCategory();
    adapter.replaceCategories(productId, List.of(first));

    adapter.replaceCategories(productId, List.of(second));

    List<ProductCategoryRef> found = adapter.findCategories(productId);
    assertThat(found).extracting(ProductCategoryRef::id).containsExactly(second.value());
  }

  @Test
  void aProductWithNoCategoriesReturnsAnEmptyList() {
    ProductId productId = newProduct();

    assertThat(adapter.findCategories(productId)).isEmpty();
  }
}
