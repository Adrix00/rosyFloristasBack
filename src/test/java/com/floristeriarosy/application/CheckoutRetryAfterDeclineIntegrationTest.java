package com.floristeriarosy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.application.order.command.CheckoutCommand;
import com.floristeriarosy.application.order.port.in.CheckoutUseCase;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.support.PendingCheckoutPayloadReader;
import com.floristeriarosy.application.payment.port.in.ReceiveRedsysNotificationUseCase;
import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;
import com.floristeriarosy.domain.exception.order.IdempotencyKeyReusedException;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysOrderId;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysProperties;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysSignature;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The retry-after-decline money path of order.md §10, end to end through the real services: a
 * declined Redsys notification leaves the row {@code FAILED} with its reservation released, and the
 * same {@code Idempotency-Key} with the same cart restarts from Fase 1 (a new order number, a fresh
 * reservation), while a changed cart is refused with {@code IDEMPOTENCY_KEY_REUSED}. Nothing is
 * mocked: the Redsys redirect is a pure computation and the notification is signed with the
 * configured merchant key.
 */
@Testcontainers
@SpringBootTest
class CheckoutRetryAfterDeclineIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  private static final int INITIAL_STOCK = 10;
  private static final int QUANTITY = 2;

  @Autowired private CheckoutUseCase checkoutUseCase;
  @Autowired private ReceiveRedsysNotificationUseCase receiveRedsysNotificationUseCase;
  @Autowired private CheckoutIdempotencyPort idempotencyPort;
  @Autowired private PendingCheckoutPayloadReader payloadReader;
  @Autowired private RedsysProperties redsysProperties;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID productId;
  private UUID cartId;
  private String sessionToken;
  private String key;

  @BeforeEach
  void seedAVisibleProductAndACartWithTwoUnits() {
    productId = UUID.randomUUID();
    cartId = UUID.randomUUID();
    sessionToken = UUID.randomUUID().toString();
    key = UUID.randomUUID().toString();
    UUID categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO categories (id, name, slug, status) VALUES (?, 'Ramos', ?, 'ACTIVE')",
        categoryId,
        "ramos-" + categoryId);
    jdbcTemplate.update(
        "INSERT INTO products (id, name, slug, price, stock, status)"
            + " VALUES (?, ?, ?, 10.00, ?, 'ACTIVE')",
        productId,
        "Retry " + productId,
        "retry-" + productId,
        INITIAL_STOCK);
    jdbcTemplate.update(
        "INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)", productId, categoryId);
    jdbcTemplate.update(
        "INSERT INTO carts (id, session_token, expires_at) VALUES (?, ?, now() + interval '1 day')",
        cartId,
        sessionToken);
    jdbcTemplate.update(
        "INSERT INTO cart_items (id, cart_id, product_id, quantity) VALUES (?, ?, ?, ?)",
        UUID.randomUUID(),
        cartId,
        productId,
        QUANTITY);
  }

  @Test
  void theSameKeyAndCartAfterADeclineRestartsFromFase1WithANewOrderNumberAndAFreshReservation() {
    checkoutUseCase.execute(command());
    String firstOrderNumber = orderNumberOfPendingRow();
    UUID firstResourceId = resourceId();
    assertThat(rowStatus()).isEqualTo("PENDING");
    assertThat(stock()).isEqualTo(INITIAL_STOCK - QUANTITY);

    decline(firstOrderNumber);

    assertThat(rowStatus()).isEqualTo("FAILED");
    assertThat(payloadIsNull()).isTrue();
    assertThat(stock()).isEqualTo(INITIAL_STOCK);

    checkoutUseCase.execute(command());

    assertThat(rowStatus()).isEqualTo("PENDING");
    assertThat(orderNumberOfPendingRow()).isNotEqualTo(firstOrderNumber);
    assertThat(resourceId()).isNotEqualTo(firstResourceId);
    assertThat(stock()).isEqualTo(INITIAL_STOCK - QUANTITY);
    assertThat(payloadIsNull()).isFalse();
  }

  @Test
  void theSameKeyWithAChangedCartAfterADeclineIsRefusedAndReservesNothing() {
    checkoutUseCase.execute(command());
    decline(orderNumberOfPendingRow());
    jdbcTemplate.update("UPDATE cart_items SET quantity = ? WHERE cart_id = ?", QUANTITY + 1, cartId);

    assertThatThrownBy(() -> checkoutUseCase.execute(command()))
        .isInstanceOf(IdempotencyKeyReusedException.class)
        .isInstanceOf(UnprocessableException.class)
        .satisfies(e -> assertThat(((HasErrorCode) e).errorCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));

    assertThat(rowStatus()).isEqualTo("FAILED");
    assertThat(stock()).isEqualTo(INITIAL_STOCK);
  }

  private CheckoutCommand command() {
    return new CheckoutCommand(
        null,
        sessionToken,
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
        LocalTime.of(12, 0),
        null,
        key);
  }

  private void decline(String orderNumber) {
    String redsysOrderId = RedsysOrderId.from(OrderNumber.of(orderNumber));
    String json =
        "{\"Ds_Order\":\""
            + redsysOrderId
            + "\",\"Ds_Response\":\"0190\",\"Ds_Amount\":\"2000\",\"Ds_Merchant_MerchantData\":\""
            + key
            + "\"}";
    String params = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    receiveRedsysNotificationUseCase.execute(
        params, RedsysSignature.sign(redsysProperties.secretKey(), redsysOrderId, params));
  }

  private String orderNumberOfPendingRow() {
    byte[] payload = idempotencyPort.find(key).orElseThrow().checkoutPayloadEncrypted();
    return payloadReader.read(payload).orderNumber();
  }

  private UUID resourceId() {
    return idempotencyPort.find(key).orElseThrow().resourceId();
  }

  private String rowStatus() {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM idempotency_keys WHERE idempotency_key = ?", String.class, key);
  }

  private boolean payloadIsNull() {
    return jdbcTemplate.queryForObject(
        "SELECT checkout_payload_encrypted IS NULL FROM idempotency_keys WHERE idempotency_key = ?",
        Boolean.class,
        key);
  }

  private Integer stock() {
    return jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
  }
}
