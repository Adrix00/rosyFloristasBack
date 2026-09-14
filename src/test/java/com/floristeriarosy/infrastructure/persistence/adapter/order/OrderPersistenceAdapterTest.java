package com.floristeriarosy.infrastructure.persistence.adapter.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.product.port.out.ProductWritePort;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.OrderItem;
import com.floristeriarosy.domain.model.order.OrderStatus;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderItemId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.product.Product;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.domain.model.product.valueobject.ProductSlug;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class OrderPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private OrderPersistenceAdapter adapter;
  @Autowired private ProductWritePort productWritePort;
  @Autowired private com.floristeriarosy.infrastructure.persistence.jpa.order.repository.OrderStatusHistoryJpaRepository historyRepository;

  private static final byte[] ENCRYPTED = "encrypted".getBytes(StandardCharsets.UTF_8);

  @Test
  void savingThenFindingByOrderNumberRoundTripsItemsAndTotal() {
    ProductId productId = ProductId.newId();
    productWritePort.save(
        Product.create(
            productId,
            "Rosa",
            ProductSlug.of("rosa-" + UUID.randomUUID()),
            null,
            new BigDecimal("10.00"),
            false,
            Map.of()));

    OrderItem item =
        OrderItem.snapshot(
            OrderItemId.newId(),
            productId,
            "Rosa",
            Map.of(),
            new BigDecimal("10.00"),
            null,
            null,
            2);
    OrderNumber orderNumber = OrderNumber.of("WEB-20260914-000001");
    Order order =
        Order.place(
            OrderId.newId(),
            orderNumber,
            OrderChannel.WEB,
            OrderFulfillment.NONE,
            null,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            List.of(item),
            null,
            new BigDecimal("20.00"),
            BigDecimal.ZERO);

    adapter.save(order);
    Order found = adapter.findByOrderNumber(orderNumber).orElseThrow();

    assertThat(found.items()).hasSize(1);
    assertThat(found.total()).isEqualByComparingTo("20.00");
  }

  @Test
  void recordStatusChangeInsertsHistoryRowWithDatabaseDefaultTimestamp() {
    ProductId productId = ProductId.newId();
    productWritePort.save(
        Product.create(
            productId,
            "Rosa",
            ProductSlug.of("rosa-" + UUID.randomUUID()),
            null,
            new BigDecimal("10.00"),
            false,
            Map.of()));

    OrderId orderId = OrderId.newId();
    Order order =
        Order.place(
            orderId,
            OrderNumber.of("WEB-20260914-000002"),
            OrderChannel.WEB,
            OrderFulfillment.NONE,
            null,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            List.of(
                OrderItem.snapshot(
                    OrderItemId.newId(),
                    productId,
                    "Rosa",
                    Map.of(),
                    new BigDecimal("10.00"),
                    null,
                    null,
                    1)),
            null,
            new BigDecimal("10.00"),
            BigDecimal.ZERO);
    adapter.save(order);

    adapter.recordStatusChange(orderId, OrderStatus.PREPARING, null, null);

    var history = historyRepository.findAll();
    assertThat(history).hasSize(1);
    assertThat(history.get(0).getOrderId()).isEqualTo(orderId.value());
    assertThat(history.get(0).getStatus()).isEqualTo(OrderStatus.PREPARING);
    assertThat(history.get(0).getChangedAt()).isNotNull();
  }
}
