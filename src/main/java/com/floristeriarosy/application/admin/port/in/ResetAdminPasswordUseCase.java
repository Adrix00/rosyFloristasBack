package com.floristeriarosy.application.admin.port.in;

import com.floristeriarosy.application.admin.command.ResetAdminPasswordCommand;
import com.floristeriarosy.application.admin.dto.PasswordResetResult;

/** Fixes a new provisional password for an administrator (admin.md, rule 3.4). */
public interface ResetAdminPasswordUseCase {

  /**
   * @param command id of the admin whose password is reset
   * @return the newly generated provisional password, in plaintext, once
   */
  PasswordResetResult execute(ResetAdminPasswordCommand command);
}
