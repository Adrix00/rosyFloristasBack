package com.floristeriarosy.application.notification.port.in;

import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;

/**
 * Consumed directly by other modules (notification.md, section 1: "ningún otro módulo habla con
 * un proveedor de correo; registran una notificación y siguen con lo suyo") — no controller.
 */
public interface RegisterNotificationUseCase {

  /**
   * @param command what to notify about
   */
  void execute(RegisterNotificationCommand command);
}
