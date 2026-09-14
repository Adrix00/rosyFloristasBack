package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.order.Order;

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
}
