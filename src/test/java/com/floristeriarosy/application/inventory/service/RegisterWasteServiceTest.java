package com.floristeriarosy.application.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.inventory.command.RegisterStockMovementCommand;
import com.floristeriarosy.application.inventory.command.RegisterWasteCommand;
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
 * {@link RegisterWasteService}: forces the negative sign {@code WASTE} always carries, regardless
 * of what the client sends (inventory.md, section 3.5, section 5).
 */
@ExtendWith(MockitoExtension.class)
class RegisterWasteServiceTest {

  @Mock private RegisterStockMovementUseCase registerStockMovementUseCase;

  private RegisterWasteService service;

  private StockMovementDto anyDto() {
    return new StockMovementDto(
        UUID.randomUUID(), UUID.randomUUID(), StockMovementType.WASTE, -3, 7, null, null, Instant.now());
  }

  @Test
  void appliesTheNegativeSignToAPositiveQuantitySentByTheClient() {
    service = new RegisterWasteService(registerStockMovementUseCase);
    UUID productId = UUID.randomUUID();
    ArgumentCaptor<RegisterStockMovementCommand> captor = ArgumentCaptor.forClass(RegisterStockMovementCommand.class);
    when(registerStockMovementUseCase.execute(captor.capture())).thenReturn(anyDto());

    service.execute(new RegisterWasteCommand(productId, 3, "rota"));

    assertThat(captor.getValue().type()).isEqualTo(StockMovementType.WASTE);
    assertThat(captor.getValue().quantity()).isEqualTo(-3);
    assertThat(captor.getValue().note()).isEqualTo("rota");
    assertThat(captor.getValue().adminUserId()).isNull();
  }

  @Test
  void forcesTheNegativeSignEvenIfAnUpstreamCallerAlreadySentANegativeQuantity() {
    service = new RegisterWasteService(registerStockMovementUseCase);
    UUID productId = UUID.randomUUID();
    ArgumentCaptor<RegisterStockMovementCommand> captor = ArgumentCaptor.forClass(RegisterStockMovementCommand.class);
    when(registerStockMovementUseCase.execute(captor.capture())).thenReturn(anyDto());

    service.execute(new RegisterWasteCommand(productId, -3, "rota"));

    assertThat(captor.getValue().quantity()).isEqualTo(-3);
  }
}
