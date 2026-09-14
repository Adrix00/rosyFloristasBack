package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.mapper.OrderDtoMapper;
import com.floristeriarosy.application.order.port.in.GetOrderUseCase;
import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.query.GetOrderQuery;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
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
    OrderDto result = OrderDtoMapper.toDto(order);
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
}
