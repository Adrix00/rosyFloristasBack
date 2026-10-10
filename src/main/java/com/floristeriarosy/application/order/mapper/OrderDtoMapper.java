package com.floristeriarosy.application.order.mapper;

import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderItem;
import java.math.BigDecimal;

/**
 * Maps an {@link Order} aggregate to the public {@link OrderDto} view. Extracted from
 * {@link com.floristeriarosy.application.order.service.GetOrderService} once five lifecycle services
 * needed the same mapping (order.md §6).
 */
public final class OrderDtoMapper {

  private OrderDtoMapper() {}

  /**
   * @param order a paid order
   * @return its public view
   */
  public static OrderDto toDto(Order order) {
    return new OrderDto(
        order.id().value(),
        order.orderNumber().value(),
        order.channel().name(),
        order.fulfillment().name(),
        order.status().name(),
        order.items().stream().map(OrderDtoMapper::toDto).toList(),
        order.subtotal(),
        order.shippingCost(),
        order.total(),
        order.placedAt());
  }

  /**
   * @param item a sold line
   * @return its public view
   */
  private static OrderDto.Item toDto(OrderItem item) {
    BigDecimal effectivePrice = item.discountPrice() != null ? item.discountPrice() : item.unitPrice();
    BigDecimal lineTotal = effectivePrice.multiply(BigDecimal.valueOf(item.quantity()));
    return new OrderDto.Item(
        item.productId().value(),
        item.productName(),
        item.productAttributes(),
        item.unitPrice(),
        item.discountPrice(),
        item.quantity(),
        lineTotal);
  }
}
