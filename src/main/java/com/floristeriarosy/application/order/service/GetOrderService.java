package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.port.in.GetOrderUseCase;
import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.query.GetOrderQuery;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderItem;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Objects;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetOrderUseCase}: loads an order and enforces ownership before returning the
 * public view (order.md §4, §6).
 */
@Service
public class GetOrderService implements GetOrderUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetOrderService.class);

  private final OrderReadPort orderReadPort;
  private final PiiCryptoPort piiCryptoPort;

  /**
   * @param orderReadPort loads orders by business identifier
   * @param piiCryptoPort hashes a guest's email for ownership comparison
   */
  public GetOrderService(OrderReadPort orderReadPort, PiiCryptoPort piiCryptoPort) {
    this.orderReadPort = orderReadPort;
    this.piiCryptoPort = piiCryptoPort;
  }

  /**
   * @param query the order to look up and the caller's identity
   * @return the public order view
   * @throws OrderNotFoundException no such order, or the caller does not own it
   */
  @Override
  public OrderDto execute(GetOrderQuery query) {
    LOGGER.debug("getOrder orderNumber={}", Encode.forJava(query.orderNumber()));
    Order order =
        orderReadPort
            .findByOrderNumber(OrderNumber.of(query.orderNumber()))
            .orElseThrow(() -> new OrderNotFoundException("Order " + query.orderNumber() + " not found"));
    ensureOwnership(order, query);
    OrderDto result = toDto(order);
    LOGGER.debug("getOrder orderNumber={} -> found", Encode.forJava(query.orderNumber()));
    return result;
  }

  /**
   * @param order the loaded order
   * @param query the caller's claimed identity
   * @throws OrderNotFoundException the caller does not own the order
   */
  private void ensureOwnership(Order order, GetOrderQuery query) {
    if (query.customerId() != null) {
      if (!Objects.equals(order.customerId(), query.customerId())) {
        throw new OrderNotFoundException("Order " + query.orderNumber() + " not found");
      }
      return;
    }
    if (query.guestEmail() == null || order.buyerEmailHash() == null) {
      throw new OrderNotFoundException("Order " + query.orderNumber() + " not found");
    }
    byte[] providedHash =
        piiCryptoPort.hmac(query.guestEmail().trim().toLowerCase(Locale.ROOT));
    if (!MessageDigest.isEqual(providedHash, order.buyerEmailHash())) {
      throw new OrderNotFoundException("Order " + query.orderNumber() + " not found");
    }
  }

  /**
   * @param order the owned order
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
