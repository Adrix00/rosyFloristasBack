package com.floristeriarosy.application.admin.port.out;

import com.floristeriarosy.domain.model.admin.Admin;

/** Writes {@code admin_users} (admin.md, section 8). */
public interface AdminWritePort {

  /**
   * @param admin the admin to insert or update
   * @return the saved admin, with timestamps populated by the database
   */
  Admin save(Admin admin);
}
