package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.dto.CartItemDto;
import com.floristeriarosy.application.cart.dto.CartValidationDto;
import com.floristeriarosy.application.cart.port.in.GetCartUseCase;
import com.floristeriarosy.application.cart.port.in.ValidateCartUseCase;
import com.floristeriarosy.application.discount.port.out.DiscountReservationPort;
import com.floristeriarosy.application.inventory.port.in.RegisterStockMovementUseCase;
import com.floristeriarosy.application.order.command.CheckoutCommand;
import com.floristeriarosy.application.order.dto.ChargeResult;
import com.floristeriarosy.application.order.dto.CheckoutRedirectDto;
import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.application.order.port.in.CheckoutUseCase;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.dto.GeocodedAddress;
import com.floristeriarosy.application.order.dto.IdempotencyLock;
import com.floristeriarosy.application.order.dto.ShippingRate;
import com.floristeriarosy.application.order.port.out.GeocodingPort;
import com.floristeriarosy.application.order.port.out.OrderNumberPort;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.inventory.InventoryInsufficientStockException;
import com.floristeriarosy.domain.exception.inventory.InventoryNotManagedException;
import com.floristeriarosy.domain.exception.order.CheckoutDiscountExhaustedException;
import com.floristeriarosy.domain.exception.order.CheckoutInsufficientStockException;
import com.floristeriarosy.domain.exception.order.DeliveryAddressNotGeocodableException;
import com.floristeriarosy.domain.exception.order.DeliveryOutOfRangeException;
import com.floristeriarosy.domain.exception.order.IdempotencyKeyReusedException;
import com.floristeriarosy.domain.exception.order.OrderValidationFailedException;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Implements {@link CheckoutUseCase} — Fase 1 of order.md §3.2: validate and price the cart,
 * geocode and rate delivery (before anything is reserved, order.md §3.7), reserve stock/discount,
 * store the pending-checkout payload, and hand off to Redsys via {@link OrderPaymentPort}. Never
 * writes to {@code orders} — that is {@code ConfirmCheckoutService} (Phase 3b), driven by Redsys's
 * notification.
 */
