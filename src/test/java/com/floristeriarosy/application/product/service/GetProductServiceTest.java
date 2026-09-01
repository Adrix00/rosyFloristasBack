package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.query.GetProductQuery;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetProductServiceTest {

  @Mock private ProductReadPort readPort;
  @Mock private ProductCategoryPort categoryPort;
  @Mock private ProductImagePort imagePort;

  private GetProductService service;

  private Product visibleProduct(UUID id) {
    return Product.reconstitute(
        ProductId.of(id),
        "Ramo",
        ProductSlug.generateFrom("Ramo"),
        null,
        BigDecimal.TEN,
        null,
        null,
        ProductStatus.ACTIVE,
        false,
        Map.of(),
        Instant.now(),
        Instant.now());
  }

  @Test
  void findsAVisibleProductByUuid() {
    service = new GetProductService(readPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    Product product = visibleProduct(id);
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product));
    when(readPort.isVisible(ProductId.of(id))).thenReturn(true);
    when(categoryPort.findCategories(ProductId.of(id))).thenReturn(List.of());
    when(imagePort.findImages(ProductId.of(id))).thenReturn(List.of());

    ProductDto dto = service.execute(new GetProductQuery(id.toString()));

    assertThat(dto.id()).isEqualTo(id);
  }

  @Test
  void findsAVisibleProductBySlugWhenTheSegmentIsNotAUuid() {
    service = new GetProductService(readPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    Product product = visibleProduct(id);
    when(readPort.findBySlug("ramo")).thenReturn(Optional.of(product));
    when(readPort.isVisible(ProductId.of(id))).thenReturn(true);
    when(categoryPort.findCategories(ProductId.of(id))).thenReturn(List.of());
    when(imagePort.findImages(ProductId.of(id))).thenReturn(List.of());

    ProductDto dto = service.execute(new GetProductQuery("ramo"));

    assertThat(dto.slug()).isEqualTo("ramo");
  }

  @Test
  void rejectsWhenNoProductMatches() {
    service = new GetProductService(readPort, categoryPort, imagePort);
    when(readPort.findBySlug("no-existe")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new GetProductQuery("no-existe")))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void rejectsANonVisibleProductAsNotFound() {
    service = new GetProductService(readPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    Product product = visibleProduct(id);
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product));
    when(readPort.isVisible(ProductId.of(id))).thenReturn(false);

    assertThatThrownBy(() -> service.execute(new GetProductQuery(id.toString())))
        .isInstanceOf(ProductNotFoundException.class);
  }
}
