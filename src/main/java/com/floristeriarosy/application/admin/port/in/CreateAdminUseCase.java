package com.floristeriarosy.application.admin.port.in;

import com.floristeriarosy.application.admin.command.CreateAdminCommand;
import com.floristeriarosy.application.admin.dto.AdminDto;

/** Creates a new administrator with a provisional password (admin.md, rule 3.2). */
public interface CreateAdminUseCase {

  /**
   * @param command email, provisional password and role of the admin to create
   * @return the created admin
   */
  AdminDto execute(CreateAdminCommand command);
}
