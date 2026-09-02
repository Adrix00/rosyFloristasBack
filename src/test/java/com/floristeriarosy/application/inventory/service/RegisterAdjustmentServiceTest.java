package com.floristeriarosy.application.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.inventory.command.RegisterAdjustmentCommand;
import com.floristeriarosy.application.inventory.command.RegisterStockMovementCommand;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.inventory.port.in.RegisterStockMovementUseCase;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RegisterAdjustmentService}: preserves whichever sign the correction needs, unlike {@code
 * RegisterWasteService} (inventory.md, section 3.6).
 */
@ExtendWith(MockitoExtension.class)
class RegisterAdjustmentServiceTest {

  @Mock private RegisterStockMovementUseCase registerStockMovementUseCase;

  private RegisterAdjustmentService service;

  private StockMovementDto anyDto() {
    return new StockMovementDto(
        UUID.randomUUID(), UUID.randomUUID(), StockMovementType.ADJUSTMENT, 3, 13, null, null, Instant.now());
  }

  @Test
  void preservesAPositiveDelta() {
    service = new RegisterAdjustmentService(registerStockMovementUseCase);
    ArgumentCaptor<RegisterStockMovementCommand> captor = ArgumentCaptor.forClass(RegisterStockMovementCommand.class);
    when(registerStockMovementUseCase.execute(captor.capture())).thenReturn(anyDto());

    service.execute(new RegisterAdjustmentCommand(UUID.randomUUID(), 3, "recuento"));

    assertThat(captor.getValue().type()).isEqualTo(StockMovementType.ADJUSTMENT);
    assertThat(captor.getValue().quantity()).isEqualTo(3);
    assertThat(captor.getValue().adminUserId()).isNull();
  }

  @Test
  void preservesANegativeDelta() {
    service = new RegisterAdjustmentService(registerStockMovementUseCase);
    ArgumentCaptor<RegisterStockMovementCommand> captor = ArgumentCaptor.forClass(RegisterStockMovementCommand.class);
    when(registerStockMovementUseCase.execute(captor.capture())).thenReturn(anyDto());

    service.execute(new RegisterAdjustmentCommand(UUID.randomUUID(), -3, "recuento"));

    assertThat(captor.getValue().quantity()).isEqualTo(-3);
  }
}
