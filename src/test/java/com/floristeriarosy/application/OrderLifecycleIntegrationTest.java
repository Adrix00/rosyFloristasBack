package com.floristeriarosy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.command.CancelOrderCommand;
import com.floristeriarosy.application.order.command.CompleteOrderCommand;
import com.floristeriarosy.application.order.command.PrepareOrderCommand;
import com.floristeriarosy.application.order.command.RejectOrderCommand;
import com.floristeriarosy.application.order.command.ShipOrderCommand;
import com.floristeriarosy.application.order.port.in.CancelOrderUseCase;
import com.floristeriarosy.application.order.port.in.CompleteOrderUseCase;
import com.floristeriarosy.application.order.port.in.PrepareOrderUseCase;
import com.floristeriarosy.application.order.port.in.RejectOrderUseCase;
import com.floristeriarosy.application.order.port.in.ShipOrderUseCase;
import com.floristeriarosy.application.payment.dto.GatewayRefundResult;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The order lifecycle (order.md §3.9, §3.10) through the real {@code @Transactional} services and
 * a real PostgreSQL. Saving an already-managed order used to trip Hibernate's orphan-removal check
 * on its children; only a test that enters through the service — where the entity is managed —
 * can see it (see {@code TransactionalConstraintTranslationIntegrationTest}). Each step checks the
 * status, that the optimistic-lock version moved, that exactly one history row was added, and that
 * the delivery row and the lines are untouched: a status transition never changes them.
 */
