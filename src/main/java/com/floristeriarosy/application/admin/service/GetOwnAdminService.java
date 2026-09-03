package com.floristeriarosy.application.admin.service;

import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.mapper.AdminDtoMapper;
import com.floristeriarosy.application.admin.port.in.GetOwnAdminUseCase;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.query.GetAdminQuery;
import com.floristeriarosy.domain.exception.admin.AdminNotFoundException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetOwnAdminUseCase}: {@code GET /admin/me}, any authenticated admin on
 * themselves — split from {@link GetAdminService} (feature/auth, phase 13; ADR-004 correction) so
 * {@code @PreAuthorize} can grant this without also opening {@code GET /admin/users/{id}} to a
 * plain {@code ADMIN} looking up an arbitrary other admin.
 */
@Service
public class GetOwnAdminService implements GetOwnAdminUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetOwnAdminService.class);

  private final AdminReadPort readPort;
  private final AdminDtoMapper mapper;

  /**
   * @param readPort loads the admin by id
   * @param mapper builds the response DTO
   */
  public GetOwnAdminService(AdminReadPort readPort, AdminDtoMapper mapper) {
    this.readPort = readPort;
    this.mapper = mapper;
  }

  /**
   * @param query the caller's own id, resolved from the JWT
   * @return the caller's own admin record
   * @throws AdminNotFoundException {@code query.id()} does not exist
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public AdminDto execute(GetAdminQuery query) {
    LOGGER.debug("getOwnAdmin id={}", query.id());

    AdminId id = AdminId.of(query.id());
    Admin admin =
        readPort
            .findById(id)
            .orElseThrow(() -> new AdminNotFoundException("Admin " + id + " not found"));
    AdminDto result = mapper.toDto(admin);

    LOGGER.debug("getOwnAdmin -> id={}", result.id());
    return result;
  }
}
