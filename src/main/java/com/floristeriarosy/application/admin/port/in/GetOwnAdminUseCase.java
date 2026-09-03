package com.floristeriarosy.application.admin.port.in;

import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.query.GetAdminQuery;

/** {@code GET /admin/me} (any authenticated admin, on themselves) (admin.md, section 4). */
public interface GetOwnAdminUseCase {

  /**
   * @param query the caller's own id, resolved from the JWT
   * @return the caller's own admin record
   */
  AdminDto execute(GetAdminQuery query);
}
