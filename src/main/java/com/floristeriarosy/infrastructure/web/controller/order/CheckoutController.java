package com.floristeriarosy.infrastructure.web.controller.order;

import com.floristeriarosy.application.order.dto.CheckoutRedirectDto;
import com.floristeriarosy.application.order.dto.CheckoutStatusDto;
import com.floristeriarosy.application.order.port.in.CheckoutUseCase;
import com.floristeriarosy.application.order.port.in.GetCheckoutStatusUseCase;
import com.floristeriarosy.application.order.query.GetCheckoutStatusQuery;
import com.floristeriarosy.infrastructure.security.jwt.AccessTokenJwtClaims;
import com.floristeriarosy.infrastructure.web.mapper.order.CheckoutWebMapper;
import com.floristeriarosy.infrastructure.web.request.order.CheckoutRequest;
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
  private final CheckoutWebMapper mapper;

  /**
   * @param checkoutUseCase runs Fase 1 and builds the redirect
   * @param getCheckoutStatusUseCase reads a checkout attempt's status
   * @param mapper translates Request/Response to/from Command/Dto
   */
  public CheckoutController(
      CheckoutUseCase checkoutUseCase,
      GetCheckoutStatusUseCase getCheckoutStatusUseCase,
      CheckoutWebMapper mapper) {
    this.checkoutUseCase = checkoutUseCase;
    this.getCheckoutStatusUseCase = getCheckoutStatusUseCase;
    this.mapper = mapper;
  }

  /**
   * @param request the checkout details
   * @param idempotencyKey required, ADR-011
   * @param sessionToken the guest cart cookie value, or blank if absent
   * @return 202 with the signed Redsys redirect
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
    CheckoutRedirectDto result =
        checkoutUseCase.execute(mapper.toCommand(request, idempotencyKey, customerId, sessionToken));
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
}
