package com.floristeriarosy.application.notification.port.out;

import java.util.UUID;

/** Write access to {@code notifications} (notification.md, section 2/3.4: transactional outbox). */
public interface NotificationWritePort {

  /**
   * @param type the notification type
   * @param orderId the referenced order, or {@code null}
   * @param customerId the referenced customer, or {@code null}
   */
  void insert(String type, UUID orderId, UUID customerId);
}
