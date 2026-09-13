package com.floristeriarosy.infrastructure.persistence.adapter.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.payment.PaymentProvider;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Round-trips {@link Payment} through PostgreSQL, same pattern as the other adapter tests. */
@Testcontainers
@SpringBootTest
class PaymentPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private PaymentPersistenceAdapter adapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  /**
   * {@code payments.order_id} is a foreign key to {@code orders.id}; an order row must exist before
   * saving a payment.
   *
   * @param orderId the order to create
   * @param orderNumber a unique business identifier
   */
  private void insertOrder(OrderId orderId, String orderNumber) {
    jdbcTemplate.update(
        """
        INSERT INTO orders (id, order_number, channel, fulfillment, status, subtotal, shipping_cost)
        VALUES (?, ?, 'WEB', 'DELIVERY', 'PENDING', 0.00, 0.00)
        """,
        orderId.value(),
        orderNumber);
  }

  @Test
  void savingThenFindingByIdRoundTripsAllFields() {
    OrderId orderId = OrderId.newId();
    insertOrder(orderId, "WEB-PAY-000001");
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), orderId, PaymentMethod.CASH, PaymentProvider.MANUAL,
            new BigDecimal("15.00"));
    payment.capture(null, Instant.now());

    Payment saved = adapter.save(payment);
    Payment found = adapter.findById(saved.id()).orElseThrow();

    assertThat(found.status()).isEqualTo(com.floristeriarosy.domain.model.payment.PaymentStatus.CAPTURED);
    assertThat(found.amount()).isEqualByComparingTo("15.00");
  }

  @Test
  void findByOrderIdLocatesTheCapturedPayment() {
    OrderId orderId = OrderId.newId();
    insertOrder(orderId, "WEB-PAY-000002");
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), orderId, PaymentMethod.CASH, PaymentProvider.MANUAL, new BigDecimal("10.00"));
    payment.capture(null, Instant.now());
    adapter.save(payment);

    assertThat(adapter.findByOrderId(orderId)).isPresent();
  }
}
