package com.floristeriarosy.application.admin.port.in;

import com.floristeriarosy.application.admin.command.ChangeAdminStatusCommand;
import com.floristeriarosy.application.admin.dto.AdminDto;

/** Activates or deactivates an administrator (admin.md, rule 3.6). */
public interface ChangeAdminStatusUseCase {

  /**
   * @param command id of the admin and the status to set
   * @return the admin with its new status
   */
  AdminDto execute(ChangeAdminStatusCommand command);
}
