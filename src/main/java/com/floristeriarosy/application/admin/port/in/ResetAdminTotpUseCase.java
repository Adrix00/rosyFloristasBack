package com.floristeriarosy.application.admin.port.in;

import com.floristeriarosy.application.admin.command.ResetAdminTotpCommand;

/** Resets an administrator's TOTP enrollment to its initial state (admin.md, rule 3.5). */
public interface ResetAdminTotpUseCase {

  /**
   * @param command id of the admin whose TOTP is reset
   */
  void execute(ResetAdminTotpCommand command);
}
