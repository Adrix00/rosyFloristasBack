package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.command.UpdateProductCommand;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.port.out.ProductWritePort;
import com.floristeriarosy.application.product.validation.ProductAttributeValidator;
import com.floristeriarosy.domain.exception.product.ProductAlreadyExistsException;
import com.floristeriarosy.domain.exception.product.ProductAttributeUndeclaredException;
import com.floristeriarosy.domain.exception.product.ProductDiscontinuedException;
import com.floristeriarosy.domain.exception.product.ProductHasActiveDiscountException;
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
class UpdateProductServiceTest {

  @Mock private ProductReadPort readPort;
  @Mock private ProductWritePort writePort;
  @Mock private ProductCategoryPort categoryPort;
  @Mock private ProductImagePort imagePort;
  @Mock private ProductAttributeValidator attributeValidator;

  private UpdateProductService service;

  private Product existingProduct(UUID id, String name, BigDecimal price, ProductStatus status) {
    return Product.reconstitute(
        ProductId.of(id),
        name,
        ProductSlug.generateFrom(name),
        "descripcion",
        price,
        null,
        null,
        status,
        false,
        Map.of(),
        Instant.now(),
        Instant.now());
  }

  private UpdateProductCommand command(UUID id, String name, BigDecimal price) {
    return new UpdateProductCommand(id, name, "descripcion", price, false, Map.of());
  }

  @Test
  void replacesTheProductsOwnFields() {
    service = new UpdateProductService(readPort, writePort, categoryPort, imagePort, attributeValidator);
    UUID id = UUID.randomUUID();
    Product product = existingProduct(id, "Ramo viejo", new BigDecimal("10.00"), ProductStatus.ACTIVE);
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product));
    when(readPort.findBySlug("ramo-nuevo")).thenReturn(Optional.empty());
    when(writePort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ProductDto dto = service.execute(command(id, "Ramo nuevo", new BigDecimal("10.00")));

    assertThat(dto.name()).isEqualTo("Ramo nuevo");
    assertThat(dto.slug()).isEqualTo("ramo-nuevo");
  }

  @Test
  void rejectsUpdateOfAnUnknownProduct() {
    service = new UpdateProductService(readPort, writePort, categoryPort, imagePort, attributeValidator);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(command(id, "Ramo nuevo", new BigDecimal("10.00"))))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void rejectsWhenTheRegeneratedSlugCollidesWithAnotherProduct() {
    service = new UpdateProductService(readPort, writePort, categoryPort, imagePort, attributeValidator);
    UUID id = UUID.randomUUID();
    Product product = existingProduct(id, "Ramo viejo", new BigDecimal("10.00"), ProductStatus.ACTIVE);
    Product other = existingProduct(UUID.randomUUID(), "Ramo nuevo", new BigDecimal("5.00"), ProductStatus.ACTIVE);
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product));
    when(readPort.findBySlug("ramo-nuevo")).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> service.execute(command(id, "Ramo nuevo", new BigDecimal("10.00"))))
        .isInstanceOf(ProductAlreadyExistsException.class);
  }

  @Test
  void rejectsAPriceChangeWhileADiscountIsActive() {
    service = new UpdateProductService(readPort, writePort, categoryPort, imagePort, attributeValidator);
    UUID id = UUID.randomUUID();
    Product product = existingProduct(id, "Ramo viejo", new BigDecimal("10.00"), ProductStatus.ACTIVE);
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product));
    when(readPort.findActiveSalePrice(ProductId.of(id))).thenReturn(Optional.of(new BigDecimal("8.00")));

    assertThatThrownBy(() -> service.execute(command(id, "Ramo viejo", new BigDecimal("15.00"))))
        .isInstanceOf(ProductHasActiveDiscountException.class);
  }

  @Test
  void rejectsEditingADiscontinuedProduct() {
    service = new UpdateProductService(readPort, writePort, categoryPort, imagePort, attributeValidator);
    UUID id = UUID.randomUUID();
    Product product = existingProduct(id, "Ramo viejo", new BigDecimal("10.00"), ProductStatus.DISCONTINUED);
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> service.execute(command(id, "Ramo viejo", new BigDecimal("10.00"))))
        .isInstanceOf(ProductDiscontinuedException.class);
  }

  @Test
  void rejectsUndeclaredAttributesBeforeTouchingThePort() {
    service = new UpdateProductService(readPort, writePort, categoryPort, imagePort, attributeValidator);
    UUID id = UUID.randomUUID();
    doThrow(new ProductAttributeUndeclaredException("bad key")).when(attributeValidator).validate(any());

    assertThatThrownBy(() -> service.execute(command(id, "Ramo viejo", new BigDecimal("10.00"))))
        .isInstanceOf(ProductAttributeUndeclaredException.class);
    verifyNoInteractions(readPort);
  }
}
