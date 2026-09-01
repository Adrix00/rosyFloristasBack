package com.floristeriarosy.infrastructure.persistence.adapter.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.product.dto.ProductImageAssignment;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
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

/** Runs the migrations against real PostgreSQL, then exercises the product image-gallery adapter. */
@Testcontainers
@SpringBootTest
class ProductImagePersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ProductImagePersistenceAdapter adapter;
  @Autowired private ProductPersistenceAdapter productAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ProductId newProduct() {
    String name = "Producto " + UUID.randomUUID();
    Product saved =
        productAdapter.save(
            Product.create(ProductId.newId(), name, ProductSlug.generateFrom(name), null, BigDecimal.TEN, false, Map.of()));
    return saved.id();
  }

  private UUID newImage() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO images (id, s3_key, content_type, byte_size, width, height, created_at) "
            + "VALUES (?, ?, 'image/png', 100, 10, 10, now())",
        id,
        "key-" + id);
    return id;
  }

  @Test
  void replacesAndFindsTheGalleryInPositionOrder() {
    ProductId productId = newProduct();
    UUID firstImage = newImage();
    UUID secondImage = newImage();

    adapter.replaceImages(
        productId,
        List.of(new ProductImageAssignment(firstImage, "primera"), new ProductImageAssignment(secondImage, "segunda")));

    List<ProductImageRef> gallery = adapter.findImages(productId);
    assertThat(gallery).extracting(ProductImageRef::imageId).containsExactly(firstImage, secondImage);
    assertThat(gallery).extracting(ProductImageRef::position).containsExactly(0, 1);
    assertThat(gallery.get(0).altText()).isEqualTo("primera");
  }

  @Test
  void replaceOverwritesThePreviousGallery() {
    ProductId productId = newProduct();
    UUID first = newImage();
    UUID second = newImage();
    adapter.replaceImages(productId, List.of(new ProductImageAssignment(first, null)));

    adapter.replaceImages(productId, List.of(new ProductImageAssignment(second, null)));

    List<ProductImageRef> gallery = adapter.findImages(productId);
    assertThat(gallery).extracting(ProductImageRef::imageId).containsExactly(second);
  }

  @Test
  void aProductWithNoImagesReturnsAnEmptyGallery() {
    ProductId productId = newProduct();

    assertThat(adapter.findImages(productId)).isEmpty();
  }
}
