package com.floristeriarosy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.command.CancelOrderCommand;
import com.floristeriarosy.application.order.command.RejectOrderCommand;
import com.floristeriarosy.application.order.port.in.CancelOrderUseCase;
import com.floristeriarosy.application.order.port.in.RejectOrderUseCase;
import com.floristeriarosy.application.payment.dto.GatewayRefundResult;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.domain.exception.payment.PaymentGatewayException;
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
 * Rejecting or cancelling a paid order when Redsys refuses the refund must leave the database
 * exactly as it was (order.md §3.10, ADR-012 {@code PAYMENT_GATEWAY_ERROR}). The service's own
 * writes (status, history, stock, discount, notifications) happen before the refund call, so only
 * a real transaction proves they are rolled back; a mocked unit test can show the exception
 * propagates but not that the rows are gone.
 */
@Testcontainers
@SpringBootTest
@WithMockUser(roles = "ADMIN")
class OrderReversalRollbackIntegrationTest {

  private static final int INITIAL_STOCK = 10;
  private static final int DISCOUNT_SOLD = 2;

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private RejectOrderUseCase rejectOrderUseCase;
  @Autowired private CancelOrderUseCase cancelOrderUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private PaymentGatewayPort paymentGatewayPort;

  private UUID adminId;
  private UUID orderId;
  private UUID productId;
  private UUID discountId;

  @BeforeEach
  void seedAPaidPendingOrderAndAFailingGateway() {
    when(paymentGatewayPort.refund(any()))
        .thenReturn(new GatewayRefundResult(false, null, "SIS9999"));
    adminId = UUID.randomUUID();
    orderId = UUID.randomUUID();
    productId = UUID.randomUUID();
    discountId = UUID.randomUUID();
    String suffix = orderId.toString();
    jdbcTemplate.update(
        "INSERT INTO admin_users (id, email_encrypted, email_hash, password_hash, role)"
            + " VALUES (?, ?, ?, 'x', 'ADMIN')",
        adminId,
        suffix.getBytes(),
        suffix.getBytes());
    jdbcTemplate.update(
        "INSERT INTO products (id, name, slug, price, stock, status)"
            + " VALUES (?, ?, ?, 20.00, ?, 'ACTIVE')",
        productId,
        "Rollback " + suffix,
        "rollback-" + suffix,
        INITIAL_STOCK);
    jdbcTemplate.update(
        "INSERT INTO product_discounts (id, product_id, original_price, sale_price, starts_at,"
            + " ends_at, quantity_limit, quantity_sold)"
            + " VALUES (?, ?, 20.00, 15.00, now() - interval '1 day', now() + interval '1 day',"
            + " 10, ?)",
        discountId,
        productId,
        DISCOUNT_SOLD);
    jdbcTemplate.update(
        "INSERT INTO orders (id, order_number, channel, fulfillment, status, subtotal)"
            + " VALUES (?, ?, 'WEB', 'NONE', 'PENDING', 30.00)",
        orderId,
        "WEB-20260920-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)));
    jdbcTemplate.update(
        "INSERT INTO order_items (id, order_id, product_id, product_name, unit_price,"
            + " discount_price, quantity, discount_id) VALUES (?, ?, ?, 'Rosa', 20.00, 15.00, 2, ?)",
        UUID.randomUUID(),
        orderId,
        productId,
        discountId);
    jdbcTemplate.update(
        "INSERT INTO payments (id, order_id, method, provider, provider_payment_intent_id, amount,"
            + " status, captured_at) VALUES (?, ?, 'CARD_ONLINE', 'REDSYS', 'auth-1', 30.00,"
            + " 'CAPTURED', now())",
        UUID.randomUUID(),
        orderId);
  }

  @Test
  void rejectRollsEverythingBackWhenTheGatewayRefusesTheRefund() {
    assertThatThrownBy(
            () -> rejectOrderUseCase.execute(new RejectOrderCommand(orderId, adminId, "Out of stock")))
        .isInstanceOf(PaymentGatewayException.class);

    assertNothingPersisted();
  }

  @Test
  void cancelRollsEverythingBackWhenTheGatewayRefusesTheRefund() {
    assertThatThrownBy(
            () ->
                cancelOrderUseCase.execute(
                    new CancelOrderCommand(orderId, null, adminId, "Customer called")))
        .isInstanceOf(PaymentGatewayException.class);

    assertNothingPersisted();
  }

  private void assertNothingPersisted() {
    assertThat(queryString("SELECT status FROM orders WHERE id = ?", orderId)).isEqualTo("PENDING");
    assertThat(queryInt("SELECT stock FROM products WHERE id = ?", productId)).isEqualTo(INITIAL_STOCK);
    assertThat(queryInt("SELECT COUNT(*) FROM stock_movements WHERE product_id = ?", productId))
        .isZero();
    assertThat(queryInt("SELECT quantity_sold FROM product_discounts WHERE id = ?", discountId))
        .isEqualTo(DISCOUNT_SOLD);
    assertThat(queryInt("SELECT COUNT(*) FROM order_status_history WHERE order_id = ?", orderId))
        .isZero();
    assertThat(queryInt("SELECT COUNT(*) FROM notifications WHERE order_id = ?", orderId)).isZero();
    assertThat(queryString("SELECT status FROM payments WHERE order_id = ?", orderId))
        .isEqualTo("CAPTURED");
    assertThat(queryInt("SELECT COUNT(*) FROM payments WHERE order_id = ? AND refunded_amount > 0", orderId))
        .isZero();
  }

  private String queryString(String sql, UUID id) {
    return jdbcTemplate.queryForObject(sql, String.class, id);
  }

  private Integer queryInt(String sql, UUID id) {
    return jdbcTemplate.queryForObject(sql, Integer.class, id);
  }
}
