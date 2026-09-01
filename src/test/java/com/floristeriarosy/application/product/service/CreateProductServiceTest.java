package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.command.CreateProductCommand;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductInventoryPort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.product.port.out.ProductWritePort;
import com.floristeriarosy.application.product.validation.ProductAttributeValidator;
import com.floristeriarosy.domain.exception.product.ProductAlreadyExistsException;
import com.floristeriarosy.domain.exception.product.ProductWithoutCategoryException;
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
class CreateProductServiceTest {

  @Mock private ProductWritePort writePort;
  @Mock private ProductReadPort readPort;
  @Mock private ProductExistencePort existencePort;
  @Mock private ProductCategoryPort categoryPort;
  @Mock private ProductImagePort imagePort;
  @Mock private ProductInventoryPort inventoryPort;
  @Mock private ProductAttributeValidator attributeValidator;

  private CreateProductService service;

  private CreateProductCommand command(List<UUID> categoryIds, Integer initialStock) {
    return new CreateProductCommand(
        "Ramo de rosas",
        "descripcion",
        new BigDecimal("19.99"),
        categoryIds,
        false,
        Map.of(),
        List.of(),
        initialStock);
  }

  private Product reconstitutedProduct(ProductId id, Integer stock) {
    return Product.reconstitute(
        id,
        "Ramo de rosas",
        ProductSlug.of("ramo-de-rosas"),
        "descripcion",
        new BigDecimal("19.99"),
        stock,
        null,
        ProductStatus.ACTIVE,
        false,
        Map.of(),
        Instant.now(),
        Instant.now());
  }

  @Test
  void createsProductWithGeneratedSlugAndAssignsCategories() {
    service =
        new CreateProductService(
            writePort, readPort, existencePort, categoryPort, imagePort, inventoryPort, attributeValidator);
    UUID categoryId = UUID.randomUUID();
    when(existencePort.existsBySlug("ramo-de-rosas")).thenReturn(false);
    when(writePort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ProductDto dto = service.execute(command(List.of(categoryId), null));

    assertThat(dto.name()).isEqualTo("Ramo de rosas");
    assertThat(dto.slug()).isEqualTo("ramo-de-rosas");
    verify(categoryPort).replaceCategories(any(ProductId.class), any());
    verify(inventoryPort, never()).initializeStock(any(), anyInt(), any(), any());
  }

  @Test
  void rejectsCreationWithoutAnyCategory() {
    service =
        new CreateProductService(
            writePort, readPort, existencePort, categoryPort, imagePort, inventoryPort, attributeValidator);

    assertThatThrownBy(() -> service.execute(command(List.of(), null)))
        .isInstanceOf(ProductWithoutCategoryException.class);
  }

  @Test
  void rejectsWhenGeneratedSlugAlreadyExists() {
    service =
        new CreateProductService(
            writePort, readPort, existencePort, categoryPort, imagePort, inventoryPort, attributeValidator);
    when(existencePort.existsBySlug("ramo-de-rosas")).thenReturn(true);

    assertThatThrownBy(() -> service.execute(command(List.of(UUID.randomUUID()), null)))
        .isInstanceOf(ProductAlreadyExistsException.class);
  }

  @Test
  void activatesManagedInventoryWhenInitialStockIsGiven() {
    service =
        new CreateProductService(
            writePort, readPort, existencePort, categoryPort, imagePort, inventoryPort, attributeValidator);
    UUID categoryId = UUID.randomUUID();
    when(existencePort.existsBySlug("ramo-de-rosas")).thenReturn(false);
    when(writePort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(readPort.findById(any(ProductId.class)))
        .thenAnswer(invocation -> Optional.of(reconstitutedProduct(invocation.getArgument(0), 5)));

    ProductDto dto = service.execute(command(List.of(categoryId), 5));

    assertThat(dto.stock()).isEqualTo(5);
    verify(inventoryPort).initializeStock(any(ProductId.class), eq(5), any(), any());
  }
}
