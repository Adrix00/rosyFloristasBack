package com.floristeriarosy.domain.model.category.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.category.CategorySlugReservedException;
import org.junit.jupiter.api.Test;

class CategorySlugTest {

  @Test
  void generatesLowercaseHyphenatedSlugFromName() {
    assertThat(CategorySlug.generateFrom("Ramos de novia").value()).isEqualTo("ramos-de-novia");
  }

  @Test
  void stripsAccents() {
    assertThat(CategorySlug.generateFrom("San Valentín").value()).isEqualTo("san-valentin");
  }

  @Test
  void rejectsNameThatGeneratesReservedSlug() {
    assertThatThrownBy(() -> CategorySlug.generateFrom("All"))
        .isInstanceOf(CategorySlugReservedException.class);
    assertThatThrownBy(() -> CategorySlug.generateFrom("Positions"))
        .isInstanceOf(CategorySlugReservedException.class);
  }

  @Test
  void ofWrapsAnAlreadyValidSlugWithoutReservedCheck() {
    assertThat(CategorySlug.of("ramos").value()).isEqualTo("ramos");
  }
}
