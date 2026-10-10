package com.floristeriarosy.application.notification.service;

import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;
import com.floristeriarosy.application.notification.port.in.RegisterNotificationUseCase;
import com.floristeriarosy.application.notification.port.out.NotificationWritePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link RegisterNotificationUseCase} — the transactional-outbox insert, nothing else
 * (notification.md, section 1: sending is a separate, future scheduled task).
 */
@Service
public class RegisterNotificationService implements RegisterNotificationUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(RegisterNotificationService.class);

  private final NotificationWritePort writePort;

  /**
   * @param writePort inserts the {@code notifications} row
   */
  public RegisterNotificationService(NotificationWritePort writePort) {
    this.writePort = writePort;
  }

  /**
   * @param command what to notify about
   */
  @Override
  public void execute(RegisterNotificationCommand command) {
    LOGGER.debug("registerNotification type={} orderId={}", command.type(), command.orderId());
    writePort.insert(command.type(), command.orderId(), command.customerId());
    LOGGER.debug("registerNotification type={} -> registered", command.type());
  }
}
