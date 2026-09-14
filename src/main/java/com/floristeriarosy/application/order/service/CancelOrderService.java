package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.command.CancelOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.mapper.OrderDtoMapper;
import com.floristeriarosy.application.order.port.in.CancelOrderUseCase;
import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.application.order.port.out.OrderWritePort;
import com.floristeriarosy.domain.exception.order.OrderInvalidTransitionException;
import com.floristeriarosy.domain.exception.order.OrderNotCancelableException;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;
import com.floristeriarosy.domain.exception.order.OrderValidationFailedException;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderStatus;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link CancelOrderUseCase}: {@code PENDING} or {@code PREPARING} → {@code CANCELLED}
 * (order.md §3.9). Admin may cancel from {@code PENDING} or {@code PREPARING}; the owning customer
 * may cancel only from {@code PENDING}.
 */
@Service
@Transactional
public class CancelOrderService implements CancelOrderUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(CancelOrderService.class);

  private final OrderReadPort orderReadPort;
  private final OrderWritePort orderWritePort;
  private final OrderReversalComponent reversalComponent;

  /**
   * @param orderReadPort loads orders by id
   * @param orderWritePort persists orders and records status history
   * @param reversalComponent reverses stock, discounts, payment and notifications
   */
  public CancelOrderService(
      OrderReadPort orderReadPort,
      OrderWritePort orderWritePort,
      OrderReversalComponent reversalComponent) {
    this.orderReadPort = orderReadPort;
    this.orderWritePort = orderWritePort;
    this.reversalComponent = reversalComponent;
  }

  /**
   * @param command the order to cancel and who is cancelling it
   * @return the updated order
   * @throws OrderNotFoundException the order does not exist, or the customer does not own it
   * @throws OrderValidationFailedException an admin cancel is missing the required reason
   * @throws OrderInvalidTransitionException an admin tried to cancel from a non-allowed status
   * @throws OrderNotCancelableException a customer tried to cancel from a non-allowed status
   */
  @Override
  @PreAuthorize("isAuthenticated()")
  public OrderDto execute(CancelOrderCommand command) {
    LOGGER.debug(
        "cancelOrder orderId={} customerId={} adminUserId={}",
        command.orderId(),
        command.customerId(),
        command.adminUserId());
    Order order = loadOrder(command.orderId());
    boolean byAdmin = command.adminUserId() != null;
    if (byAdmin) {
      requireReason(command.reason());
      if (order.status() != OrderStatus.PENDING && order.status() != OrderStatus.PREPARING) {
        throw new OrderInvalidTransitionException(
            "Order " + command.orderId() + " is not PENDING or PREPARING");
      }
    } else {
      requireOwnership(order, command.customerId());
      if (order.status() != OrderStatus.PENDING) {
        throw new OrderNotCancelableException(
            "Order " + command.orderId() + " is not PENDING");
      }
    }
    order.cancel();
    Order saved = orderWritePort.save(order);
    orderWritePort.recordStatusChange(saved.id(), OrderStatus.CANCELLED, command.adminUserId(), command.reason());
    String notificationType = byAdmin ? "ORDER_CANCELLED" : "STAFF_ORDER_CANCELLED_BY_CUSTOMER";
    reversalComponent.reverse(saved, notificationType);
    OrderDto result = OrderDtoMapper.toDto(saved);
    LOGGER.debug("cancelOrder orderId={} -> cancelled", command.orderId());
    return result;
  }

  /**
   * @param reason the admin's stated reason
   * @throws OrderValidationFailedException {@code reason} is blank — role-conditional, so it is
   *     checked here rather than with a DTO-level {@code @NotBlank} that would also apply to the
   *     customer's optional {@code reason} (order.md, {@code CancelOrderRequest})
   */
  private void requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new OrderValidationFailedException("reason is required when an admin cancels an order");
    }
  }

  /**
   * @param order the order being cancelled
   * @param customerId the caller's id
   * @throws OrderNotFoundException the caller does not own the order — same not-found-not-forbidden
   *     convention {@code GetOrderService.ensureOwnership} already uses, so a probing customer
   *     cannot learn an order exists by getting a different error for "not yours" vs. "does not
   *     exist"
   */
  private void requireOwnership(Order order, UUID customerId) {
    if (!Objects.equals(order.customerId(), customerId)) {
      throw new OrderNotFoundException("Order " + order.id() + " not found");
    }
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
