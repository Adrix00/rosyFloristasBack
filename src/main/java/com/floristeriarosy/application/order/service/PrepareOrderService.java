package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.command.PrepareOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.mapper.OrderDtoMapper;
import com.floristeriarosy.application.order.port.in.PrepareOrderUseCase;
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

/** Implements {@link PrepareOrderUseCase}: {@code PENDING} → {@code PREPARING} (order.md §3.9). */
@Service
@Transactional
public class PrepareOrderService implements PrepareOrderUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(PrepareOrderService.class);

  private final OrderReadPort orderReadPort;
  private final OrderWritePort orderWritePort;

  /**
   * @param orderReadPort loads orders by id
   * @param orderWritePort persists orders and records status history
   */
  public PrepareOrderService(OrderReadPort orderReadPort, OrderWritePort orderWritePort) {
    this.orderReadPort = orderReadPort;
    this.orderWritePort = orderWritePort;
  }

  /**
   * @param command the order to prepare and the admin who is preparing it
   * @return the updated order
   * @throws OrderNotFoundException the order does not exist
   * @throws OrderInvalidTransitionException the order is not {@code PENDING}
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public OrderDto execute(PrepareOrderCommand command) {
    LOGGER.debug("prepareOrder orderId={} adminUserId={}", command.orderId(), command.adminUserId());
    Order order = loadOrder(command.orderId());
    if (order.status() != OrderStatus.PENDING) {
      throw new OrderInvalidTransitionException("Order " + command.orderId() + " is not PENDING");
    }
    order.prepare();
    Order saved = orderWritePort.save(order);
    orderWritePort.recordStatusChange(saved.id(), OrderStatus.PREPARING, command.adminUserId(), null);
    OrderDto result = OrderDtoMapper.toDto(saved);
    LOGGER.debug("prepareOrder orderId={} -> preparing", command.orderId());
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
