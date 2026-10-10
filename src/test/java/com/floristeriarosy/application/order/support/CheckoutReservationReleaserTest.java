package com.floristeriarosy.application.order.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.floristeriarosy.application.discount.port.out.DiscountReservationPort;
import com.floristeriarosy.application.inventory.port.in.RegisterStockMovementUseCase;
import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.domain.exception.inventory.InventoryNotManagedException;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** {@link CheckoutReservationReleaser} — reverses the Fase-1 reservation of a checkout (ADR-017). */
class CheckoutReservationReleaserTest {

  private final RegisterStockMovementUseCase stockUseCase = mock(RegisterStockMovementUseCase.class);
  private final DiscountReservationPort discountPort = mock(DiscountReservationPort.class);
  private final CheckoutReservationReleaser releaser =
      new CheckoutReservationReleaser(stockUseCase, discountPort);

  @Test
  void releasesEveryLinesStockAndTheDiscountOfTheDiscountedOne() {
    UUID plain = UUID.randomUUID();
    UUID discounted = UUID.randomUUID();
    UUID discountId = UUID.randomUUID();

    releaser.release(payload(item(plain, null, 2), item(discounted, discountId, 3)));

    verify(stockUseCase).execute(plain, StockMovementType.ADJUSTMENT, 2, null, "POST /checkout");
    verify(stockUseCase).execute(discounted, StockMovementType.ADJUSTMENT, 3, null, "POST /checkout");
    verify(discountPort).release(DiscountId.of(discountId), 3);
    verify(discountPort, never()).release(any(), eq(2));
  }

  @Test
  void releasesInProductIdOrderWhateverOrderThePayloadHasSoConcurrentReleasesCannotDeadlock() {
    UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");

    releaser.release(payload(item(high, null, 1), item(low, null, 1)));

    InOrder inOrder = inOrder(stockUseCase);
    inOrder.verify(stockUseCase).execute(eq(low), any(), eq(1), any(), any());
    inOrder.verify(stockUseCase).execute(eq(high), any(), eq(1), any(), any());
  }

  @Test
  void anUnmanagedLineDoesNotStopTheRestFromBeingReleased() {
    UUID unmanaged = UUID.randomUUID();
    UUID managed = UUID.randomUUID();
    UUID discountId = UUID.randomUUID();
    doThrow(new InventoryNotManagedException("not managed"))
        .when(stockUseCase)
        .execute(unmanaged, StockMovementType.ADJUSTMENT, 1, null, "POST /checkout");

    releaser.release(payload(item(unmanaged, discountId, 1), item(managed, null, 4)));

    verify(stockUseCase).execute(managed, StockMovementType.ADJUSTMENT, 4, null, "POST /checkout");
    verify(discountPort).release(DiscountId.of(discountId), 1);
  }

  private PendingCheckoutPayload.Item item(UUID productId, UUID discountId, int quantity) {
    return new PendingCheckoutPayload.Item(
        productId,
        "Rosa",
        Map.of(),
        new BigDecimal("10.00"),
        discountId == null ? null : new BigDecimal("8.00"),
        discountId,
        quantity);
  }

  private PendingCheckoutPayload payload(PendingCheckoutPayload.Item... items) {
    return new PendingCheckoutPayload(
        "WEB",
        "NONE",
        "WEB-20260920-000001",
        null,
        "session-1",
        "Ana",
        "García",
        "ana@example.com",
        "600111222",
        List.of(items),
        null,
        null,
        new BigDecimal("20.00"),
        BigDecimal.ZERO);
  }
}
