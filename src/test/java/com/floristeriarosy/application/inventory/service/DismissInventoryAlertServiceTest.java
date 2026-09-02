package com.floristeriarosy.application.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.inventory.command.DismissInventoryAlertCommand;
import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.inventory.port.out.InventoryAlertPort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.domain.exception.inventory.InventoryAlertNotFoundException;
import com.floristeriarosy.domain.exception.inventory.InventoryAlertNotOpenException;
import com.floristeriarosy.domain.model.inventory.InventoryAlert;
import com.floristeriarosy.domain.model.inventory.InventoryAlertType;
import com.floristeriarosy.domain.model.inventory.valueobject.InventoryAlertId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DismissInventoryAlertService}: closes an alert as acknowledged, no action needed
 * (inventory.md, section 3.8: "Descartar").
 */
@ExtendWith(MockitoExtension.class)
class DismissInventoryAlertServiceTest {

  @Mock private InventoryAlertPort alertPort;
  @Mock private ProductReadPort productReadPort;

  private DismissInventoryAlertService service;

  @Test
  void throwsNotFoundWhenTheAlertDoesNotExist() {
    service = new DismissInventoryAlertService(alertPort, productReadPort);
    UUID id = UUID.randomUUID();
    when(alertPort.findById(any(InventoryAlertId.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new DismissInventoryAlertCommand(id, null)))
        .isInstanceOf(InventoryAlertNotFoundException.class);
    verify(alertPort, never()).dismiss(any());
  }

  @Test
  void dismissesAnOpenAlertWhenTheProductNoLongerExists() {
    service = new DismissInventoryAlertService(alertPort, productReadPort);
    InventoryAlert alert =
        InventoryAlert.open(
            InventoryAlertId.newId(), InventoryAlertType.RECONCILIATION_MISMATCH, ProductId.newId(), 8, 5);
    when(alertPort.findById(any(InventoryAlertId.class))).thenReturn(Optional.of(alert));
    when(alertPort.dismiss(any(InventoryAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(productReadPort.findById(alert.productId())).thenReturn(Optional.empty());

    InventoryAlertDto result = service.execute(new DismissInventoryAlertCommand(alert.id().value(), "umbral bajo"));

    assertThat(result.productName()).isNull();
    verify(alertPort).dismiss(alert);
  }

  @Test
  void dismissingAnAlertThatIsAlreadyClosedThrowsNotOpen() {
    service = new DismissInventoryAlertService(alertPort, productReadPort);
    InventoryAlert alert =
        InventoryAlert.open(InventoryAlertId.newId(), InventoryAlertType.LOW_STOCK, ProductId.newId(), 2, 5);
    alert.resolve(null, null, Instant.now());
    when(alertPort.findById(any(InventoryAlertId.class))).thenReturn(Optional.of(alert));

    assertThatThrownBy(() -> service.execute(new DismissInventoryAlertCommand(alert.id().value(), null)))
        .isInstanceOf(InventoryAlertNotOpenException.class);
    verify(alertPort, never()).dismiss(any());
  }
}
