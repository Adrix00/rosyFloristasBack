package com.floristeriarosy.application.order.support;

import com.floristeriarosy.application.discount.port.out.DiscountReservationPort;
import com.floristeriarosy.application.inventory.port.in.RegisterStockMovementUseCase;
import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.domain.exception.inventory.InventoryNotManagedException;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reverses the Fase-1 reservation of a {@code POST /checkout} that never became an order: gives
 * back every line's stock ({@code ADJUSTMENT} reversing the {@code SALE}) and every line's discount
 * units. Shared by the declined-notification path ({@code ConfirmCheckoutService}) and the
 * abandoned-checkout cleanup (ADR-017).
 *
 * <p>Placed in {@code application.order.support} for the same reason as {@link
 * OrderReversalComponent}: a plain internal helper with no {@code *UseCase} interface, never swapped.
 * It joins the caller's transaction and never opens one of its own.
 */
@Component
public class CheckoutReservationReleaser {

  private static final Logger LOGGER = LoggerFactory.getLogger(CheckoutReservationReleaser.class);
  private static final String ENDPOINT = "POST /checkout";

  private final RegisterStockMovementUseCase registerStockMovementUseCase;
  private final DiscountReservationPort discountReservationPort;

  /**
   * @param registerStockMovementUseCase records the stock {@code ADJUSTMENT} movements
   * @param discountReservationPort releases the promotional reservations
   */
  public CheckoutReservationReleaser(
      RegisterStockMovementUseCase registerStockMovementUseCase,
      DiscountReservationPort discountReservationPort) {
    this.registerStockMovementUseCase = registerStockMovementUseCase;
    this.discountReservationPort = discountReservationPort;
  }

  /**
   * @param payload the decrypted pending-checkout payload, to reverse the exact reservation made
   */
  public void release(PendingCheckoutPayload payload) {
    LOGGER.debug("release items={}", payload.items().size());
    // Rows are touched in productId order so two transactions sharing products cannot deadlock.
    List<PendingCheckoutPayload.Item> items =
        payload.items().stream().sorted(Comparator.comparing(PendingCheckoutPayload.Item::productId)).toList();
    for (PendingCheckoutPayload.Item item : items) {
      releaseStock(item);
      releaseDiscount(item);
    }
    LOGGER.debug("release -> released");
  }

  /**
   * @param item the pending-checkout line whose stock reservation must be released
   */
  private void releaseStock(PendingCheckoutPayload.Item item) {
    try {
      registerStockMovementUseCase.execute(
          item.productId(), StockMovementType.ADJUSTMENT, item.quantity(), null, ENDPOINT);
      LOGGER.debug("releaseStock productId={} quantity={}", item.productId(), item.quantity());
    } catch (InventoryNotManagedException e) {
      LOGGER.debug("releaseStock skipped productId={} (unmanaged)", item.productId());
    }
  }

  /**
   * @param item the pending-checkout line whose discount reservation must be released
   */
  private void releaseDiscount(PendingCheckoutPayload.Item item) {
    if (item.discountId() == null) {
      return;
    }
    discountReservationPort.release(DiscountId.of(item.discountId()), item.quantity());
    LOGGER.debug(
        "releaseDiscount productId={} discountId={} quantity={}",
        item.productId(),
        item.discountId(),
        item.quantity());
  }
}
