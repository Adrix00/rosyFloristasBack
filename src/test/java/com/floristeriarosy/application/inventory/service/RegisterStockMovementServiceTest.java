package com.floristeriarosy.application.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.inventory.command.RegisterStockMovementCommand;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.inventory.port.out.ProductStockPort;
import com.floristeriarosy.application.inventory.port.out.StockMovementWritePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.domain.exception.inventory.InventoryInsufficientStockException;
import com.floristeriarosy.domain.exception.inventory.InventoryNotManagedException;
import com.floristeriarosy.domain.model.inventory.StockMovement;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import com.floristeriarosy.domain.model.inventory.valueobject.StockMovementId;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RegisterStockMovementService}: the single write path (inventory.md, section 1, section
 * 3.1, section 3.7).
 */
@ExtendWith(MockitoExtension.class)
class RegisterStockMovementServiceTest {

  @Mock private ProductStockPort stockPort;
  @Mock private StockMovementWritePort movementWritePort;
  @Mock private ProductReadPort productReadPort;

  private RegisterStockMovementService service;

  private Product managedProduct(int stock) {
    String name = "Ramo";
    return Product.reconstitute(
        ProductId.newId(),
        name,
        ProductSlug.generateFrom(name),
        null,
        BigDecimal.TEN,
        stock,
        null,
        ProductStatus.ACTIVE,
        false,
        Map.of(),
        null,
        null);
  }

  private StockMovement savedMovementWith(int resultingStock) {
    return StockMovement.reconstitute(
        StockMovementId.newId(),
        ProductId.newId(),
        StockMovementType.SALE,
        -3,
        resultingStock,
        null,
        null,
        Instant.now());
  }

  @Test
  void initialMovementWritesTheExactStockTheDatabaseSetInitialReturned() {
    service = new RegisterStockMovementService(stockPort, movementWritePort, productReadPort);
    UUID productId = UUID.randomUUID();
    when(stockPort.setInitial(any(ProductId.class), eq(10))).thenReturn(10);
    ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
    when(movementWritePort.save(captor.capture())).thenReturn(savedMovementWith(10));

    StockMovementDto result =
        service.execute(new RegisterStockMovementCommand(productId, StockMovementType.INITIAL, 10, null, null));

    assertThat(captor.getValue().resultingStock()).isEqualTo(10);
    assertThat(result.resultingStock()).isEqualTo(10);
  }

  @Test
  void decrementUsesTheStockTheDatabaseReturnedNeverAValueComputedSeparately() {
    service = new RegisterStockMovementService(stockPort, movementWritePort, productReadPort);
    UUID productId = UUID.randomUUID();
    // The database's actual returned value (7) intentionally does not match a naive
    // "resultingStock = someAssumedPriorStock - quantity" computation: proves the service passes
    // the UPDATE's own RETURNING value through unchanged (inventory.md, section 3.1).
    when(stockPort.decrementConditional(any(ProductId.class), eq(3))).thenReturn(Optional.of(7));
    ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
    when(movementWritePort.save(captor.capture())).thenReturn(savedMovementWith(7));

    service.execute(new RegisterStockMovementCommand(productId, StockMovementType.SALE, -3, null, null));

    assertThat(captor.getValue().resultingStock()).isEqualTo(7);
  }

  @Test
  void purchaseIncrementsStock() {
    service = new RegisterStockMovementService(stockPort, movementWritePort, productReadPort);
    UUID productId = UUID.randomUUID();
    when(stockPort.incrementConditional(any(ProductId.class), eq(5))).thenReturn(Optional.of(15));
    when(movementWritePort.save(any(StockMovement.class))).thenReturn(savedMovementWith(15));

    StockMovementDto result =
        service.execute(new RegisterStockMovementCommand(productId, StockMovementType.PURCHASE, 5, null, null));

    assertThat(result.resultingStock()).isEqualTo(15);
    verify(stockPort, never()).decrementConditional(any(), anyInt());
  }

