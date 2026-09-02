package com.floristeriarosy.application.admin.port.in;

import com.floristeriarosy.application.admin.command.ChangeOwnPasswordCommand;

/** An administrator replaces their own password (admin.md, section 4). */
public interface ChangeOwnPasswordUseCase {

  /**
   * @param command the admin's id, current password and new password
   */
  void execute(ChangeOwnPasswordCommand command);
}
