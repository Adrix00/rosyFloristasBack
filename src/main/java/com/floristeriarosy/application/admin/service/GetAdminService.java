package com.floristeriarosy.application.admin.service;

import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.mapper.AdminDtoMapper;
import com.floristeriarosy.application.admin.port.in.GetAdminUseCase;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.query.GetAdminQuery;
import com.floristeriarosy.domain.exception.admin.AdminNotFoundException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetAdminUseCase}: lookup by id, shared by {@code GET /admin/users/{id}} (
 * {@code OWNER}) and {@code GET /admin/me} (self).
 */
@Service
public class GetAdminService implements GetAdminUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetAdminService.class);

  private final AdminReadPort readPort;
  private final AdminDtoMapper mapper;

  /**
   * @param readPort loads the admin by id
   * @param mapper builds the response DTO
   */
  public GetAdminService(AdminReadPort readPort, AdminDtoMapper mapper) {
    this.readPort = readPort;
    this.mapper = mapper;
  }

  /**
   * @param query the admin to look up
   * @return the matching admin
   * @throws AdminNotFoundException {@code query.id()} does not exist
   */
  @Override
  public AdminDto execute(GetAdminQuery query) {
    LOGGER.debug("getAdmin id={}", query.id());

    AdminId id = AdminId.of(query.id());
    Admin admin =
        readPort.findById(id).orElseThrow(() -> new AdminNotFoundException("Admin " + id + " not found"));
    AdminDto result = mapper.toDto(admin);

    LOGGER.debug("getAdmin -> id={}", result.id());
    return result;
  }
}
