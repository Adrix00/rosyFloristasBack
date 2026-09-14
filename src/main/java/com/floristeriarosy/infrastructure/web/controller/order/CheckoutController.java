package com.floristeriarosy.infrastructure.web.controller.order;

import com.floristeriarosy.application.order.command.CheckoutCommand;
import com.floristeriarosy.application.order.dto.CheckoutRedirectDto;
import com.floristeriarosy.application.order.dto.CheckoutStatusDto;
import com.floristeriarosy.application.order.port.in.CheckoutUseCase;
import com.floristeriarosy.application.order.port.in.GetCheckoutStatusUseCase;
import com.floristeriarosy.application.order.query.GetCheckoutStatusQuery;
import com.floristeriarosy.domain.exception.order.OrderValidationFailedException;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.infrastructure.security.jwt.AccessTokenJwtClaims;
import com.floristeriarosy.infrastructure.web.request.order.BuyerRequest;
import com.floristeriarosy.infrastructure.web.request.order.CheckoutRequest;
import com.floristeriarosy.infrastructure.web.request.order.DeliveryRequest;
import com.floristeriarosy.infrastructure.web.request.order.PickupRequest;
import com.floristeriarosy.infrastructure.web.response.order.CheckoutResponse;
import com.floristeriarosy.infrastructure.web.response.order.CheckoutStatusResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /checkout}, {@code GET /checkout/{idempotencyKey}/status} (order.md, §4). */
@RestController
@RequestMapping("/api/v1")
public class CheckoutController {

  private static final String CART_SESSION_COOKIE = "cart_session";

  private static final Logger LOGGER = LoggerFactory.getLogger(CheckoutController.class);

  private final CheckoutUseCase checkoutUseCase;
  private final GetCheckoutStatusUseCase getCheckoutStatusUseCase;

  /**
   * @param checkoutUseCase runs Fase 1 and builds the redirect
   * @param getCheckoutStatusUseCase reads a checkout attempt's status
   */
  public CheckoutController(
      CheckoutUseCase checkoutUseCase, GetCheckoutStatusUseCase getCheckoutStatusUseCase) {
    this.checkoutUseCase = checkoutUseCase;
    this.getCheckoutStatusUseCase = getCheckoutStatusUseCase;
  }

  /**
   * @param request the checkout details
   * @param idempotencyKey required, ADR-011
   * @param sessionToken the guest cart cookie value, or blank if absent
   * @return 202 with the signed Redsys redirect
   * @throws OrderValidationFailedException the cross-field rule failed (order.md, §5)
   */
  @PostMapping("/checkout")
  public ResponseEntity<CheckoutResponse> checkout(
      @Valid @RequestBody CheckoutRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @CookieValue(name = CART_SESSION_COOKIE, required = false, defaultValue = "") String sessionToken) {
    UUID customerId = resolveCustomerId();
    LOGGER.debug(
        "POST /checkout fulfillment={} idempotencyKey={}",
        request.fulfillment(),
        Encode.forJava(idempotencyKey));
    requireValidRequest(request, customerId);
    CheckoutRedirectDto result =
        checkoutUseCase.execute(toCommand(request, idempotencyKey, customerId, sessionToken));
    LOGGER.debug("POST /checkout idempotencyKey={} -> redirect built", Encode.forJava(idempotencyKey));
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(new CheckoutResponse(result.redirectUrl(), result.formFields()));
  }

  /**
   * @param idempotencyKey the key used on the original {@code POST /checkout}
   * @return the current status
   */
  @GetMapping("/checkout/{idempotencyKey}/status")
  public ResponseEntity<CheckoutStatusResponse> getStatus(
      @PathVariable String idempotencyKey) {
    LOGGER.debug("GET /checkout/{}/status", Encode.forJava(idempotencyKey));
    CheckoutStatusDto result =
        getCheckoutStatusUseCase.execute(new GetCheckoutStatusQuery(idempotencyKey));
    return ResponseEntity.ok(
        new CheckoutStatusResponse(result.status(), result.orderNumber(), result.reason()));
  }

  /**
   * @return the authenticated customer's id, or {@code null} for an anonymous caller or a non-{@code
   *     CUSTOMER} bearer token (cart.md, rule 3.1)
   */
  private UUID resolveCustomerId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      Jwt jwt = jwtAuthentication.getToken();
      if ("CUSTOMER".equals(jwt.getClaimAsString(AccessTokenJwtClaims.SUBJECT_TYPE))) {
        return UUID.fromString(jwt.getSubject());
      }
    }
    return null;
  }

  /**
   * @param request the incoming checkout request
   * @param customerId the resolved caller identity, or {@code null} for a guest
   * @throws OrderValidationFailedException the cross-field rule failed
   */
  private void requireValidRequest(CheckoutRequest request, UUID customerId) {
    requireExactlyOnePaymentMethodReference(request);
    if (customerId == null && request.buyer() == null) {
      throw new OrderValidationFailedException("Buyer details are required for guest checkout");
    }
    if (request.fulfillment() == OrderFulfillment.DELIVERY) {
      if (request.addressId() == null && request.delivery() == null) {
        throw new OrderValidationFailedException(
            "Delivery details or a saved address are required for delivery checkout");
      }
      return;
    }
    if (request.fulfillment() == OrderFulfillment.PICKUP && request.pickup() == null) {
      throw new OrderValidationFailedException("Pickup details are required");
    }
  }

  /**
   * @param request the incoming checkout request
   * @throws OrderValidationFailedException the cross-field rule failed (order.md, §5)
   */
  private void requireExactlyOnePaymentMethodReference(CheckoutRequest request) {
    boolean hasToken = request.paymentMethodToken() != null;
    boolean hasSavedCard = request.paymentMethodId() != null;
    if (hasToken == hasSavedCard) {
      throw new OrderValidationFailedException(
          "Exactly one of paymentMethodToken/paymentMethodId is required");
    }
  }

  /**
   * @param request the incoming checkout request
   * @param idempotencyKey the {@code Idempotency-Key} header
   * @param customerId the logged-in customer, or {@code null}
   * @param sessionToken the guest cart cookie value, or blank
   * @return the application command
   */
  private CheckoutCommand toCommand(
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
