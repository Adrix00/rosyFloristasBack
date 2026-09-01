package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.command.ChangeInventoryModeCommand;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.application.product.port.out.ProductImagePort;
import com.floristeriarosy.application.product.port.out.ProductInventoryPort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.exception.product.ProductStockRequiredException;
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
class ChangeInventoryModeServiceTest {

  @Mock private ProductReadPort readPort;
  @Mock private ProductInventoryPort inventoryPort;
  @Mock private ProductCategoryPort categoryPort;
  @Mock private ProductImagePort imagePort;

  private ChangeInventoryModeService service;

  private Product product(UUID id, Integer stock) {
    return Product.reconstitute(
        ProductId.of(id),
        "Ramo",
        ProductSlug.generateFrom("Ramo"),
        null,
        BigDecimal.TEN,
        stock,
        null,
        ProductStatus.ACTIVE,
        false,
        Map.of(),
        Instant.now(),
        Instant.now());
  }

  @Test
  void rejectsActivatingManagedInventoryWithoutAStockValue() {
    service = new ChangeInventoryModeService(readPort, inventoryPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product(id, null)));

    assertThatThrownBy(
            () -> service.execute(new ChangeInventoryModeCommand(id, true, null, null, null)))
        .isInstanceOf(ProductStockRequiredException.class);
  }

  @Test
  void firstActivationInitializesStock() {
    service = new ChangeInventoryModeService(readPort, inventoryPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id)))
        .thenReturn(Optional.of(product(id, null)))
        .thenReturn(Optional.of(product(id, 10)));
    when(categoryPort.findCategories(ProductId.of(id))).thenReturn(List.of());
    when(imagePort.findImages(ProductId.of(id))).thenReturn(List.of());

    ProductDto dto = service.execute(new ChangeInventoryModeCommand(id, true, 10, null, null));

    assertThat(dto.stock()).isEqualTo(10);
    verify(inventoryPort).initializeStock(ProductId.of(id), 10, null, null);
    verify(inventoryPort, never()).adjustStock(any(), anyInt(), any(), any());
  }

  @Test
  void reactivatingAnAlreadyManagedProductAdjustsStock() {
    service = new ChangeInventoryModeService(readPort, inventoryPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id)))
        .thenReturn(Optional.of(product(id, 5)))
        .thenReturn(Optional.of(product(id, 12)));
    when(categoryPort.findCategories(ProductId.of(id))).thenReturn(List.of());
    when(imagePort.findImages(ProductId.of(id))).thenReturn(List.of());

    service.execute(new ChangeInventoryModeCommand(id, true, 12, null, null));

    verify(inventoryPort).adjustStock(ProductId.of(id), 12, null, null);
    verify(inventoryPort, never()).initializeStock(any(), anyInt(), any(), any());
  }

  @Test
  void disablesManagedInventory() {
    service = new ChangeInventoryModeService(readPort, inventoryPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id)))
        .thenReturn(Optional.of(product(id, 5)))
        .thenReturn(Optional.of(product(id, null)));
    when(categoryPort.findCategories(ProductId.of(id))).thenReturn(List.of());
    when(imagePort.findImages(ProductId.of(id))).thenReturn(List.of());

    ProductDto dto = service.execute(new ChangeInventoryModeCommand(id, false, null, null, null));

    assertThat(dto.stock()).isNull();
    verify(inventoryPort).disableStockManagement(ProductId.of(id));
  }

  @Test
  void disablingAnAlreadyUnmanagedProductIsANoOp() {
    service = new ChangeInventoryModeService(readPort, inventoryPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.of(product(id, null)));
    when(categoryPort.findCategories(ProductId.of(id))).thenReturn(List.of());
    when(imagePort.findImages(ProductId.of(id))).thenReturn(List.of());

    service.execute(new ChangeInventoryModeCommand(id, false, null, null, null));

    verify(inventoryPort, never()).disableStockManagement(any());
  }

  @Test
  void rejectsChangingInventoryOfAnUnknownProduct() {
    service = new ChangeInventoryModeService(readPort, inventoryPort, categoryPort, imagePort);
    UUID id = UUID.randomUUID();
    when(readPort.findById(ProductId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.execute(new ChangeInventoryModeCommand(id, false, null, null, null)))
        .isInstanceOf(ProductNotFoundException.class);
  }
}
