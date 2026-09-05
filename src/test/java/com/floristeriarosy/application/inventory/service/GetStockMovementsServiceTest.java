package com.floristeriarosy.application.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.inventory.port.out.StockMovementReadPort;
import com.floristeriarosy.application.inventory.query.GetStockMovementsQuery;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.port.out.ProductExistencePort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link GetStockMovementsService}: a product's movement history is scoped to a real product. */
@ExtendWith(MockitoExtension.class)
class GetStockMovementsServiceTest {

  @Mock private ProductExistencePort productExistencePort;
  @Mock private StockMovementReadPort readPort;
  @Mock private AdminReadPort adminReadPort;
  @Mock private PiiCryptoPort piiCryptoPort;

  private GetStockMovementsService service;

  @Test
  void throwsProductNotFoundWhenTheProductDoesNotExist() {
    service = new GetStockMovementsService(productExistencePort, readPort, adminReadPort, piiCryptoPort);
    when(productExistencePort.existsById(any(ProductId.class))).thenReturn(false);

    assertThatThrownBy(() -> service.execute(new GetStockMovementsQuery(UUID.randomUUID(), 0, 20)))
        .isInstanceOf(ProductNotFoundException.class);
    verify(readPort, never()).findByProduct(any(), anyInt(), anyInt());
  }

  @Test
  void returnsTheProductsHistoryWhenItExists() {
    service = new GetStockMovementsService(productExistencePort, readPort, adminReadPort, piiCryptoPort);
    UUID productId = UUID.randomUUID();
    when(productExistencePort.existsById(any(ProductId.class))).thenReturn(true);
    PageResult<StockMovementDto> page = new PageResult<>(List.of(), 0, 0, 20);
    when(readPort.findByProduct(any(ProductId.class), eq(0), eq(20))).thenReturn(page);

    PageResult<StockMovementDto> result = service.execute(new GetStockMovementsQuery(productId, 0, 20));

    assertThat(result).isEqualTo(page);
  }

  @Test
  void resolvesTheAdminNameForAMovementThatHasOne() {
    service = new GetStockMovementsService(productExistencePort, readPort, adminReadPort, piiCryptoPort);
    UUID productId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    when(productExistencePort.existsById(any(ProductId.class))).thenReturn(true);
    StockMovementDto withoutName =
        new StockMovementDto(
            UUID.randomUUID(), productId, StockMovementType.ADJUSTMENT, 1, 5, adminId, null, null, Instant.now());
    when(readPort.findByProduct(any(ProductId.class), eq(0), eq(20)))
        .thenReturn(new PageResult<>(List.of(withoutName), 1, 0, 20));
    com.floristeriarosy.domain.model.admin.Admin admin =
        com.floristeriarosy.domain.model.admin.Admin.create(
            com.floristeriarosy.domain.model.admin.valueobject.AdminId.of(adminId),
            "encrypted".getBytes(),
            "hash".getBytes(),
            "argon2",
            com.floristeriarosy.domain.model.admin.AdminRole.ADMIN);
    when(adminReadPort.findById(com.floristeriarosy.domain.model.admin.valueobject.AdminId.of(adminId)))
        .thenReturn(java.util.Optional.of(admin));
    when(piiCryptoPort.decrypt(admin.emailEncrypted())).thenReturn("admin@rosyfloristas.com");

    PageResult<StockMovementDto> result = service.execute(new GetStockMovementsQuery(productId, 0, 20));

    assertThat(result.items().get(0).adminUserName()).isEqualTo("admin@rosyfloristas.com");
  }
}
