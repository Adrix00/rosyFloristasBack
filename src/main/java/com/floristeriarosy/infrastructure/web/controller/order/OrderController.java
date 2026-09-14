package com.floristeriarosy.infrastructure.web.controller.order;

import com.floristeriarosy.application.order.command.CancelOrderCommand;
import com.floristeriarosy.application.order.command.CompleteOrderCommand;
import com.floristeriarosy.application.order.command.PrepareOrderCommand;
import com.floristeriarosy.application.order.command.RejectOrderCommand;
import com.floristeriarosy.application.order.command.ShipOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.port.in.CancelOrderUseCase;
import com.floristeriarosy.application.order.port.in.CompleteOrderUseCase;
import com.floristeriarosy.application.order.port.in.GetOrderUseCase;
import com.floristeriarosy.application.order.port.in.GetOrdersUseCase;
import com.floristeriarosy.application.order.port.in.PrepareOrderUseCase;
import com.floristeriarosy.application.order.port.in.RejectOrderUseCase;
import com.floristeriarosy.application.order.port.in.ShipOrderUseCase;
import com.floristeriarosy.application.order.query.GetOrderQuery;
import com.floristeriarosy.application.order.query.GetOrdersQuery;
import com.floristeriarosy.infrastructure.security.jwt.AccessTokenJwtClaims;
import com.floristeriarosy.infrastructure.security.jwt.CurrentAdminResolver;
import com.floristeriarosy.infrastructure.web.request.order.CancelOrderRequest;
import com.floristeriarosy.infrastructure.web.request.order.RejectOrderRequest;
import com.floristeriarosy.infrastructure.web.response.order.OrderItemResponse;
import com.floristeriarosy.infrastructure.web.response.order.OrderResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  private final PrepareOrderUseCase prepareOrderUseCase;
  private final ShipOrderUseCase shipOrderUseCase;
  private final CompleteOrderUseCase completeOrderUseCase;
  private final RejectOrderUseCase rejectOrderUseCase;
  private final CancelOrderUseCase cancelOrderUseCase;

  /**
   * @param getOrderUseCase loads a single owned order
   * @param getOrdersUseCase lists the authenticated customer's orders
   * @param prepareOrderUseCase moves an order to PREPARING
   * @param shipOrderUseCase moves a DELIVERY order to OUT_FOR_DELIVERY
   * @param completeOrderUseCase marks an order DELIVERED
   * @param rejectOrderUseCase rejects an order
   * @param cancelOrderUseCase cancels an order
   */
  public OrderController(
      GetOrderUseCase getOrderUseCase,
      GetOrdersUseCase getOrdersUseCase,
      PrepareOrderUseCase prepareOrderUseCase,
      ShipOrderUseCase shipOrderUseCase,
      CompleteOrderUseCase completeOrderUseCase,
      RejectOrderUseCase rejectOrderUseCase,
      CancelOrderUseCase cancelOrderUseCase) {
    this.getOrderUseCase = getOrderUseCase;
    this.getOrdersUseCase = getOrdersUseCase;
    this.prepareOrderUseCase = prepareOrderUseCase;
    this.shipOrderUseCase = shipOrderUseCase;
    this.completeOrderUseCase = completeOrderUseCase;
    this.rejectOrderUseCase = rejectOrderUseCase;
    this.cancelOrderUseCase = cancelOrderUseCase;
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
   * @param id the order to move to PREPARING
   * @return the updated order
   */
  @PostMapping("/orders/{id}/prepare")
  public ResponseEntity<OrderResponse> prepare(@PathVariable UUID id) {
    LOGGER.debug("POST /orders/{}/prepare", Encode.forJava(id.toString()));
    OrderDto order = prepareOrderUseCase.execute(new PrepareOrderCommand(id, CurrentAdminResolver.resolve()));
    LOGGER.debug("POST /orders/{}/prepare -> 200", Encode.forJava(id.toString()));
    return ResponseEntity.ok(toResponse(order));
  }

  /**
   * @param id the DELIVERY order to move to OUT_FOR_DELIVERY
   * @return the updated order
   */
  @PostMapping("/orders/{id}/ship")
  public ResponseEntity<OrderResponse> ship(@PathVariable UUID id) {
    LOGGER.debug("POST /orders/{}/ship", Encode.forJava(id.toString()));
    OrderDto order = shipOrderUseCase.execute(new ShipOrderCommand(id, CurrentAdminResolver.resolve()));
    LOGGER.debug("POST /orders/{}/ship -> 200", Encode.forJava(id.toString()));
    return ResponseEntity.ok(toResponse(order));
  }

  /**
   * @param id the order to mark DELIVERED
   * @return the updated order
   */
  @PostMapping("/orders/{id}/complete")
  public ResponseEntity<OrderResponse> complete(@PathVariable UUID id) {
    LOGGER.debug("POST /orders/{}/complete", Encode.forJava(id.toString()));
    OrderDto order = completeOrderUseCase.execute(new CompleteOrderCommand(id, CurrentAdminResolver.resolve()));
    LOGGER.debug("POST /orders/{}/complete -> 200", Encode.forJava(id.toString()));
    return ResponseEntity.ok(toResponse(order));
  }

  /**
   * @param id the order to reject
   * @param request the admin's reason
   * @return the updated order
   */
  @PostMapping("/orders/{id}/reject")
  public ResponseEntity<OrderResponse> reject(
      @PathVariable UUID id, @Valid @RequestBody RejectOrderRequest request) {
    LOGGER.debug("POST /orders/{}/reject", Encode.forJava(id.toString()));
    OrderDto order =
        rejectOrderUseCase.execute(
            new RejectOrderCommand(id, CurrentAdminResolver.resolve(), request.reason()));
    LOGGER.debug("POST /orders/{}/reject -> 200", Encode.forJava(id.toString()));
    return ResponseEntity.ok(toResponse(order));
  }

  /**
   * @param id the order to cancel
   * @param request optional reason (required for admin, optional for customer)
   * @return the updated order
   */
  @PostMapping("/orders/{id}/cancel")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<OrderResponse> cancel(
      @PathVariable UUID id, @RequestBody(required = false) CancelOrderRequest request) {
    LOGGER.debug("POST /orders/{}/cancel", Encode.forJava(id.toString()));
    UUID adminUserId = CurrentAdminResolver.resolve();
    UUID customerId = adminUserId == null ? resolveCustomerId() : null;
    String reason = request == null ? null : request.reason();
    OrderDto order =
        cancelOrderUseCase.execute(new CancelOrderCommand(id, customerId, adminUserId, reason));
    LOGGER.debug("POST /orders/{}/cancel -> 200", Encode.forJava(id.toString()));
    return ResponseEntity.ok(toResponse(order));
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
