package com.floristeriarosy.domain.model.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import org.junit.jupiter.api.Test;

class CategoryTest {

  @Test
  void newCategoryIsBornActive() {
    Category category =
        Category.create(
            CategoryId.newId(), "Ramos", CategorySlug.generateFrom("Ramos"), null, null, 0);

    assertThat(category.status()).isEqualTo(CategoryStatus.ACTIVE);
  }

  @Test
  void changingToTheSameStatusIsANoOp() {
    Category category =
        Category.create(
            CategoryId.newId(), "Ramos", CategorySlug.generateFrom("Ramos"), null, null, 0);

    category.changeStatus(CategoryStatus.ACTIVE);

    assertThat(category.status()).isEqualTo(CategoryStatus.ACTIVE);
  }

  @Test
  void replaceClearsAnAbsentOptionalField() {
    Category category =
        Category.create(
            CategoryId.newId(),
            "Ramos",
            CategorySlug.generateFrom("Ramos"),
            "descripcion",
            null,
            0);

    category.replace("Ramos", CategorySlug.generateFrom("Ramos"), null, null, 3);

    assertThat(category.description()).isNull();
    assertThat(category.position()).isEqualTo(3);
  }
}
