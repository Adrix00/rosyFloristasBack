package com.floristeriarosy.infrastructure.persistence.mapper.admin;

import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import com.floristeriarosy.infrastructure.persistence.entity.admin.AdminUserEntity;
import org.springframework.stereotype.Component;

/** Domain ↔ JPA entity conversions (ADR-002: Persistence Mapper). */
@Component
public class AdminPersistenceMapper {

  /**
   * @param admin the domain admin to persist
   * @return its JPA entity shape
   */
  public AdminUserEntity toEntity(Admin admin) {
    return new AdminUserEntity(
        admin.id().value(),
        admin.emailEncrypted(),
        admin.emailHash(),
        admin.passwordHash(),
        admin.role(),
        admin.totpSecretEncrypted(),
        admin.totpEnabled(),
        admin.totpLastUsedStep(),
        admin.passwordChangeRequired(),
        admin.active(),
        admin.version(),
        admin.createdAt(),
        admin.updatedAt());
  }

  /**
   * @param entity the persisted JPA entity
   * @return the rebuilt domain admin ({@link Admin#reconstitute})
   */
  public Admin toDomain(AdminUserEntity entity) {
    return Admin.reconstitute(
        AdminId.of(entity.getId()),
        entity.getEmailEncrypted(),
        entity.getEmailHash(),
        entity.getPasswordHash(),
        entity.getRole(),
        entity.getTotpSecretEncrypted(),
        entity.isTotpEnabled(),
        entity.getTotpLastUsedStep(),
        entity.isPasswordChangeRequired(),
        entity.isActive(),
        entity.getVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
