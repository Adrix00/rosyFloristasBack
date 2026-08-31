package com.floristeriarosy.infrastructure.persistence.adapter.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.category.CategoryAlreadyExistsException;
import com.floristeriarosy.domain.exception.category.CategoryImageNotFoundException;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the 15 migrations against real PostgreSQL, then exercises the category adapter. */
@Testcontainers
@SpringBootTest
class CategoryPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private CategoryPersistenceAdapter adapter;

  private Category newCategory(String name) {
    return Category.create(
        CategoryId.newId(), name, CategorySlug.generateFrom(name), "desc", null, 0);
  }

  @Test
  void savesAndFindsByIdAndSlug() {
    Category saved = adapter.save(newCategory("Ramos IT " + UUID.randomUUID()));

    assertThat(adapter.findById(saved.id())).isPresent();
    assertThat(adapter.findBySlug(saved.slug().value())).isPresent();
    assertThat(saved.createdAt()).isNotNull();
  }

  @Test
  void rejectsADuplicateSlug() {
    Category first = newCategory("Duplicado " + UUID.randomUUID());
    adapter.save(first);
    Category second =
        Category.create(CategoryId.newId(), "otro nombre", first.slug(), null, null, 0);

    assertThatThrownBy(() -> adapter.save(second))
        .isInstanceOf(CategoryAlreadyExistsException.class);
  }

  @Test
  void rejectsAnImageIdThatDoesNotExist() {
    Category category =
        Category.create(
            CategoryId.newId(),
            "Con imagen " + UUID.randomUUID(),
            CategorySlug.generateFrom("Con imagen " + UUID.randomUUID()),
            null,
            UUID.randomUUID(),
            0);

    assertThatThrownBy(() -> adapter.save(category))
        .isInstanceOf(CategoryImageNotFoundException.class);
  }

  @Test
  void deletesACategory() {
    Category saved = adapter.save(newCategory("Borrar " + UUID.randomUUID()));

    adapter.delete(saved.id());

    assertThat(adapter.existsById(saved.id())).isFalse();
  }

  @Test
  void updatesPositionsInOrder() {
    Category first = adapter.save(newCategory("Primero " + UUID.randomUUID()));
    Category second = adapter.save(newCategory("Segundo " + UUID.randomUUID()));

    adapter.updatePositions(List.of(second.id(), first.id()));

    assertThat(adapter.findById(second.id()).orElseThrow().position()).isZero();
    assertThat(adapter.findById(first.id()).orElseThrow().position()).isEqualTo(1);
  }
}
