package com.floristeriarosy.infrastructure.web.controller.order;

import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.port.in.GetOrderUseCase;
import com.floristeriarosy.application.order.port.in.GetOrdersUseCase;
import com.floristeriarosy.application.order.query.GetOrderQuery;
import com.floristeriarosy.application.order.query.GetOrdersQuery;
import com.floristeriarosy.infrastructure.security.jwt.AccessTokenJwtClaims;
import com.floristeriarosy.infrastructure.web.response.order.OrderItemResponse;
import com.floristeriarosy.infrastructure.web.response.order.OrderResponse;
import java.util.List;
import java.util.UUID;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /orders/{orderNumber}}, {@code GET /orders} (order.md §4, §6). */
@RestController
@RequestMapping("/api/v1")
public class OrderController {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);

  private final GetOrderUseCase getOrderUseCase;
  private final GetOrdersUseCase getOrdersUseCase;

  /**
   * @param getOrderUseCase loads a single owned order
   * @param getOrdersUseCase lists the authenticated customer's orders
   */
  public OrderController(GetOrderUseCase getOrderUseCase, GetOrdersUseCase getOrdersUseCase) {
    this.getOrderUseCase = getOrderUseCase;
    this.getOrdersUseCase = getOrdersUseCase;
  }

  /**
   * @param orderNumber the business identifier of the order to retrieve
   * @param guestEmail the buyer's email, required to prove ownership when the caller is not
   *     authenticated
   * @return the public order view
   */
  @GetMapping("/orders/{orderNumber}")
  public ResponseEntity<OrderResponse> getOrder(
      @PathVariable String orderNumber,
      @RequestParam(name = "email", required = false) String guestEmail) {
    UUID customerId = resolveCustomerId();
    LOGGER.debug(
        "GET /orders/{} customerId={} guestEmailPresent={}",
        Encode.forJava(orderNumber),
        customerId,
        guestEmail != null);
    OrderDto order =
        getOrderUseCase.execute(new GetOrderQuery(orderNumber, customerId, guestEmail));
    OrderResponse response = toResponse(order);
    LOGGER.debug("GET /orders/{} -> found", Encode.forJava(orderNumber));
    return ResponseEntity.ok(response);
  }

  /**
   * @return the authenticated customer's orders, newest first
   */
  @GetMapping("/orders")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<OrderResponse>> getOrders() {
    UUID customerId = resolveCustomerId();
    LOGGER.debug("GET /orders customerId={}", customerId);
    List<OrderDto> orders = getOrdersUseCase.execute(new GetOrdersQuery(customerId));
    List<OrderResponse> response = orders.stream().map(this::toResponse).toList();
    LOGGER.debug("GET /orders customerId={} -> count={}", customerId, response.size());
    return ResponseEntity.ok(response);
  }

  /**
   * @return the authenticated customer's id, or {@code null} for an anonymous caller or a non-{@code
   *     CUSTOMER} bearer token (order.md §4)
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
   * @param order the application order view
   * @return the HTTP response shape
   */
  private OrderResponse toResponse(OrderDto order) {
    return new OrderResponse(
        order.id(),
        order.orderNumber(),
        order.channel(),
        order.fulfillment(),
        order.status(),
        order.items().stream().map(this::toResponse).toList(),
        order.subtotal(),
        order.shippingCost(),
        order.total(),
        order.placedAt());
  }

  /**
   * @param item a sold line
   * @return the HTTP response shape
   */
  private OrderItemResponse toResponse(OrderDto.Item item) {
    return new OrderItemResponse(
        item.productId(),
        item.productName(),
        item.productAttributes(),
        item.unitPrice(),
        item.discountPrice(),
        item.quantity(),
        item.lineTotal());
  }
}
