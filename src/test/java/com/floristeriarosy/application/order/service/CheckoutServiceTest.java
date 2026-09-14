package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.floristeriarosy.application.order.dto.GeocodedAddress;
import com.floristeriarosy.application.order.dto.IdempotencyLock;
import com.floristeriarosy.application.order.dto.ShippingRate;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.GeocodingPort;
import com.floristeriarosy.application.order.port.out.OrderNumberPort;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.domain.exception.inventory.InventoryInsufficientStockException;
import com.floristeriarosy.domain.exception.order.CheckoutDiscountExhaustedException;
import com.floristeriarosy.domain.exception.order.CheckoutInsufficientStockException;
import com.floristeriarosy.domain.exception.order.DeliveryAddressNotGeocodableException;
import com.floristeriarosy.domain.exception.order.DeliveryOutOfRangeException;
import com.floristeriarosy.domain.exception.order.IdempotencyKeyReusedException;
import com.floristeriarosy.domain.exception.order.IdempotencyOperationInProgressException;
import com.floristeriarosy.domain.exception.order.OrderValidationFailedException;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/** {@link CheckoutService} — Fase 1 (order.md, §3.2, §3.4, §3.7). */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

  @Mock private ValidateCartUseCase validateCartUseCase;
  @Mock private GetCartUseCase getCartUseCase;
  @Mock private GeocodingPort geocodingPort;
  @Mock private ShippingRatePort shippingRatePort;
  @Mock private OrderNumberPort orderNumberPort;
  @Mock private CheckoutIdempotencyPort idempotencyPort;
  @Mock private OrderPaymentPort orderPaymentPort;
  @Mock private PiiCryptoPort piiCryptoPort;
  @Mock private RegisterStockMovementUseCase registerStockMovementUseCase;
  @Mock private DiscountReservationPort discountReservationPort;
  @Mock private ProductReadPort productReadPort;

  private CheckoutService service;

  @BeforeEach
  void setUp() {
    service =
        new CheckoutService(
            validateCartUseCase,
            getCartUseCase,
            geocodingPort,
            shippingRatePort,
            orderNumberPort,
            idempotencyPort,
            orderPaymentPort,
            piiCryptoPort,
            new ObjectMapper(),
            registerStockMovementUseCase,
            discountReservationPort,
            productReadPort);
  }

  private CheckoutCommand pickupCommand(String idempotencyKey) {
    return new CheckoutCommand(
        null,
        "session-token",
        OrderFulfillment.PICKUP,
        PaymentMethod.CARD_ONLINE,
        "tok_visa",
        null,
        "Ana",
        "García",
        "ana@example.com",
        "600111222",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        LocalDate.now().plusDays(2),
        LocalTime.of(10, 0),
        LocalTime.of(11, 0),
        null,
        idempotencyKey);
  }

  private CheckoutCommand deliveryCommand(String idempotencyKey) {
    return new CheckoutCommand(
        null,
        "session-token",
        OrderFulfillment.DELIVERY,
        PaymentMethod.CARD_ONLINE,
        "tok_visa",
        null,
        "Ana",
        "García",
        "ana@example.com",
        "600111222",
        null,
        "Luis",
        "600333444",
        "Gran Vía",
        "1",
        null,
        "28013",
        LocalDate.now().plusDays(2),
        LocalTime.of(10, 0),
        LocalTime.of(11, 0),
        null,
        idempotencyKey);
  }

  private CartDto singleItemCart(UUID productId, UUID discountId, Integer availableQuantity) {
    return new CartDto(
        UUID.randomUUID(),
        null,
        "session-token",
        List.of(
            new CartItemDto(
                productId,
                "Rosa roja",
                "rosa-roja",
                null,
                new BigDecimal("10.00"),
                discountId == null ? new BigDecimal("10.00") : new BigDecimal("8.00"),
                discountId != null,
                Map.of(),
                discountId,
                2,
                discountId == null ? new BigDecimal("20.00") : new BigDecimal("16.00"),
                availableQuantity)),
        discountId == null ? new BigDecimal("20.00") : new BigDecimal("16.00"),
        2);
  }

  private void stubValidCart(UUID productId, UUID discountId, Integer availableQuantity) {
    when(validateCartUseCase.execute(any())).thenReturn(new CartValidationDto(true, List.of(), List.of()));
    when(getCartUseCase.execute(any())).thenReturn(singleItemCart(productId, discountId, availableQuantity));
  }

  @Test
  void pickupSkipsGeocodingAndShippingEntirely() {
    UUID productId = UUID.randomUUID();
    stubValidCart(productId, null, 5);
    when(piiCryptoPort.encrypt(any(String.class)))
        .thenReturn("encrypted".getBytes(StandardCharsets.UTF_8));
    when(orderNumberPort.next(OrderChannel.WEB)).thenReturn(OrderNumber.of("WEB-20260914-000001"));
    when(orderPaymentPort.charge(any(), any(), any(), any(), any(), any()))
        .thenReturn(new ChargeResult.RedirectRequired("https://sis-t.redsys.es/sis/realizarPago", Map.of("k", "v")));

    CheckoutRedirectDto result = service.execute(pickupCommand("idem-1"));

    assertThat(result.redirectUrl()).isEqualTo("https://sis-t.redsys.es/sis/realizarPago");
    verifyNoInteractions(geocodingPort, shippingRatePort);
    verify(registerStockMovementUseCase)
        .execute(productId, StockMovementType.SALE, -2, null, "POST /checkout");
    verify(idempotencyPort).insertPending(any(), any(), any(), any(), any());
  }

  @Test
  void deliveryOutOfRangeIsRejectedBeforeReservingAnything() {
    UUID productId = UUID.randomUUID();
    stubValidCart(productId, null, 5);
    when(geocodingPort.geocode(any(), any(), any()))
        .thenReturn(
            Optional.of(
                new GeocodedAddress(new BigDecimal("40.0"), new BigDecimal("-3.0"), new BigDecimal("15.0"))));

    assertThatExceptionOfType(DeliveryOutOfRangeException.class)
        .isThrownBy(() -> service.execute(deliveryCommand("idem-2")));
    verifyNoInteractions(
        orderNumberPort,
        idempotencyPort,
        orderPaymentPort,
        registerStockMovementUseCase,
        discountReservationPort,
        productReadPort);
  }

  @Test
  void nonGeocodableAddressIsRejected() {
    UUID productId = UUID.randomUUID();
    stubValidCart(productId, null, 5);
    when(geocodingPort.geocode(any(), any(), any())).thenReturn(Optional.empty());

    assertThatExceptionOfType(DeliveryAddressNotGeocodableException.class)
        .isThrownBy(() -> service.execute(deliveryCommand("idem-3")));
  }

  @Test
  void cartValidationFailureIsRejectedBeforeReservation() {
    when(validateCartUseCase.execute(any()))
        .thenReturn(
            new CartValidationDto(
                false,
                List.of(new com.floristeriarosy.application.cart.dto.RemovedCartItemDto(UUID.randomUUID(), "Rosa")),
                List.of()));

    assertThatExceptionOfType(OrderValidationFailedException.class)
        .isThrownBy(() -> service.execute(pickupCommand("idem-4")));
    verifyNoInteractions(
        getCartUseCase,
        geocodingPort,
        shippingRatePort,
        orderNumberPort,
        idempotencyPort,
        orderPaymentPort,
        registerStockMovementUseCase,
        discountReservationPort,
        productReadPort);
  }

  @Test
  void insufficientStockReportsAvailableQuantity() {
    UUID productId = UUID.randomUUID();
    stubValidCart(productId, null, 5);
    when(registerStockMovementUseCase.execute(productId, StockMovementType.SALE, -2, null, "POST /checkout"))
        .thenThrow(new InventoryInsufficientStockException("insufficient"));
    Product product = mock(Product.class);
    when(product.stock()).thenReturn(3);
    when(productReadPort.findById(ProductId.of(productId))).thenReturn(Optional.of(product));

    assertThatExceptionOfType(CheckoutInsufficientStockException.class)
        .isThrownBy(() -> service.execute(pickupCommand("idem-5")))
        .satisfies(e -> assertThat(e.errorDetails().get("availableQuantity")).isEqualTo(3));
  }

  @Test
  void discountExhaustedIsRejected() {
    UUID productId = UUID.randomUUID();
    UUID discountId = UUID.randomUUID();
    stubValidCart(productId, discountId, 5);
    when(discountReservationPort.reserve(DiscountId.of(discountId), 2)).thenReturn(false);

    assertThatExceptionOfType(CheckoutDiscountExhaustedException.class)
        .isThrownBy(() -> service.execute(pickupCommand("idem-6")));
  }

  @Test
  void idempotencyKeyReusedWithDifferentCart() {
    UUID productId = UUID.randomUUID();
    stubValidCart(productId, null, 5);
    when(idempotencyPort.find("idem-reused"))
        .thenReturn(Optional.of(new IdempotencyLock("FAILED", new byte[] {1, 2, 3}, UUID.randomUUID(), null)));

    assertThatExceptionOfType(IdempotencyKeyReusedException.class)
        .isThrownBy(() -> service.execute(pickupCommand("idem-reused")));
  }

  @Test
  void idempotencyKeyAlreadyPendingIsConflict() {
    UUID productId = UUID.randomUUID();
    stubValidCart(productId, null, 5);
    when(idempotencyPort.find("idem-pending"))
        .thenReturn(Optional.of(new IdempotencyLock("PENDING", new byte[0], UUID.randomUUID(), null)));

    assertThatExceptionOfType(IdempotencyOperationInProgressException.class)
        .isThrownBy(() -> service.execute(pickupCommand("idem-pending")));
  }

  @Test
  void idempotencyKeyAlreadyCompletedIsConflict() {
    UUID productId = UUID.randomUUID();
    stubValidCart(productId, null, 5);
    when(idempotencyPort.find("idem-completed"))
        .thenReturn(Optional.of(new IdempotencyLock("COMPLETED", new byte[0], UUID.randomUUID(), null)));

    assertThatExceptionOfType(IdempotencyOperationInProgressException.class)
        .isThrownBy(() -> service.execute(pickupCommand("idem-completed")));
  }

  @Test
  void nonCardOnlinePaymentMethodIsRejectedBeforeReservation() {
    CheckoutCommand command =
        new CheckoutCommand(
            null,
            "session-token",
            OrderFulfillment.PICKUP,
            PaymentMethod.CASH,
            null,
            null,
            "Ana",
            "García",
            "ana@example.com",
            "600111222",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            LocalDate.now().plusDays(2),
            LocalTime.of(10, 0),
            LocalTime.of(11, 0),
            null,
            "idem-cash");

    assertThatExceptionOfType(OrderValidationFailedException.class)
        .isThrownBy(() -> service.execute(command));
    verifyNoInteractions(
        validateCartUseCase,
        getCartUseCase,
        geocodingPort,
        shippingRatePort,
        orderNumberPort,
        idempotencyPort,
        orderPaymentPort,
        registerStockMovementUseCase,
        discountReservationPort,
        productReadPort);
  }

  @Test
  void savedCardPaymentMethodIdIsRejectedBeforeReservation() {
    CheckoutCommand command =
        new CheckoutCommand(
            null,
            "session-token",
            OrderFulfillment.PICKUP,
            PaymentMethod.CARD_ONLINE,
            "tok_visa",
            UUID.randomUUID(),
            "Ana",
            "García",
            "ana@example.com",
            "600111222",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            LocalDate.now().plusDays(2),
            LocalTime.of(10, 0),
            LocalTime.of(11, 0),
            null,
            "idem-saved");

    assertThatExceptionOfType(OrderValidationFailedException.class)
        .isThrownBy(() -> service.execute(command));
    verifyNoInteractions(
        orderNumberPort,
        idempotencyPort,
        orderPaymentPort,
        registerStockMovementUseCase,
        discountReservationPort,
        productReadPort);
  }
}
