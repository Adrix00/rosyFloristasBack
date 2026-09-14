package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.port.in.GetOrdersUseCase;
import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.query.GetOrdersQuery;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderItem;
import java.math.BigDecimal;
import java.util.List;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetOrdersUseCase}: lists the authenticated customer's orders, newest first
 * (order.md §4, §6).
 */
@Service
public class GetOrdersService implements GetOrdersUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetOrdersService.class);

  private final OrderReadPort orderReadPort;

  /**
   * @param orderReadPort loads orders by customer
   */
  public GetOrdersService(OrderReadPort orderReadPort) {
    this.orderReadPort = orderReadPort;
  }

  /**
   * @param query the authenticated customer whose orders to list
   * @return the customer's orders, newest first
   */
  @Override
  public List<OrderDto> execute(GetOrdersQuery query) {
    LOGGER.debug("getOrders customerId={}", query.customerId());
    List<OrderDto> result = orderReadPort.findByCustomerId(query.customerId()).stream()
        .map(this::toDto)
        .toList();
    LOGGER.debug("getOrders customerId={} -> count={}", query.customerId(), result.size());
    return result;
  }

  /**
   * @param order a paid order belonging to the caller
   * @return its public view
   */
  private OrderDto toDto(Order order) {
    return new OrderDto(
        order.id().value(),
        order.orderNumber().value(),
        order.channel().name(),
        order.fulfillment().name(),
        order.status().name(),
        order.items().stream().map(this::toDto).toList(),
        order.subtotal(),
        order.shippingCost(),
        order.total(),
        order.placedAt());
  }

  /**
   * @param item a sold line
   * @return its public view
   */
  private OrderDto.Item toDto(OrderItem item) {
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
