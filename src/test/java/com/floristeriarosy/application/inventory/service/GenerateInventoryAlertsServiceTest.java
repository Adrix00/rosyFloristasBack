package com.floristeriarosy.application.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.inventory.dto.LowStockCandidate;
import com.floristeriarosy.application.inventory.dto.ReconciliationMismatch;
import com.floristeriarosy.application.inventory.port.out.InventoryAlertPort;
import com.floristeriarosy.application.inventory.port.out.LowStockPort;
import com.floristeriarosy.application.inventory.port.out.StockMovementReadPort;
import com.floristeriarosy.domain.model.inventory.InventoryAlert;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link GenerateInventoryAlertsService}: the daily job runs both detection queries and opens an
 * alert per result (inventory.md, section 3.8; ADR-013).
 */
@ExtendWith(MockitoExtension.class)
class GenerateInventoryAlertsServiceTest {

  @Mock private StockMovementReadPort stockMovementReadPort;
  @Mock private LowStockPort lowStockPort;
  @Mock private InventoryAlertPort alertPort;

  private GenerateInventoryAlertsService service;

  @Test
  void opensNoAlertsWhenNeitherConditionIsFound() {
    service = new GenerateInventoryAlertsService(stockMovementReadPort, lowStockPort, alertPort);
    when(stockMovementReadPort.findReconciliationMismatches()).thenReturn(List.of());
    when(lowStockPort.findBelowThreshold()).thenReturn(List.of());

    service.execute();

    verify(alertPort, never()).save(any());
  }

  @Test
  void opensAReconciliationMismatchAlertForEveryMismatch() {
    service = new GenerateInventoryAlertsService(stockMovementReadPort, lowStockPort, alertPort);
    UUID productId = UUID.randomUUID();
    when(stockMovementReadPort.findReconciliationMismatches())
        .thenReturn(List.of(new ReconciliationMismatch(productId, 8, 5)));
    when(lowStockPort.findBelowThreshold()).thenReturn(List.of());
    ArgumentCaptor<InventoryAlert> captor = ArgumentCaptor.forClass(InventoryAlert.class);
    when(alertPort.save(captor.capture())).thenReturn(true);

    service.execute();

    assertThat(captor.getValue().productId().value()).isEqualTo(productId);
    assertThat(captor.getValue().observedValue()).isEqualTo(8);
    assertThat(captor.getValue().expectedValue()).isEqualTo(5);
  }

  @Test
  void opensALowStockAlertForEveryCandidate() {
    service = new GenerateInventoryAlertsService(stockMovementReadPort, lowStockPort, alertPort);
    UUID productId = UUID.randomUUID();
    when(stockMovementReadPort.findReconciliationMismatches()).thenReturn(List.of());
    when(lowStockPort.findBelowThreshold()).thenReturn(List.of(new LowStockCandidate(productId, 2, 5)));
    ArgumentCaptor<InventoryAlert> captor = ArgumentCaptor.forClass(InventoryAlert.class);
    when(alertPort.save(captor.capture())).thenReturn(true);

    service.execute();

    assertThat(captor.getValue().productId().value()).isEqualTo(productId);
    assertThat(captor.getValue().observedValue()).isEqualTo(2);
    assertThat(captor.getValue().expectedValue()).isEqualTo(5);
  }

  @Test
  void opensOneAlertPerConditionWhenBothAreDetectedForTheSameProduct() {
    service = new GenerateInventoryAlertsService(stockMovementReadPort, lowStockPort, alertPort);
    UUID productId = UUID.randomUUID();
    when(stockMovementReadPort.findReconciliationMismatches())
        .thenReturn(List.of(new ReconciliationMismatch(productId, 8, 5)));
    when(lowStockPort.findBelowThreshold()).thenReturn(List.of(new LowStockCandidate(productId, 8, 10)));
    when(alertPort.save(any())).thenReturn(true);

    service.execute();

    verify(alertPort, times(2)).save(any());
  }

  @Test
  void aDuplicateSkippedByTheUniqueIndexDoesNotFailTheJob() {
    service = new GenerateInventoryAlertsService(stockMovementReadPort, lowStockPort, alertPort);
    when(stockMovementReadPort.findReconciliationMismatches()).thenReturn(List.of());
    when(lowStockPort.findBelowThreshold())
        .thenReturn(List.of(new LowStockCandidate(UUID.randomUUID(), 2, 5)));
    when(alertPort.save(any())).thenReturn(false);

    assertThatCode(() -> service.execute()).doesNotThrowAnyException();
  }
}
