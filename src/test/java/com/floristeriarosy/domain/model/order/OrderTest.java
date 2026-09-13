package com.floristeriarosy.domain.model.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderItemId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Domain rules of {@link Order} (order.md, sections 1, 2, 3.9). */
class OrderTest {

  private static final byte[] ENCRYPTED = "encrypted".getBytes(StandardCharsets.UTF_8);

  private OrderItem oneRose() {
    return OrderItem.snapshot(
        OrderItemId.newId(), ProductId.newId(), "Rosa roja", Map.of(), new BigDecimal("10.00"), null,
        null, 2);
  }

  @Test
  void placeIsBornPendingWithTotalAsSubtotalPlusShipping() {
    Order order =
        Order.place(
            OrderId.newId(),
            OrderNumber.of("WEB-20260913-000001"),
            OrderChannel.WEB,
            OrderFulfillment.PICKUP,
            null,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            List.of(oneRose()),
            OrderDelivery.forPickup(LocalDate.of(2026, 9, 20), LocalTime.of(10, 0), LocalTime.of(11, 0)),
            new BigDecimal("20.00"),
            BigDecimal.ZERO);

    assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.total()).isEqualByComparingTo("20.00");
    assertThat(order.version()).isZero();
    assertThat(order.customerId()).isNull();
  }

  @Test
  void placeRejectsAnOrderWithNoItems() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                Order.place(
                    OrderId.newId(),
                    OrderNumber.of("WEB-20260913-000002"),
                    OrderChannel.WEB,
                    OrderFulfillment.NONE,
                    null,
                    ENCRYPTED,
                    ENCRYPTED,
                    ENCRYPTED,
                    ENCRYPTED,
                    ENCRYPTED,
                    ENCRYPTED,
                    List.of(),
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO));
  }

  @Test
  void totalAddsShippingCostToSubtotal() {
    Order order =
        Order.place(
            OrderId.newId(),
            OrderNumber.of("WEB-20260913-000003"),
            OrderChannel.WEB,
            OrderFulfillment.NONE,
            null,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            List.of(oneRose()),
            null,
            new BigDecimal("20.00"),
            new BigDecimal("5.00"));

    assertThat(order.total()).isEqualByComparingTo("25.00");
  }

  @Test
  void itemsAreDefensivelyCopiedAndUnmodifiable() {
    List<OrderItem> items = new java.util.ArrayList<>(List.of(oneRose()));
    Order order =
        Order.place(
            OrderId.newId(),
            OrderNumber.of("WEB-20260913-000004"),
            OrderChannel.WEB,
            OrderFulfillment.NONE,
            null,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            items,
            null,
            new BigDecimal("20.00"),
            BigDecimal.ZERO);

    items.clear();

    assertThat(order.items()).hasSize(1);
    assertThatThrownBy(() -> order.items().add(oneRose()))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
