package com.floristeriarosy.domain.model.product.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.product.ProductSlugReservedException;
import org.junit.jupiter.api.Test;

class ProductSlugTest {

  @Test
  void generatesLowercaseHyphenatedSlugFromName() {
    assertThat(ProductSlug.generateFrom("Ramo de rosas rojas").value()).isEqualTo("ramo-de-rosas-rojas");
  }

  @Test
  void stripsAccents() {
    assertThat(ProductSlug.generateFrom("Peluche osito").value()).isEqualTo("peluche-osito");
    assertThat(ProductSlug.generateFrom("Ramón").value()).isEqualTo("ramon");
  }

  @Test
  void rejectsNameThatGeneratesReservedSlug() {
    assertThatThrownBy(() -> ProductSlug.generateFrom("All"))
        .isInstanceOf(ProductSlugReservedException.class);
    assertThatThrownBy(() -> ProductSlug.generateFrom("Suggestions"))
        .isInstanceOf(ProductSlugReservedException.class);
  }

  @Test
  void ofWrapsAnAlreadyValidSlugWithoutReservedCheck() {
    assertThat(ProductSlug.of("ramos").value()).isEqualTo("ramos");
  }
}
