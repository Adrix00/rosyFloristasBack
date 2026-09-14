package com.floristeriarosy.infrastructure.web.mapper.order;

import com.floristeriarosy.application.order.command.CheckoutCommand;
import com.floristeriarosy.infrastructure.web.request.order.BuyerRequest;
import com.floristeriarosy.infrastructure.web.request.order.CheckoutRequest;
import com.floristeriarosy.infrastructure.web.request.order.DeliveryRequest;
import com.floristeriarosy.infrastructure.web.request.order.PickupRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Only class in {@link com.floristeriarosy.infrastructure.web.controller.order.CheckoutController}'s
 * call graph allowed to touch application-layer DTOs: keeps the controller itself free of anything
 * beyond Request/Response (CLAUDE.md, Logging — pure 1:1 field mapping, not logged).
 */
@Component
public class CheckoutWebMapper {

  /**
   * @param request the incoming checkout request
   * @param idempotencyKey the {@code Idempotency-Key} header value
   * @param customerId the authenticated customer, or {@code null} for a guest
   * @param sessionToken the guest cart cookie value, or blank
   * @return the command to hand to {@code CheckoutUseCase}
   */
  public CheckoutCommand toCommand(
      CheckoutRequest request, String idempotencyKey, UUID customerId, String sessionToken) {
    DeliveryRequest delivery = request.delivery();
    PickupRequest pickup = request.pickup();
    BuyerRequest buyer = request.buyer();
    return new CheckoutCommand(
        customerId,
        customerId == null ? sessionToken : null,
        request.fulfillment(),
        request.paymentMethod(),
        request.paymentMethodToken(),
        request.paymentMethodId(),
        buyer != null ? buyer.firstName() : null,
        buyer != null ? buyer.lastName() : null,
        buyer != null ? buyer.email() : null,
        buyer != null ? buyer.phone() : null,
        request.addressId(),
        delivery != null ? delivery.recipientName() : null,
        delivery != null ? delivery.recipientPhone() : null,
        delivery != null ? delivery.street() : null,
        delivery != null ? delivery.number() : null,
        delivery != null ? delivery.detail() : null,
        delivery != null ? delivery.postalCode() : null,
        delivery != null ? delivery.deliveryDate() : pickup != null ? pickup.deliveryDate() : null,
        delivery != null ? delivery.slotFrom() : pickup != null ? pickup.slotFrom() : null,
        delivery != null ? delivery.slotTo() : pickup != null ? pickup.slotTo() : null,
        delivery != null ? delivery.cardMessage() : null,
        idempotencyKey);
  }
}
