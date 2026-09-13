package com.floristeriarosy.domain.model.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.order.valueobject.OrderItemId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Domain rules of {@link OrderItem} (order.md, rule 3.5). */
class OrderItemTest {

  @Test
  void lineTotalUsesDiscountPriceWhenPresent() {
    OrderItem item =
        OrderItem.snapshot(
            OrderItemId.newId(),
            ProductId.newId(),
            "Ramo de rosas",
            Map.of("color", "red"),
            new BigDecimal("25.00"),
            new BigDecimal("20.00"),
            DiscountId.newId(),
            3);

    assertThat(item.lineTotal()).isEqualByComparingTo("60.00");
  }

  @Test
  void lineTotalFallsBackToUnitPriceWhenNoDiscountApplied() {
    OrderItem item =
        OrderItem.snapshot(
            OrderItemId.newId(),
            ProductId.newId(),
            "Ramo de tulipanes",
            Map.of(),
            new BigDecimal("15.00"),
            null,
            null,
            2);

    assertThat(item.lineTotal()).isEqualByComparingTo("30.00");
    assertThat(item.discountId()).isNull();
  }

  @Test
  void snapshotDefensivelyCopiesProductAttributes() {
    Map<String, Object> attributes = new java.util.HashMap<>(Map.of("color", "red"));
    OrderItem item =
        OrderItem.snapshot(
            OrderItemId.newId(), ProductId.newId(), "Ramo", attributes, BigDecimal.TEN, null, null, 1);

    attributes.put("color", "blue");

    assertThat(item.productAttributes()).containsEntry("color", "red");
  }

  @Test
  void snapshotDefaultsNullAttributesToEmptyMap() {
    OrderItem item =
        OrderItem.snapshot(
            OrderItemId.newId(), ProductId.newId(), "Ramo", null, BigDecimal.TEN, null, null, 1);

    assertThat(item.productAttributes()).isEmpty();
  }
}
