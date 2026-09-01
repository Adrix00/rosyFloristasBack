package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.command.ChangeProductStatusCommand;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.port.out.ProductWritePort;
import com.floristeriarosy.domain.exception.product.ProductDiscontinuedException;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChangeProductStatusServiceTest {

  @Mock private ProductReadPort readPort;
  @Mock private ProductWritePort writePort;
  @Mock private ProductCategoryPort categoryPort;
  @Mock private ProductImagePort imagePort;

  private ChangeProductStatusService service;

  private Product product(UUID id, ProductStatus status) {
    return Product.reconstitute(
        ProductId.of(id),
        "Ramo",
        ProductSlug.generateFrom("Ramo"),
        null,
        BigDecimal.TEN,
        null,
        null,
        status,
        false,
        Map.of(),
        Instant.now(),
        Instant.now());
  }

  @Test
  void changesTheProductsStatus() {
    service = new ChangeProductStatusService(readPort, writePort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    Product product = product(id, ProductStatus.ACTIVE);
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product));
    when(writePort.updateStatus(ProductId.of(id), ProductStatus.INACTIVE))
        .thenReturn(product(id, ProductStatus.INACTIVE));

    ProductDto dto = service.execute(new ChangeProductStatusCommand(id, ProductStatus.INACTIVE));

    assertThat(dto.status()).isEqualTo(ProductStatus.INACTIVE);
  }

  @Test
  void rejectsChangingAnUnknownProduct() {
    service = new ChangeProductStatusService(readPort, writePort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new ChangeProductStatusCommand(id, ProductStatus.INACTIVE)))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void rejectsReactivatingADiscontinuedProduct() {
    service = new ChangeProductStatusService(readPort, writePort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product(id, ProductStatus.DISCONTINUED)));

    assertThatThrownBy(() -> service.execute(new ChangeProductStatusCommand(id, ProductStatus.ACTIVE)))
        .isInstanceOf(ProductDiscontinuedException.class);
  }

  @Test
  void settingTheSameStatusIsIdempotent() {
    service = new ChangeProductStatusService(readPort, writePort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    Product product = product(id, ProductStatus.ACTIVE);
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product));
    when(writePort.updateStatus(any(ProductId.class), any(ProductStatus.class))).thenReturn(product);

    ProductDto dto = service.execute(new ChangeProductStatusCommand(id, ProductStatus.ACTIVE));

    assertThat(dto.status()).isEqualTo(ProductStatus.ACTIVE);
  }
}
