package com.floristeriarosy.infrastructure.web.controller.cart;

import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.port.in.AddCartItemUseCase;
import com.floristeriarosy.application.cart.port.in.ClearCartUseCase;
import com.floristeriarosy.application.cart.port.in.GetCartUseCase;
import com.floristeriarosy.application.cart.port.in.RemoveCartItemUseCase;
import com.floristeriarosy.application.cart.port.in.UpdateCartItemUseCase;
import com.floristeriarosy.application.cart.port.in.ValidateCartUseCase;
import com.floristeriarosy.infrastructure.security.jwt.AccessTokenJwtClaims;
import com.floristeriarosy.infrastructure.web.mapper.cart.CartWebMapper;
import com.floristeriarosy.infrastructure.web.request.cart.AddCartItemRequest;
import com.floristeriarosy.infrastructure.web.request.cart.UpdateCartItemRequest;
import com.floristeriarosy.infrastructure.web.response.cart.CartResponse;
import com.floristeriarosy.infrastructure.web.response.cart.CartValidationResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code /api/v1/cart} (cart.md, section 4). Public — works with no authentication;
 * with a valid customer JWT, identifies the caller by {@code customer_id} instead of the guest
 * cookie (cart.md, rule 3.1).
 *
 * <p>Builds the guest cart cookie itself: that is HTTP transport, not business logic, same pattern
 * as {@code AuthController}'s refresh-token cookie.
 */
