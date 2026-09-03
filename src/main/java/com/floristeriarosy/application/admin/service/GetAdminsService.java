package com.floristeriarosy.application.admin.service;

import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.mapper.AdminDtoMapper;
import com.floristeriarosy.application.admin.port.in.GetAdminsUseCase;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.query.GetAdminsQuery;
import com.floristeriarosy.domain.model.admin.Admin;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetAdminsUseCase}: lists administrators, optionally filtered by {@code active}
 * and {@code role} (admin.md, section 4). {@code OWNER}-only, enforced via {@code @PreAuthorize}
 * (feature/auth, phase 13).
 */
@Service
public class GetAdminsService implements GetAdminsUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetAdminsService.class);

  private final AdminReadPort readPort;
  private final AdminDtoMapper mapper;

  /**
   * @param readPort lists admins, optionally filtered
   * @param mapper builds each response DTO
   */
  public GetAdminsService(AdminReadPort readPort, AdminDtoMapper mapper) {
    this.readPort = readPort;
    this.mapper = mapper;
  }

  /**
   * @param query the optional {@code active}/{@code role} filters
   * @return the matching admins
   */
  @Override
  @PreAuthorize("hasRole('OWNER')")
  public List<AdminDto> execute(GetAdminsQuery query) {
    LOGGER.debug("getAdmins active={} role={}", query.active(), query.role());

    List<Admin> admins = readPort.findAll(query.active(), query.role());
    List<AdminDto> result = admins.stream().map(mapper::toDto).toList();

    LOGGER.debug("getAdmins -> {} admins", result.size());
    return result;
  }
}
