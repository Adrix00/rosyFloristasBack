package com.floristeriarosy.application.admin.port.in;

import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.query.GetAdminQuery;

/** Looks up one administrator by id (admin.md, section 4). */
public interface GetAdminUseCase {

  /**
   * @param query the admin to look up
   * @return the matching admin
   */
  AdminDto execute(GetAdminQuery query);
}
