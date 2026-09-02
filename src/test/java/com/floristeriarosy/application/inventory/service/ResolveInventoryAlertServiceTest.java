package com.floristeriarosy.application.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.inventory.command.ResolveInventoryAlertCommand;
import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.inventory.port.out.InventoryAlertPort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.domain.exception.inventory.InventoryAlertNotFoundException;
import com.floristeriarosy.domain.exception.inventory.InventoryAlertNotOpenException;
import com.floristeriarosy.domain.model.inventory.InventoryAlert;
import com.floristeriarosy.domain.model.inventory.InventoryAlertType;
import com.floristeriarosy.domain.model.inventory.valueobject.InventoryAlertId;
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

/**
 * {@link ResolveInventoryAlertService}: closes an alert as fixed (inventory.md, section 3.8:
 * "Resolver").
 */
@ExtendWith(MockitoExtension.class)
class ResolveInventoryAlertServiceTest {

  @Mock private InventoryAlertPort alertPort;
  @Mock private ProductReadPort productReadPort;

  private ResolveInventoryAlertService service;

  private Product product(String name) {
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

  @Test
  void throwsNotFoundWhenTheAlertDoesNotExist() {
    service = new ResolveInventoryAlertService(alertPort, productReadPort);
    UUID id = UUID.randomUUID();
    when(alertPort.findById(any(InventoryAlertId.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new ResolveInventoryAlertCommand(id, null)))
        .isInstanceOf(InventoryAlertNotFoundException.class);
    verify(alertPort, never()).resolve(any());
  }

  @Test
  void resolvesAnOpenAlertAndReturnsItWithTheProductsCurrentName() {
    service = new ResolveInventoryAlertService(alertPort, productReadPort);
    ProductId productId = ProductId.newId();
    InventoryAlert alert = InventoryAlert.open(InventoryAlertId.newId(), InventoryAlertType.LOW_STOCK, productId, 2, 5);
    when(alertPort.findById(any(InventoryAlertId.class))).thenReturn(Optional.of(alert));
    when(alertPort.resolve(any(InventoryAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(productReadPort.findById(productId)).thenReturn(Optional.of(product("Ramo")));

    InventoryAlertDto result = service.execute(new ResolveInventoryAlertCommand(alert.id().value(), "repuesto"));

    assertThat(result.productName()).isEqualTo("Ramo");
    verify(alertPort).resolve(alert);
  }

  @Test
  void resolvingAnAlertThatIsAlreadyClosedThrowsNotOpen() {
    service = new ResolveInventoryAlertService(alertPort, productReadPort);
    InventoryAlert alert =
        InventoryAlert.open(InventoryAlertId.newId(), InventoryAlertType.LOW_STOCK, ProductId.newId(), 2, 5);
    alert.dismiss(null, null, Instant.now());
    when(alertPort.findById(any(InventoryAlertId.class))).thenReturn(Optional.of(alert));

    assertThatThrownBy(() -> service.execute(new ResolveInventoryAlertCommand(alert.id().value(), null)))
        .isInstanceOf(InventoryAlertNotOpenException.class);
    verify(alertPort, never()).resolve(any());
  }
}
