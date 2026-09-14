package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.command.RejectOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.mapper.OrderDtoMapper;
import com.floristeriarosy.application.order.port.in.RejectOrderUseCase;
import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.port.out.OrderWritePort;
import com.floristeriarosy.domain.exception.order.OrderInvalidTransitionException;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderStatus;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link RejectOrderUseCase}: {@code PENDING} → {@code REJECTED} (order.md §3.9). */
@Service
@Transactional
public class RejectOrderService implements RejectOrderUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(RejectOrderService.class);

  private final OrderReadPort orderReadPort;
  private final OrderWritePort orderWritePort;
  private final OrderReversalComponent reversalComponent;

  /**
   * @param orderReadPort loads orders by id
   * @param orderWritePort persists orders and records status history
   * @param reversalComponent reverses stock, discounts, payment and notifications
   */
  public RejectOrderService(
      OrderReadPort orderReadPort,
      OrderWritePort orderWritePort,
      OrderReversalComponent reversalComponent) {
    this.orderReadPort = orderReadPort;
    this.orderWritePort = orderWritePort;
    this.reversalComponent = reversalComponent;
  }

  /**
   * @param command the order to reject, the admin, and the reason
   * @return the updated order
   * @throws OrderNotFoundException the order does not exist
   * @throws OrderInvalidTransitionException the order is not {@code PENDING}
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public OrderDto execute(RejectOrderCommand command) {
    LOGGER.debug("rejectOrder orderId={} adminUserId={}", command.orderId(), command.adminUserId());
    Order order = loadOrder(command.orderId());
    if (order.status() != OrderStatus.PENDING) {
      throw new OrderInvalidTransitionException("Order " + command.orderId() + " is not PENDING");
    }
    order.reject();
    Order saved = orderWritePort.save(order);
    orderWritePort.recordStatusChange(saved.id(), OrderStatus.REJECTED, command.adminUserId(), command.reason());
    reversalComponent.reverse(saved, "ORDER_REJECTED");
    OrderDto result = OrderDtoMapper.toDto(saved);
    LOGGER.debug("rejectOrder orderId={} -> rejected", command.orderId());
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
