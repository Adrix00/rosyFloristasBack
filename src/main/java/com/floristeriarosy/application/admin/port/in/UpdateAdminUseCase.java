package com.floristeriarosy.application.admin.port.in;

import com.floristeriarosy.application.admin.command.UpdateAdminCommand;
import com.floristeriarosy.application.admin.dto.AdminDto;

/** Replaces an admin's email and role (admin.md, section 5). */
public interface UpdateAdminUseCase {

  /**
   * @param command id of the admin to update, plus its new email and role
   * @return the updated admin
   */
  AdminDto execute(UpdateAdminCommand command);
}