@Testcontainers
@SpringBootTest
@WithMockUser(roles = "ADMIN")
class OrderLifecycleIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private PrepareOrderUseCase prepareOrderUseCase;
  @Autowired private ShipOrderUseCase shipOrderUseCase;
  @Autowired private CompleteOrderUseCase completeOrderUseCase;
  @Autowired private RejectOrderUseCase rejectOrderUseCase;
  @Autowired private CancelOrderUseCase cancelOrderUseCase;
  @Autowired private PiiCryptoPort piiCryptoPort;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private PaymentGatewayPort paymentGatewayPort;

  private UUID adminId;
  private UUID productId;

  @BeforeEach
  void seedAnAdminAProductAndASuccessfulGateway() {
    when(paymentGatewayPort.refund(any()))
        .thenAnswer(call -> new GatewayRefundResult(true, "ref-" + UUID.randomUUID(), null));
    adminId = UUID.randomUUID();
    productId = UUID.randomUUID();
    byte[] unique = adminId.toString().getBytes();
    jdbcTemplate.update(
        "INSERT INTO admin_users (id, email_encrypted, email_hash, password_hash, role)"
            + " VALUES (?, ?, ?, 'x', 'ADMIN')",
        adminId,
        unique,
        unique);
    jdbcTemplate.update(
        "INSERT INTO products (id, name, slug, price, status) VALUES (?, ?, ?, 20.00, 'ACTIVE')",
        productId,
        "Lifecycle " + productId,
        "lifecycle-" + productId);
  }

  @Test
  void deliveryOrderWalksPrepareShipComplete() {
    UUID orderId = seedOrder("DELIVERY", true);

    assertStep(orderId, "PREPARING", () -> prepareOrderUseCase.execute(new PrepareOrderCommand(orderId, adminId)));
    assertStep(orderId, "OUT_FOR_DELIVERY", () -> shipOrderUseCase.execute(new ShipOrderCommand(orderId, adminId)));
    assertStep(orderId, "DELIVERED", () -> completeOrderUseCase.execute(new CompleteOrderCommand(orderId, adminId)));
  }

  @Test
  void pickupOrderWalksPrepareComplete() {
    UUID orderId = seedOrder("PICKUP", true);

    assertStep(orderId, "PREPARING", () -> prepareOrderUseCase.execute(new PrepareOrderCommand(orderId, adminId)));
    assertStep(orderId, "DELIVERED", () -> completeOrderUseCase.execute(new CompleteOrderCommand(orderId, adminId)));
  }

  @Test
  void orderWithoutDeliveryWalksPrepareComplete() {
    UUID orderId = seedOrder("NONE", false);

    assertStep(orderId, "PREPARING", () -> prepareOrderUseCase.execute(new PrepareOrderCommand(orderId, adminId)));
    assertStep(orderId, "DELIVERED", () -> completeOrderUseCase.execute(new CompleteOrderCommand(orderId, adminId)));
  }

  @Test
  void rejectingAPaidDeliveryOrderRefundsItAndKeepsTheDelivery() {
    UUID orderId = seedOrder("DELIVERY", true);
    seedCapturedPayment(orderId);

    assertStep(orderId, "REJECTED", () -> rejectOrderUseCase.execute(new RejectOrderCommand(orderId, adminId, "Out of stock")));

    assertThat(refundedAmount(orderId)).isPositive();
  }

  @Test
  void cancellingAPaidDeliveryOrderRefundsItAndKeepsTheDelivery() {
    UUID orderId = seedOrder("DELIVERY", true);
    seedCapturedPayment(orderId);

    assertStep(
        orderId,
        "CANCELLED",
        () -> cancelOrderUseCase.execute(new CancelOrderCommand(orderId, null, adminId, "Customer called")));

    assertThat(refundedAmount(orderId)).isPositive();
  }

  private void assertStep(UUID orderId, String expectedStatus, Runnable transition) {
    Snapshot before = snapshot(orderId);

    transition.run();

    Snapshot after = snapshot(orderId);
    assertThat(after.status()).isEqualTo(expectedStatus);
    assertThat(after.version()).isGreaterThan(before.version());
    assertThat(after.historyRows()).isEqualTo(before.historyRows() + 1);
    assertThat(after.deliveryFingerprint()).isEqualTo(before.deliveryFingerprint());
    assertThat(after.items()).isEqualTo(before.items());
    // xmin changes on any UPDATE or INSERT of a row: a delete+reinsert of the same ids, or a
    // rewrite with identical values, would pass the content checks above but not these.
    assertThat(after.deliveryXmin()).isEqualTo(before.deliveryXmin());
    assertThat(after.itemsXmin()).isEqualTo(before.itemsXmin());
  }

  private Snapshot snapshot(UUID orderId) {
    List<String> fingerprints =
        jdbcTemplate.queryForList(
            "SELECT md5(concat_ws('|', encode(recipient_name_encrypted, 'hex'),"
                + " encode(recipient_phone_encrypted, 'hex'), encode(street_encrypted, 'hex'),"
                + " encode(number_encrypted, 'hex'), encode(detail_encrypted, 'hex'), postal_code,"
                + " encode(latitude_encrypted, 'hex'), encode(longitude_encrypted, 'hex'),"
                + " distance_km::text, delivery_date::text, slot_from::text, slot_to::text,"
                + " encode(card_message_encrypted, 'hex'))) FROM order_deliveries WHERE order_id = ?",
            String.class,
            orderId);
    return new Snapshot(
        jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId),
        jdbcTemplate.queryForObject("SELECT version FROM orders WHERE id = ?", Long.class, orderId),
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_status_history WHERE order_id = ?", Integer.class, orderId),
        fingerprints.isEmpty() ? null : fingerprints.get(0),
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_items WHERE order_id = ?", Integer.class, orderId),
        xmins("SELECT xmin::text FROM order_deliveries WHERE order_id = ?", orderId),
        xmins("SELECT xmin::text FROM order_items WHERE order_id = ? ORDER BY id", orderId));
  }

  private List<String> xmins(String sql, UUID orderId) {
    return jdbcTemplate.queryForList(sql, String.class, orderId);
  }

  private UUID seedOrder(String fulfillment, boolean withDelivery) {
    UUID orderId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO orders (id, order_number, channel, fulfillment, status, subtotal)"
            + " VALUES (?, ?, 'WEB', ?, 'PENDING', 40.00)",
        orderId,
        "WEB-20260920-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)),
        fulfillment);
    for (String productName : List.of("Rosa", "Tulipán")) {
      jdbcTemplate.update(
          "INSERT INTO order_items (id, order_id, product_id, product_name, unit_price, quantity)"
              + " VALUES (?, ?, ?, ?, 20.00, 1)",
          UUID.randomUUID(),
          orderId,
          productId,
          productName);
    }
    if (withDelivery) {
      jdbcTemplate.update(
          "INSERT INTO order_deliveries (order_id, recipient_name_encrypted,"
              + " recipient_phone_encrypted, street_encrypted, number_encrypted, detail_encrypted,"
              + " postal_code, latitude_encrypted, longitude_encrypted, distance_km, delivery_date,"
              + " slot_from, slot_to, card_message_encrypted)"
              + " VALUES (?, ?, ?, ?, ?, ?, '28013', ?, ?, 3.50, current_date + 1, '10:00', '12:00', ?)",
          orderId,
          piiCryptoPort.encrypt("Ana"),
          piiCryptoPort.encrypt("600000000"),
          piiCryptoPort.encrypt("Calle Mayor"),
          piiCryptoPort.encrypt("1"),
          piiCryptoPort.encrypt("2A"),
          piiCryptoPort.encrypt("40.4"),
          piiCryptoPort.encrypt("-3.7"),
          piiCryptoPort.encrypt("Felicidades"));
    }
    return orderId;
  }

  private void seedCapturedPayment(UUID orderId) {
    jdbcTemplate.update(
        "INSERT INTO payments (id, order_id, method, provider, provider_payment_intent_id, amount,"
            + " status, captured_at) VALUES (?, ?, 'CARD_ONLINE', 'REDSYS', 'auth-1', 40.00,"
            + " 'CAPTURED', now())",
        UUID.randomUUID(),
        orderId);
  }

  private java.math.BigDecimal refundedAmount(UUID orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT refunded_amount FROM payments WHERE order_id = ?", java.math.BigDecimal.class, orderId);
  }

  private record Snapshot(
      String status,
      long version,
      int historyRows,
      String deliveryFingerprint,
      int items,
      List<String> deliveryXmin,
      List<String> itemsXmin) {}
}