  @Test
  void positiveAdjustmentIncrementsStock() {
    service = new RegisterStockMovementService(stockPort, movementWritePort, productReadPort);
    UUID productId = UUID.randomUUID();
    when(stockPort.incrementConditional(any(ProductId.class), eq(4))).thenReturn(Optional.of(14));
    when(movementWritePort.save(any(StockMovement.class))).thenReturn(savedMovementWith(14));

    service.execute(new RegisterStockMovementCommand(productId, StockMovementType.ADJUSTMENT, 4, null, "recuento"));

    verify(stockPort).incrementConditional(any(ProductId.class), eq(4));
  }

  @Test
  void negativeAdjustmentDecrementsStock() {
    service = new RegisterStockMovementService(stockPort, movementWritePort, productReadPort);
    UUID productId = UUID.randomUUID();
    when(stockPort.decrementConditional(any(ProductId.class), eq(4))).thenReturn(Optional.of(6));
    when(movementWritePort.save(any(StockMovement.class))).thenReturn(savedMovementWith(6));

    service.execute(new RegisterStockMovementCommand(productId, StockMovementType.ADJUSTMENT, -4, null, "recuento"));

    verify(stockPort).decrementConditional(any(ProductId.class), eq(4));
  }

  @Test
  void aSaleThatWouldTakeStockBelowZeroThrowsInsufficientStockWhenTheProductIsManaged() {
    service = new RegisterStockMovementService(stockPort, movementWritePort, productReadPort);
    UUID productId = UUID.randomUUID();
    when(stockPort.decrementConditional(any(ProductId.class), eq(5))).thenReturn(Optional.empty());
    when(productReadPort.findById(any(ProductId.class))).thenReturn(Optional.of(managedProduct(2)));

    assertThatThrownBy(
            () -> service.execute(new RegisterStockMovementCommand(productId, StockMovementType.SALE, -5, null, null)))
        .isInstanceOf(InventoryInsufficientStockException.class);
    verify(movementWritePort, never()).save(any());
  }

  @Test
  void aDecrementOnAnUnmanagedProductThrowsNotManagedInsteadOfInsufficientStock() {
    service = new RegisterStockMovementService(stockPort, movementWritePort, productReadPort);
    UUID productId = UUID.randomUUID();
    when(stockPort.decrementConditional(any(ProductId.class), eq(5))).thenReturn(Optional.empty());
    when(productReadPort.findById(any(ProductId.class))).thenReturn(Optional.of(unmanagedProductWithNullStock()));

    assertThatThrownBy(
            () ->
                service.execute(
                    new RegisterStockMovementCommand(productId, StockMovementType.WASTE, -5, null, "rota")))
        .isInstanceOf(InventoryNotManagedException.class);
    verify(movementWritePort, never()).save(any());
  }

  @Test
  void aDecrementOnAMissingProductThrowsNotManaged() {
    service = new RegisterStockMovementService(stockPort, movementWritePort, productReadPort);
    UUID productId = UUID.randomUUID();
    when(stockPort.decrementConditional(any(ProductId.class), eq(5))).thenReturn(Optional.empty());
    when(productReadPort.findById(any(ProductId.class))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.execute(new RegisterStockMovementCommand(productId, StockMovementType.SALE, -5, null, null)))
        .isInstanceOf(InventoryNotManagedException.class);
  }

  @Test
  void anIncrementOnAnUnmanagedProductThrowsNotManaged() {
    service = new RegisterStockMovementService(stockPort, movementWritePort, productReadPort);
    UUID productId = UUID.randomUUID();
    when(stockPort.incrementConditional(any(ProductId.class), eq(5))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.execute(new RegisterStockMovementCommand(productId, StockMovementType.PURCHASE, 5, null, null)))
        .isInstanceOf(InventoryNotManagedException.class);
  }

  private Product unmanagedProductWithNullStock() {
    String name = "Ramo";
    return Product.reconstitute(
        ProductId.newId(),
        name,
        ProductSlug.generateFrom(name),
        null,
        BigDecimal.TEN,
        null,
        null,
        ProductStatus.ACTIVE,
        false,
        Map.of(),
        null,
        null);
  }
}
