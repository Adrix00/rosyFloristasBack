package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderStatus;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import java.util.UUID;

/**
 * Write access to {@code orders} (order.md, section 8): a single {@code save}, no {@code update}
 * — every transition still goes through the same aggregate root, matching {@code ProductWritePort}.
 */
public interface OrderWritePort {

  /**
   * @param order the order to insert (Fase 3a materializes it fully formed — order.md §1, an
   *     order is never partially created)
   * @return the saved order, with timestamps populated by the database
   */
  Order save(Order order);

  /**
   * Appends one row to {@code order_status_history} (order.md, section 3.9): "cada transición
   * añade una fila". Insert-only, no optimistic lock of its own (ADR-009 — same category as {@code
   * payments}/{@code order_items}, written once by exactly one call site per transition).
   *
   * @param orderId the order that changed
   * @param status the status it changed to
   * @param changedByAdminId the admin who triggered it, or {@code null} when the customer did
   * @param reason the admin's stated reason for {@code REJECTED}/{@code CANCELLED}, or {@code null}
   *     for every other transition and for a customer-initiated cancel
   */
  void recordStatusChange(OrderId orderId, OrderStatus status, UUID changedByAdminId, String reason);
}
