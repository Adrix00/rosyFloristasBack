package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;
import com.floristeriarosy.application.notification.port.in.RegisterNotificationUseCase;
import com.floristeriarosy.application.order.command.CompleteOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.mapper.OrderDtoMapper;
import com.floristeriarosy.application.order.port.in.CompleteOrderUseCase;
import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.port.out.OrderWritePort;
import com.floristeriarosy.domain.exception.order.OrderInvalidTransitionException;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.OrderStatus;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link CompleteOrderUseCase}: marks an order {@code DELIVERED} (order.md §3.9). */
@Service
@Transactional
public class CompleteOrderService implements CompleteOrderUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(CompleteOrderService.class);

  private final OrderReadPort orderReadPort;
  private final OrderWritePort orderWritePort;
  private final RegisterNotificationUseCase registerNotificationUseCase;

  /**
   * @param orderReadPort loads orders by id
   * @param orderWritePort persists orders and records status history
   * @param registerNotificationUseCase registers the {@code ORDER_DELIVERED} notification
   */
  public CompleteOrderService(
      OrderReadPort orderReadPort,
      OrderWritePort orderWritePort,
      RegisterNotificationUseCase registerNotificationUseCase) {
    this.orderReadPort = orderReadPort;
    this.orderWritePort = orderWritePort;
    this.registerNotificationUseCase = registerNotificationUseCase;
  }

  /**
   * @param command the order to complete and the admin who is completing it
   * @return the updated order
   * @throws OrderNotFoundException the order does not exist
   * @throws OrderInvalidTransitionException the order is not in the expected status for its
   *     fulfillment mode
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public OrderDto execute(CompleteOrderCommand command) {
    LOGGER.debug(
        "completeOrder orderId={} adminUserId={}", command.orderId(), command.adminUserId());
    Order order = loadOrder(command.orderId());
    boolean expected =
        order.fulfillment() == OrderFulfillment.DELIVERY
            ? order.status() == OrderStatus.OUT_FOR_DELIVERY
            : order.status() == OrderStatus.PREPARING;
    if (!expected) {
      throw new OrderInvalidTransitionException(
          "Order " + command.orderId() + " is not in the expected status for completion");
    }
    order.complete();
    Order saved = orderWritePort.save(order);
    orderWritePort.recordStatusChange(saved.id(), OrderStatus.DELIVERED, command.adminUserId(), null);
    registerNotificationUseCase.execute(
        new RegisterNotificationCommand("ORDER_DELIVERED", saved.id().value(), saved.customerId()));
    OrderDto result = OrderDtoMapper.toDto(saved);
    LOGGER.debug("completeOrder orderId={} -> delivered", command.orderId());
    return result;
  }

  /**
   * @param orderId the order id to load
   * @return the loaded order
   * @throws OrderNotFoundException the order does not exist
   */
  private Order loadOrder(UUID orderId) {
    return orderReadPort
        .findById(OrderId.of(orderId))
        .orElseThrow(() -> new OrderNotFoundException("Order " + orderId + " not found"));
  }
}
