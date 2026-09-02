package com.floristeriarosy.application.admin.port.in;

import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.query.GetAdminsQuery;
import java.util.List;

/**
 * Lists administrators, optionally filtered by {@code active} and {@code role}. No pagination:
 * {@code admin_users} is a handful of rows (admin.md, section 4).
 */
public interface GetAdminsUseCase {

  /**
   * @param query the optional {@code active}/{@code role} filters
   * @return the matching admins
   */
  List<AdminDto> execute(GetAdminsQuery query);
}
