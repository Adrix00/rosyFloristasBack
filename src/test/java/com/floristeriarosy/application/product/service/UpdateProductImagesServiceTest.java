package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.command.UpdateProductImagesCommand;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.dto.ProductImageAssignment;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
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
class UpdateProductImagesServiceTest {

  @Mock private ProductReadPort readPort;
  @Mock private ProductCategoryPort categoryPort;
  @Mock private ProductImagePort imagePort;

  private UpdateProductImagesService service;

  private Product product(UUID id) {
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
  void replacesTheFullGalleryInOrder() {
    service = new UpdateProductImagesService(readPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    UUID imageId = UUID.randomUUID();
    List<ProductImageAssignment> images = List.of(new ProductImageAssignment(imageId, "alt"));
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product(id)));
    when(imagePort.findImages(ProductId.of(id)))
        .thenReturn(List.of(new ProductImageRef(UUID.randomUUID(), imageId, null, "alt", 0)));

    ProductDto dto = service.execute(new UpdateProductImagesCommand(id, images));

    assertThat(dto.images()).hasSize(1);
    verify(imagePort).replaceImages(ProductId.of(id), images);
  }

  @Test
  void rejectsUpdatingImagesOfAnUnknownProduct() {
    service = new UpdateProductImagesService(readPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new UpdateProductImagesCommand(id, List.of())))
        .isInstanceOf(ProductNotFoundException.class);
  }
}
