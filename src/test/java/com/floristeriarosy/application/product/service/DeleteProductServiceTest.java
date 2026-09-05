package com.floristeriarosy.application.product.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.product.command.DeleteProductCommand;
import com.floristeriarosy.application.product.dto.ProductDeletionImpact;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.application.product.port.out.ProductWritePort;
import com.floristeriarosy.domain.exception.product.ProductHasHistoryException;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteProductServiceTest {

  @Mock private ProductExistencePort existencePort;
  @Mock private ProductWritePort writePort;

  private DeleteProductService service;

  private static ProductDeletionImpact deletable() {
    return new ProductDeletionImpact(true, List.of(), 0, 0, 0);
  }

  @Test
  void deletesAProductWithNoHistory() {
    service = new DeleteProductService(existencePort, writePort);
    UUID id = UUID.randomUUID();
    when(existencePort.existsById(ProductId.of(id))).thenReturn(true);
    when(existencePort.deletionImpact(ProductId.of(id))).thenReturn(deletable());

    service.execute(new DeleteProductCommand(id));

    verify(writePort).delete(ProductId.of(id));
  }

  @Test
  void rejectsDeletingAnUnknownProduct() {
    service = new DeleteProductService(existencePort, writePort);
    UUID id = UUID.randomUUID();
    when(existencePort.existsById(ProductId.of(id))).thenReturn(false);

    assertThatThrownBy(() -> service.execute(new DeleteProductCommand(id)))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void refusesBeforeDeletingWhenTheImpactPreviewReportsHistory() {
    service = new DeleteProductService(existencePort, writePort);
    UUID id = UUID.randomUUID();
    when(existencePort.existsById(ProductId.of(id))).thenReturn(true);
    when(existencePort.deletionImpact(ProductId.of(id)))
        .thenReturn(new ProductDeletionImpact(false, List.of("ORDERS", "STOCK_MOVEMENTS"), 2, 1, 0));

    assertThatThrownBy(() -> service.execute(new DeleteProductCommand(id)))
        .isInstanceOf(ProductHasHistoryException.class)
        .hasMessageContaining("ORDERS")
        .hasMessageContaining("STOCK_MOVEMENTS");
    verify(writePort, never()).delete(ProductId.of(id));
  }

  @Test
  void stillPropagatesTheHistoryConflictTheWritePortRaisesInARace() {
    service = new DeleteProductService(existencePort, writePort);
    UUID id = UUID.randomUUID();
    when(existencePort.existsById(ProductId.of(id))).thenReturn(true);
    when(existencePort.deletionImpact(ProductId.of(id))).thenReturn(deletable());
    doThrow(new ProductHasHistoryException("has history")).when(writePort).delete(ProductId.of(id));

    assertThatThrownBy(() -> service.execute(new DeleteProductCommand(id)))
        .isInstanceOf(ProductHasHistoryException.class);
  }
}
