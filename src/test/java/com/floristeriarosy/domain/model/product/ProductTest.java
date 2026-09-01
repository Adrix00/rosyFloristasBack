package com.floristeriarosy.domain.model.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.product.ProductDiscontinuedException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductTest {

  private Product newProduct() {
    return Product.create(
        ProductId.newId(),
        "Ramo de rosas",
        ProductSlug.generateFrom("Ramo de rosas"),
        "descripcion",
        new BigDecimal("19.99"),
        false,
        Map.of("color", "rojo"));
  }

  @Test
  void newProductIsBornActiveWithoutInventory() {
    Product product = newProduct();

    assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(product.stock()).isNull();
  }

  @Test
  void changingToTheSameStatusIsANoOp() {
    Product product = newProduct();

    product.changeStatus(ProductStatus.ACTIVE);

    assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
  }

  @Test
  void discontinuedIsTerminal() {
    Product product = newProduct();
    product.changeStatus(ProductStatus.DISCONTINUED);

    assertThatThrownBy(() -> product.changeStatus(ProductStatus.ACTIVE))
        .isInstanceOf(ProductDiscontinuedException.class);
  }

  @Test
  void reDiscontinuingADiscontinuedProductIsANoOp() {
    Product product = newProduct();
    product.changeStatus(ProductStatus.DISCONTINUED);

    product.changeStatus(ProductStatus.DISCONTINUED);

    assertThat(product.status()).isEqualTo(ProductStatus.DISCONTINUED);
  }

  @Test
  void replaceClearsAnAbsentOptionalDescription() {
    Product product = newProduct();

    product.replace(
        "Ramo de rosas",
        ProductSlug.generateFrom("Ramo de rosas"),
        null,
        new BigDecimal("25.00"),
        true,
        Map.of());

    assertThat(product.description()).isNull();
    assertThat(product.price()).isEqualByComparingTo("25.00");
    assertThat(product.isExtra()).isTrue();
    assertThat(product.attributes()).isEmpty();
  }

  @Test
  void replaceRejectsADiscontinuedProduct() {
    Product product = newProduct();
    product.changeStatus(ProductStatus.DISCONTINUED);

    assertThatThrownBy(
            () ->
                product.replace(
                    "Nuevo nombre",
                    ProductSlug.generateFrom("Nuevo nombre"),
                    null,
                    new BigDecimal("10.00"),
                    false,
                    Map.of()))
        .isInstanceOf(ProductDiscontinuedException.class);
  }

  @Test
  void attributesAreDefensivelyCopiedOnCreation() {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("color", "rojo");
    Product product =
        Product.create(
            ProductId.newId(),
            "Ramo",
            ProductSlug.generateFrom("Ramo"),
            null,
            BigDecimal.TEN,
            false,
            attributes);

    attributes.put("color", "azul");

    assertThat(product.attributes()).containsEntry("color", "rojo");
  }

  @Test
  void attributesReturnedAreUnmodifiable() {
    Product product = newProduct();

    assertThatThrownBy(() -> product.attributes().put("color", "azul"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullAttributesBecomeAnEmptyMap() {
    Product product =
        Product.create(
            ProductId.newId(), "Ramo", ProductSlug.generateFrom("Ramo"), null, BigDecimal.TEN, false, null);

    assertThat(product.attributes()).isEmpty();
  }
}