@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

  private static final String CART_SESSION_COOKIE = "cart_session";
  private static final String COOKIE_PATH = "/api/v1";
  private static final Duration GUEST_COOKIE_MAX_AGE = Duration.ofDays(30);

  private static final Logger LOGGER = LoggerFactory.getLogger(CartController.class);

  private final GetCartUseCase getCartUseCase;
  private final AddCartItemUseCase addCartItemUseCase;
  private final UpdateCartItemUseCase updateCartItemUseCase;
  private final RemoveCartItemUseCase removeCartItemUseCase;
  private final ClearCartUseCase clearCartUseCase;
  private final ValidateCartUseCase validateCartUseCase;
  private final CartWebMapper mapper;

  /**
   * @param getCartUseCase backs {@code GET /cart}
   * @param addCartItemUseCase backs {@code POST /cart/items}
   * @param updateCartItemUseCase backs {@code PATCH /cart/items/{productId}}
   * @param removeCartItemUseCase backs {@code DELETE /cart/items/{productId}}
   * @param clearCartUseCase backs {@code DELETE /cart}
   * @param validateCartUseCase backs {@code GET /cart/validation}
   * @param mapper translates Request/Response to/from Command/Query/Dto; the only class in this
   *     controller's call graph allowed to touch an application-layer DTO
   */
  public CartController(
      GetCartUseCase getCartUseCase,
      AddCartItemUseCase addCartItemUseCase,
      UpdateCartItemUseCase updateCartItemUseCase,
      RemoveCartItemUseCase removeCartItemUseCase,
      ClearCartUseCase clearCartUseCase,
      ValidateCartUseCase validateCartUseCase,
      CartWebMapper mapper) {
    this.getCartUseCase = getCartUseCase;
    this.addCartItemUseCase = addCartItemUseCase;
    this.updateCartItemUseCase = updateCartItemUseCase;
    this.removeCartItemUseCase = removeCartItemUseCase;
    this.clearCartUseCase = clearCartUseCase;
    this.validateCartUseCase = validateCartUseCase;
    this.mapper = mapper;
  }

  /**
   * {@code GET /cart} (public): empty, with no row created, if none exists yet (rule 3.1).
   *
   * @param sessionToken the guest cart cookie value, or blank if absent
   * @return 200 with the fully priced cart
   */
  @GetMapping
  public ResponseEntity<CartResponse> get(
      @CookieValue(name = CART_SESSION_COOKIE, required = false, defaultValue = "") String sessionToken) {
    UUID customerId = resolveCustomerId();
    LOGGER.debug("GET /cart hasCustomer={}", customerId != null);
    CartDto dto = getCartUseCase.execute(mapper.toQuery(customerId, sessionToken));
    LOGGER.debug("GET /cart -> 200 itemCount={}", dto.itemCount());
    return withGuestCookie(customerId, dto).body(mapper.toResponse(dto));
  }

  /**
   * {@code POST /cart/items} (public): adds a product, creating the cart if this is its first item.
   *
   * @param sessionToken the guest cart cookie value, or blank if absent
   * @param request the product and quantity to add
   * @return 200 with the resulting cart
   */
  @PostMapping("/items")
  public ResponseEntity<CartResponse> addItem(
      @CookieValue(name = CART_SESSION_COOKIE, required = false, defaultValue = "") String sessionToken,
      @Valid @RequestBody AddCartItemRequest request) {
    UUID customerId = resolveCustomerId();
    LOGGER.debug(
        "POST /cart/items hasCustomer={} productId={} quantity={}",
        customerId != null,
        request.productId(),
        request.quantity());
    CartDto dto = addCartItemUseCase.execute(mapper.toCommand(customerId, sessionToken, request));
    LOGGER.debug("POST /cart/items -> 200 itemCount={}", dto.itemCount());
    return withGuestCookie(customerId, dto).body(mapper.toResponse(dto));
  }

  /**
   * {@code PATCH /cart/items/{productId}} (public): fixes a line's quantity to an exact value.
   *
   * @param sessionToken the guest cart cookie value, or blank if absent
   * @param productId the product to update
   * @param request the exact quantity to set
   * @return 200 with the resulting cart
   */
  @PatchMapping("/items/{productId}")
  public ResponseEntity<CartResponse> updateItem(
      @CookieValue(name = CART_SESSION_COOKIE, required = false, defaultValue = "") String sessionToken,
      @PathVariable UUID productId,
      @Valid @RequestBody UpdateCartItemRequest request) {
    UUID customerId = resolveCustomerId();
    LOGGER.debug(
        "PATCH /cart/items/{} hasCustomer={} quantity={}", productId, customerId != null, request.quantity());
    CartDto dto = updateCartItemUseCase.execute(mapper.toCommand(customerId, sessionToken, productId, request));
    LOGGER.debug("PATCH /cart/items/{} -> 200 itemCount={}", productId, dto.itemCount());
    return withGuestCookie(customerId, dto).body(mapper.toResponse(dto));
  }

  /**
   * {@code DELETE /cart/items/{productId}} (public): returns the resulting cart, not {@code 204}
   * (cart.md, section 4).
   *
   * @param sessionToken the guest cart cookie value, or blank if absent
   * @param productId the product to remove
   * @return 200 with the resulting cart
   */
  @DeleteMapping("/items/{productId}")
  public ResponseEntity<CartResponse> removeItem(
      @CookieValue(name = CART_SESSION_COOKIE, required = false, defaultValue = "") String sessionToken,
      @PathVariable UUID productId) {
    UUID customerId = resolveCustomerId();
    LOGGER.debug("DELETE /cart/items/{} hasCustomer={}", productId, customerId != null);
    CartDto dto = removeCartItemUseCase.execute(mapper.toRemoveCommand(customerId, sessionToken, productId));
    LOGGER.debug("DELETE /cart/items/{} -> 200 itemCount={}", productId, dto.itemCount());
    return withGuestCookie(customerId, dto).body(mapper.toResponse(dto));
  }

  /**
   * {@code DELETE /cart} (public): empties the cart, does not delete the row (cart.md, section 4).
   *
   * @param sessionToken the guest cart cookie value, or blank if absent
   * @return 200 with the resulting, empty cart
   */
  @DeleteMapping
  public ResponseEntity<CartResponse> clear(
      @CookieValue(name = CART_SESSION_COOKIE, required = false, defaultValue = "") String sessionToken) {
    UUID customerId = resolveCustomerId();
    LOGGER.debug("DELETE /cart hasCustomer={}", customerId != null);
    CartDto dto = clearCartUseCase.execute(mapper.toClearCommand(customerId, sessionToken));
    LOGGER.debug("DELETE /cart -> 200 itemCount={}", dto.itemCount());
    return withGuestCookie(customerId, dto).body(mapper.toResponse(dto));
  }

  /**
   * {@code GET /cart/validation} (public): runs rule 3.4 on demand, ahead of checkout.
   *
   * @param sessionToken the guest cart cookie value, or blank if absent
   * @return 200 with the validation result
   */
  @GetMapping("/validation")
  public ResponseEntity<CartValidationResponse> validate(
      @CookieValue(name = CART_SESSION_COOKIE, required = false, defaultValue = "") String sessionToken) {
    UUID customerId = resolveCustomerId();
    LOGGER.debug("GET /cart/validation hasCustomer={}", customerId != null);
    CartValidationResponse response =
        mapper.toValidationResponse(validateCartUseCase.execute(mapper.toValidateCommand(customerId, sessionToken)));
    LOGGER.debug("GET /cart/validation -> 200 valid={}", response.valid());
    return ResponseEntity.ok(response);
  }

  /**
   * @return the authenticated customer's id, or {@code null} for an anonymous caller or an
   *     {@code ADMIN} bearer token (cart.md, rule 3.1: only a {@code CUSTOMER} subject identifies a
   *     cart)
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
   * @param customerId the resolved caller identity, or {@code null} for a guest
   * @param dto the cart returned by the use case just executed
   * @return a 200 response builder, with the guest cart cookie set only when the caller is
   *     anonymous and a cart actually exists (rule 3.1: never set before the first item is added)
   */
  private ResponseEntity.BodyBuilder withGuestCookie(UUID customerId, CartDto dto) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    if (customerId == null && dto.id() != null) {
      builder.header(HttpHeaders.SET_COOKIE, cartSessionCookie(dto.sessionToken()).toString());
    }
    return builder;
  }

  /**
   * @param sessionToken the plaintext cart session token to place in the cookie
   * @return the {@code HttpOnly}, {@code Secure}, {@code SameSite=Lax} cookie (cart.md, section 3.1
   *     — {@code Lax}, not {@code Strict}, since the cart must survive navigation from outside the
   *     site), with a 30-day {@code Max-Age} for the guest cart (rule 3.7)
   */
  private ResponseCookie cartSessionCookie(String sessionToken) {
    return ResponseCookie.from(CART_SESSION_COOKIE, sessionToken)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path(COOKIE_PATH)
        .maxAge(GUEST_COOKIE_MAX_AGE)
        .build();
  }
}
