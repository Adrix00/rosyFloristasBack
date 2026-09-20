package com.floristeriarosy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.application.order.port.in.ReleaseExpiredCheckoutsUseCase;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.payment.dto.GatewayRefundResult;
import com.floristeriarosy.application.payment.port.in.ReceiveRedsysNotificationUseCase;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysOrderId;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysProperties;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysSignature;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Abandoned-checkout expiry (ADR-017) through the real transactional services against a real
 * PostgreSQL. Every checkout carries a managed-stock line, a discounted line and an
 * unmanaged-stock line: the unmanaged one is the exact case that once poisoned a shared transaction
 * (see {@code OrderLifecycleIntegrationTest}). The two sequential orders of a notification and the
 * cleanup meeting the same row are both covered.
 */
@Testcontainers
@SpringBootTest
class AbandonedCheckoutCleanupIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  private static final int RESERVED_MANAGED = 2;
  private static final int STOCK_AFTER_RESERVATION = 8;

  @Autowired private ReleaseExpiredCheckoutsUseCase releaseExpiredCheckoutsUseCase;
  @Autowired private ReceiveRedsysNotificationUseCase receiveRedsysNotificationUseCase;
  @Autowired private CheckoutIdempotencyPort idempotencyPort;
  @Autowired private PiiCryptoPort piiCryptoPort;
  @Autowired private RedsysProperties redsysProperties;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private PaymentGatewayPort paymentGatewayPort;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private Checkout expired;
  private Checkout fresh;

  @BeforeEach
  void seedAnExpiredAndAFreshPendingCheckout() throws Exception {
    jdbcTemplate.update("DELETE FROM idempotency_keys");
    expired = seedCheckout(Instant.now().minusSeconds(60));
    fresh = seedCheckout(Instant.now().plusSeconds(600));
  }

  @Test
  void cleanupReleasesTheExpiredCheckoutAndLeavesTheFreshOneUntouched() {
    int released = releaseExpiredCheckoutsUseCase.execute();

    assertThat(released).isEqualTo(1);
    assertReservationReleased(expired);
    assertThat(rowStatus(expired)).isEqualTo("FAILED");
    assertThat(payloadIsNull(expired)).isTrue();
    assertReservationStillHeld(fresh);
    assertThat(rowStatus(fresh)).isEqualTo("PENDING");
    assertThat(payloadIsNull(fresh)).isFalse();
  }

  @Test
  void runningTheCleanupTwiceReleasesOnlyOnce() {
    releaseExpiredCheckoutsUseCase.execute();
    int secondRun = releaseExpiredCheckoutsUseCase.execute();

    assertThat(secondRun).isZero();
    assertThat(stockOf(expired.managedProduct())).isEqualTo(STOCK_AFTER_RESERVATION + RESERVED_MANAGED);
    assertThat(movementsOf(expired.managedProduct())).isEqualTo(RESERVED_MANAGED);
  }

  @Test
  void anApprovedNotificationAfterTheCleanupRefundsTheChargeAndCreatesNoOrder() {
    releaseExpiredCheckoutsUseCase.execute();
    when(paymentGatewayPort.refundByGatewayOrderId(any(), any()))
        .thenReturn(new GatewayRefundResult(true, null, null));

    receiveRedsysNotificationUseCase.execute(
        approvedNotification(expired), signature(expired, approvedNotification(expired)));

    verify(paymentGatewayPort)
        .refundByGatewayOrderId(RedsysOrderId.from(OrderNumber.of(expired.orderNumber())), new BigDecimal("20.00"));
    assertThat(orderCount(expired)).isZero();
    assertThat(rowStatus(expired)).isEqualTo("FAILED");
    assertThat(stockOf(expired.managedProduct())).isEqualTo(STOCK_AFTER_RESERVATION + RESERVED_MANAGED);
  }

  @Test
  void anApprovedNotificationBeforeTheCleanupWinsTheRowAndTheCleanupThenSkipsIt() {
    receiveRedsysNotificationUseCase.execute(
        approvedNotification(expired), signature(expired, approvedNotification(expired)));

    assertThat(orderCount(expired)).isEqualTo(1);
    assertThat(rowStatus(expired)).isEqualTo("COMPLETED");

    int released = releaseExpiredCheckoutsUseCase.execute();

    assertThat(released).isZero();
    assertReservationStillHeld(expired);
    assertThat(orderCount(expired)).isEqualTo(1);
    verify(paymentGatewayPort, never()).refundByGatewayOrderId(any(), any());
  }

  private Checkout seedCheckout(Instant expiresAt) throws Exception {
    UUID managed = seedProduct(STOCK_AFTER_RESERVATION);
    UUID discounted = seedProduct(5);
    UUID unmanaged = seedProduct(null);
    UUID discountId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO product_discounts (id, product_id, original_price, sale_price, starts_at,"
            + " ends_at, quantity_limit, quantity_sold)"
            + " VALUES (?, ?, 10.00, 8.00, now() - interval '1 day', now() + interval '1 day', 10, 1)",
        discountId,
        discounted);
    String orderNumber =
        "WEB-20260920-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    UUID orderId = UUID.randomUUID();
    String key = UUID.randomUUID().toString();
    PendingCheckoutPayload payload =
        new PendingCheckoutPayload(
            "WEB",
            "NONE",
            orderNumber,
            null,
            "session-" + key,
            "Ana",
            "García",
            key + "@example.com",
            "600111222",
            List.of(
                item(managed, RESERVED_MANAGED, null),
                item(discounted, 1, discountId),
                item(unmanaged, 1, null)),
            null,
            null,
            new BigDecimal("20.00"),
            BigDecimal.ZERO);
    idempotencyPort.insertPending(
        key,
        "fp".getBytes(StandardCharsets.UTF_8),
        orderId,
        piiCryptoPort.encrypt(objectMapper.writeValueAsString(payload)),
        expiresAt);
    return new Checkout(key, orderId, orderNumber, managed, discounted, unmanaged, discountId);
  }

  private PendingCheckoutPayload.Item item(UUID productId, int quantity, UUID discountId) {
    return new PendingCheckoutPayload.Item(
        productId,
        "Rosa",
        Map.of(),
        new BigDecimal("10.00"),
        discountId == null ? null : new BigDecimal("8.00"),
        discountId,
        quantity);
  }

  private UUID seedProduct(Integer stock) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO products (id, name, slug, price, stock, status)"
            + " VALUES (?, ?, ?, 10.00, ?, 'ACTIVE')",
        id,
        "Expiry " + id,
        "expiry-" + id,
        stock);
    return id;
  }

  private String approvedNotification(Checkout checkout) {
    String json =
        "{\"Ds_Order\":\""
            + RedsysOrderId.from(OrderNumber.of(checkout.orderNumber()))
            + "\",\"Ds_Response\":\"0000\",\"Ds_Amount\":\"2000\",\"Ds_AuthorisationCode\":\"auth-1\","
            + "\"Ds_Merchant_MerchantData\":\""
            + checkout.key()
            + "\"}";
    return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  private String signature(Checkout checkout, String encodedParams) {
    return RedsysSignature.sign(
        redsysProperties.secretKey(),
        RedsysOrderId.from(OrderNumber.of(checkout.orderNumber())),
        encodedParams);
  }

  private void assertReservationReleased(Checkout checkout) {
    assertThat(stockOf(checkout.managedProduct())).isEqualTo(STOCK_AFTER_RESERVATION + RESERVED_MANAGED);
    assertThat(movementsOf(checkout.managedProduct())).isEqualTo(RESERVED_MANAGED);
    assertThat(stockOf(checkout.discountedProduct())).isEqualTo(6);
    assertThat(discountSold(checkout)).isZero();
    assertThat(stockOf(checkout.unmanagedProduct())).isNull();
    assertThat(movementsOf(checkout.unmanagedProduct())).isZero();
  }

  private void assertReservationStillHeld(Checkout checkout) {
    assertThat(stockOf(checkout.managedProduct())).isEqualTo(STOCK_AFTER_RESERVATION);
    assertThat(movementsOf(checkout.managedProduct())).isZero();
    assertThat(stockOf(checkout.discountedProduct())).isEqualTo(5);
    assertThat(discountSold(checkout)).isEqualTo(1);
  }

  private Integer stockOf(UUID productId) {
    return jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
  }

  private Integer movementsOf(UUID productId) {
    return jdbcTemplate.queryForObject(
        "SELECT COALESCE(SUM(quantity), 0) FROM stock_movements WHERE product_id = ?", Integer.class, productId);
  }

  private Integer discountSold(Checkout checkout) {
    return jdbcTemplate.queryForObject(
        "SELECT quantity_sold FROM product_discounts WHERE id = ?", Integer.class, checkout.discountId());
  }

  private String rowStatus(Checkout checkout) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM idempotency_keys WHERE idempotency_key = ?", String.class, checkout.key());
  }

  private boolean payloadIsNull(Checkout checkout) {
    return jdbcTemplate.queryForObject(
        "SELECT checkout_payload_encrypted IS NULL FROM idempotency_keys WHERE idempotency_key = ?",
        Boolean.class,
        checkout.key());
  }

  private Integer orderCount(Checkout checkout) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM orders WHERE id = ?", Integer.class, checkout.orderId());
  }

  private record Checkout(
      String key,
      UUID orderId,
      String orderNumber,
      UUID managedProduct,
      UUID discountedProduct,
      UUID unmanagedProduct,
      UUID discountId) {}
}
