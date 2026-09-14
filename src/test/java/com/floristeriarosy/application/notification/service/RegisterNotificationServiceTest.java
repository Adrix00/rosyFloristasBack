package com.floristeriarosy.application.notification.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;
import com.floristeriarosy.application.notification.port.out.NotificationWritePort;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegisterNotificationServiceTest {

  private final NotificationWritePort writePort = mock(NotificationWritePort.class);
  private final RegisterNotificationService service = new RegisterNotificationService(writePort);

  @Test
  void registersTheNotificationAsGiven() {
    UUID orderId = UUID.randomUUID();

    service.execute(new RegisterNotificationCommand("ORDER_CONFIRMED", orderId, null));

    verify(writePort).insert("ORDER_CONFIRMED", orderId, null);
  }
}