@Service
@Transactional
public class CheckoutService implements CheckoutUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(CheckoutService.class);
  private static final String ENDPOINT = "POST /checkout";
  private static final BigDecimal MAX_DELIVERY_DISTANCE_KM = new BigDecimal("10.0");
  private static final java.time.Duration RESERVATION_TTL = java.time.Duration.ofMinutes(10);

  private final ValidateCartUseCase validateCartUseCase;
  private final GetCartUseCase getCartUseCase;
  private final GeocodingPort geocodingPort;
  private final ShippingRatePort shippingRatePort;
  private final OrderNumberPort orderNumberPort;
  private final CheckoutIdempotencyPort idempotencyPort;
  private final OrderPaymentPort orderPaymentPort;
  private final PiiCryptoPort piiCryptoPort;
  private final ObjectMapper objectMapper;
  private final RegisterStockMovementUseCase registerStockMovementUseCase;
  private final DiscountReservationPort discountReservationPort;
  private final ProductReadPort productReadPort;

  /**
   * @param validateCartUseCase rejects a cart with removed or now-insufficient lines (cart.md §3.4)
   * @param getCartUseCase reads the priced lines to sell
   * @param geocodingPort locates a delivery address and its distance from the store
   * @param shippingRatePort rates a delivery distance
   * @param orderNumberPort mints the order number this attempt is diversified by (ADR-017)
   * @param idempotencyPort holds the Fase-1 reservation for the wait on Redsys
   * @param orderPaymentPort builds the signed redirect
   * @param piiCryptoPort encrypts the pending-checkout payload
   * @param objectMapper serializes the pending-checkout payload before encrypting it
   * @param registerStockMovementUseCase reserves stock for every managed line
   * @param discountReservationPort reserves promotional units for discounted lines
   * @param productReadPort reads the current stock for a failed reservation diagnosis
   */
  public CheckoutService(
      ValidateCartUseCase validateCartUseCase,
      GetCartUseCase getCartUseCase,
      GeocodingPort geocodingPort,
      ShippingRatePort shippingRatePort,
      OrderNumberPort orderNumberPort,
      CheckoutIdempotencyPort idempotencyPort,
      OrderPaymentPort orderPaymentPort,
      PiiCryptoPort piiCryptoPort,
      ObjectMapper objectMapper,
      RegisterStockMovementUseCase registerStockMovementUseCase,
      DiscountReservationPort discountReservationPort,
      ProductReadPort productReadPort) {
    this.validateCartUseCase = validateCartUseCase;
    this.getCartUseCase = getCartUseCase;
    this.geocodingPort = geocodingPort;
    this.shippingRatePort = shippingRatePort;
    this.orderNumberPort = orderNumberPort;
    this.idempotencyPort = idempotencyPort;
    this.orderPaymentPort = orderPaymentPort;
    this.piiCryptoPort = piiCryptoPort;
    this.objectMapper = objectMapper.rebuild().build();
    this.registerStockMovementUseCase = registerStockMovementUseCase;
    this.discountReservationPort = discountReservationPort;
    this.productReadPort = productReadPort;
  }

  /**
   * @param command everything needed to reserve and build the redirect
   * @return the signed Redsys redirect
   * @throws DeliveryOutOfRangeException the address is more than 10 km away
   * @throws DeliveryAddressNotGeocodableException Google Maps could not locate the address
   * @throws IdempotencyKeyReusedException the same key was used with a different cart
   * @throws CheckoutInsufficientStockException a managed line no longer has enough stock
   * @throws CheckoutDiscountExhaustedException an active promotion sold out during checkout
   */
  @Override
  public CheckoutRedirectDto execute(CheckoutCommand command) {
    LOGGER.debug(
        "checkout customerId={} fulfillment={} idempotencyKey={}",
        command.customerId(),
        command.fulfillment(),
        Encode.forJava(command.idempotencyKey()));

    ensureCommandValid(command);
    CartValidationDto validation = validateCartUseCase.execute(toValidateCartCommand(command));
    ensureCartValid(validation);
    CartDto cart = getCartUseCase.execute(toGetCartQuery(command));

    GeocodedAddress geocoded =
        command.fulfillment() == OrderFulfillment.DELIVERY ? geocodeDelivery(command) : null;
    ShippingRate rate = geocoded != null ? rateDelivery(geocoded) : null;
    BigDecimal shippingCost = rate != null ? computeShippingCost(cart.subtotal(), rate) : BigDecimal.ZERO;

    byte[] fingerprint = computeFingerprint(command, cart);
    Optional<IdempotencyLock> existing = idempotencyPort.find(command.idempotencyKey());
    if (existing.isPresent()) {
      return resumeExisting(existing.get(), fingerprint, command, cart, geocoded, shippingCost);
    }

    return reserveAndRedirect(command, cart, geocoded, shippingCost, fingerprint, false);
  }

  /**
   * @param command the incoming command
   * @throws OrderValidationFailedException the cross-field rule failed (order.md, §5)
   */
  private void ensureCommandValid(CheckoutCommand command) {
    boolean hasToken = command.paymentMethodToken() != null;
    boolean hasSavedCard = command.paymentMethodId() != null;
    if (hasToken == hasSavedCard) {
      throw new OrderValidationFailedException(
          "Exactly one of paymentMethodToken/paymentMethodId is required");
    }
    if (command.customerId() == null && command.buyerFirstName() == null) {
      throw new OrderValidationFailedException("Buyer details are required for guest checkout");
    }
    if (command.fulfillment() == OrderFulfillment.DELIVERY) {
      if (command.addressId() == null
          && (command.street() == null
              || command.number() == null
              || command.postalCode() == null)) {
        throw new OrderValidationFailedException(
            "Delivery details or a saved address are required for delivery checkout");
      }
      return;
    }
    if (command.fulfillment() == OrderFulfillment.PICKUP && command.deliveryDate() == null) {
      throw new OrderValidationFailedException("Pickup details are required");
    }
  }

  /**
   * @param validation the result of revalidating the cart before payment
   * @throws OrderValidationFailedException items were removed or stock is insufficient
   */
  private void ensureCartValid(CartValidationDto validation) {
    if (!validation.removedItems().isEmpty()) {
      throw new OrderValidationFailedException("Some items are no longer available");
    }
    if (!validation.insufficientStockItems().isEmpty()) {
      throw new CheckoutInsufficientStockException(
          "Some items have insufficient stock", validation.insufficientStockItems().get(0).availableQuantity());
    }
  }

  /**
   * @param lock the existing lock for this key
   * @param fingerprint this attempt's fingerprint
   * @param command the incoming command
   * @param cart the priced cart
   * @param geocoded the geocoded delivery, or {@code null}
   * @param shippingCost the computed shipping cost
   * @return the redirect for a fresh {@code FAILED}-retry; an already-{@code PENDING}/{@code
   *     COMPLETED} key never reaches Fase 1 again, per order.md §10
   * @throws IdempotencyKeyReusedException the fingerprint changed
   */
  private CheckoutRedirectDto resumeExisting(
      IdempotencyLock lock,
      byte[] fingerprint,
      CheckoutCommand command,
      CartDto cart,
      GeocodedAddress geocoded,
      BigDecimal shippingCost) {
    if (!MessageDigest.isEqual(lock.requestFingerprint(), fingerprint)) {
      throw new IdempotencyKeyReusedException(
          "Idempotency-Key " + command.idempotencyKey() + " was reused with a different cart");
    }
    if (!"FAILED".equals(lock.status())) {
      throw new IllegalStateException(
          "Idempotency-Key "
              + command.idempotencyKey()
              + " is "
              + lock.status()
              + " — the controller must handle this before Fase 1 runs again");
    }
    return reserveAndRedirect(command, cart, geocoded, shippingCost, fingerprint, true);
  }

  /**
   * @param command the incoming command
   * @param cart the priced cart
   * @param geocoded the geocoded delivery, or {@code null}
   * @param shippingCost the computed shipping cost
   * @param fingerprint this attempt's fingerprint
   * @param isRetry whether this re-arms a {@code FAILED} row instead of inserting fresh
   * @return the signed Redsys redirect
   */
  private CheckoutRedirectDto reserveAndRedirect(
      CheckoutCommand command,
      CartDto cart,
      GeocodedAddress geocoded,
      BigDecimal shippingCost,
      byte[] fingerprint,
      boolean isRetry) {
    UUID orderId = UUID.randomUUID();
    OrderNumber orderNumber = orderNumberPort.next(OrderChannel.WEB);

    reserveInventoryAndDiscounts(cart.items());

    PendingCheckoutPayload payload = buildPendingPayload(command, cart, geocoded, shippingCost);
    byte[] encryptedPayload = piiCryptoPort.encrypt(serialize(payload));
    Instant expiresAt = Instant.now().plus(RESERVATION_TTL);
    if (isRetry) {
      idempotencyPort.resetToPending(command.idempotencyKey(), fingerprint, orderId, encryptedPayload, expiresAt);
    } else {
      idempotencyPort.insertPending(command.idempotencyKey(), fingerprint, orderId, encryptedPayload, expiresAt);
    }

    ChargeResult result =
        orderPaymentPort.charge(
            OrderId.of(orderId),
            orderNumber,
            cart.subtotal().add(shippingCost),
            command.paymentMethod(),
            command.paymentMethodToken(),
            command.idempotencyKey());
    ChargeResult.RedirectRequired redirect = (ChargeResult.RedirectRequired) result;
    LOGGER.debug(
        "checkout idempotencyKey={} -> redirect built", Encode.forJava(command.idempotencyKey()));
    return new CheckoutRedirectDto(redirect.redirectUrl(), redirect.formFields());
  }

  /**
   * @param items the cart lines to reserve stock and discount for
   */
  private void reserveInventoryAndDiscounts(List<CartItemDto> items) {
    for (CartItemDto item : items) {
      reserveStock(item);
      reserveDiscount(item);
    }
  }

  /**
   * @param item the cart line to reserve stock for
   * @throws CheckoutInsufficientStockException the product is unmanaged or has too little stock
   */
  private void reserveStock(CartItemDto item) {
    if (item.availableQuantity() == null) {
      LOGGER.debug(
          "checkout reserveStock skipped productId={} quantity={} (unmanaged)",
          item.productId(),
          item.quantity());
      return;
    }
    try {
      registerStockMovementUseCase.execute(
          item.productId(), StockMovementType.SALE, -item.quantity(), null, ENDPOINT);
      LOGGER.debug(
          "checkout reserveStock productId={} quantity={}", item.productId(), item.quantity());
    } catch (InventoryInsufficientStockException e) {
      int available = readAvailableStock(item.productId());
      throw new CheckoutInsufficientStockException(
          "Product " + item.productId() + " has insufficient stock", available);
    } catch (InventoryNotManagedException e) {
      throw new CheckoutInsufficientStockException(
          "Product " + item.productId() + " is no longer available", 0);
    }
  }

  /**
   * @param productId the product whose reservation failed
   * @return the current stock, or {@code 0} if the product disappeared
   */
  private int readAvailableStock(UUID productId) {
    return productReadPort
        .findById(ProductId.of(productId))
        .map(Product::stock)
        .orElse(0);
  }

  /**
   * @param item the cart line to reserve promotion units for
   * @throws CheckoutDiscountExhaustedException the promotion sold out between pricing and payment
   */
  private void reserveDiscount(CartItemDto item) {
    if (item.discountId() == null) {
      return;
    }
    boolean reserved = discountReservationPort.reserve(DiscountId.of(item.discountId()), item.quantity());
    if (!reserved) {
      throw new CheckoutDiscountExhaustedException(
          "Discount " + item.discountId() + " is no longer available");
    }
    LOGGER.debug(
        "checkout reserveDiscount productId={} discountId={} quantity={}",
        item.productId(),
        item.discountId(),
        item.quantity());
  }

  /**
   * @param command the incoming command
   * @return the delivery address's coordinates and distance from the store
   * @throws DeliveryAddressNotGeocodableException Google Maps could not locate it
   */
  private GeocodedAddress geocodeDelivery(CheckoutCommand command) {
    return geocodingPort
        .geocode(command.street(), command.number(), command.postalCode())
        .orElseThrow(() -> new DeliveryAddressNotGeocodableException("Could not locate the delivery address"));
  }

  /**
   * @param geocoded the geocoded delivery address
   * @return the applicable shipping rate
   * @throws DeliveryOutOfRangeException no band covers this distance
   */
  private ShippingRate rateDelivery(GeocodedAddress geocoded) {
    if (geocoded.distanceKm().compareTo(MAX_DELIVERY_DISTANCE_KM) > 0) {
      throw new DeliveryOutOfRangeException(
          "Delivery address is " + geocoded.distanceKm() + " km away, beyond the 10 km limit");
    }
    return shippingRatePort
        .findRateForDistance(geocoded.distanceKm())
        .orElseThrow(() -> new DeliveryOutOfRangeException("No shipping band covers " + geocoded.distanceKm() + " km"));
  }

  /**
   * @param subtotal the cart's subtotal, compared against {@code free_from_amount} (order.md §3.7:
   *     against the subtotal, before shipping)
   * @param rate the applicable band
   * @return zero if free-shipping applies, otherwise the band's price
   */
  private BigDecimal computeShippingCost(BigDecimal subtotal, ShippingRate rate) {
    boolean freeShipping = rate.freeFromAmount() != null && subtotal.compareTo(rate.freeFromAmount()) >= 0;
    return freeShipping ? BigDecimal.ZERO : rate.price();
  }

  /**
   * @param command the incoming command
   * @return the {@code ValidateCartUseCase} command, built from the same identity fields
   */
  private com.floristeriarosy.application.cart.command.ValidateCartCommand toValidateCartCommand(
      CheckoutCommand command) {
    return new com.floristeriarosy.application.cart.command.ValidateCartCommand(
        command.customerId(), command.sessionToken());
  }

  /**
   * @param command the incoming command
   * @return the {@code GetCartUseCase} query, built from the same identity fields
   */
  private com.floristeriarosy.application.cart.query.GetCartQuery toGetCartQuery(CheckoutCommand command) {
    return new com.floristeriarosy.application.cart.query.GetCartQuery(
        command.customerId(), command.sessionToken());
  }

  /**
   * @param command the incoming command
   * @param cart the priced cart
   * @return the SHA-256 fingerprint (ADR-011): the request plus the cart's contents
   */
  private byte[] computeFingerprint(CheckoutCommand command, CartDto cart) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(serialize(command).getBytes(StandardCharsets.UTF_8));
      digest.update(serialize(cart).getBytes(StandardCharsets.UTF_8));
      return digest.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 must be available on every JVM", e);
    }
  }

  /**
   * @param command the incoming command
   * @param cart the priced cart
   * @param geocoded the geocoded delivery, or {@code null}
   * @param shippingCost the computed shipping cost
   * @return the payload to encrypt into {@code idempotency_keys}
   */
  private PendingCheckoutPayload buildPendingPayload(
      CheckoutCommand command, CartDto cart, GeocodedAddress geocoded, BigDecimal shippingCost) {
    List<PendingCheckoutPayload.Item> items =
        cart.items().stream()
            .map(
                item ->
                    new PendingCheckoutPayload.Item(
                        item.productId(),
                        item.productName(),
                        item.productAttributes(),
                        item.unitPrice(),
                        item.onSale() ? item.effectivePrice() : null,
                        item.discountId(),
                        item.quantity()))
            .toList();
    PendingCheckoutPayload.Delivery delivery =
        command.fulfillment() == OrderFulfillment.DELIVERY
            ? new PendingCheckoutPayload.Delivery(
                command.recipientName(),
                command.recipientPhone(),
                command.street(),
                command.number(),
                command.detail(),
                command.postalCode(),
                geocoded.latitude(),
                geocoded.longitude(),
                geocoded.distanceKm(),
                command.deliveryDate(),
                command.slotFrom(),
                command.slotTo(),
                command.cardMessage())
            : null;
    PendingCheckoutPayload.Pickup pickup =
        command.fulfillment() == OrderFulfillment.PICKUP
            ? new PendingCheckoutPayload.Pickup(command.deliveryDate(), command.slotFrom(), command.slotTo())
            : null;
    return new PendingCheckoutPayload(
        OrderChannel.WEB.name(),
        command.fulfillment().name(),
        command.customerId(),
        command.buyerFirstName(),
        command.buyerLastName(),
        command.buyerEmail(),
        command.buyerPhone(),
        items,
        delivery,
        pickup,
        cart.subtotal(),
        shippingCost);
  }

  /**
   * @param value the object to serialize
   * @return its JSON representation
   */
  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalStateException("Could not serialize " + value.getClass().getSimpleName(), e);
    }
  }
}
