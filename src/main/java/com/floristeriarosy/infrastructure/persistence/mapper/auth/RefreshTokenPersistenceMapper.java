package com.floristeriarosy.infrastructure.persistence.mapper.auth;

import com.floristeriarosy.domain.model.auth.RefreshToken;
import com.floristeriarosy.domain.model.auth.SubjectType;
import com.floristeriarosy.domain.model.auth.valueobject.RefreshTokenId;
import com.floristeriarosy.infrastructure.persistence.entity.auth.RefreshTokenEntity;
import org.springframework.stereotype.Component;

/**
 * Domain &harr; JPA entity conversions (ADR-002: Persistence Mapper). Translates the domain's
 * unified {@code subjectId}/{@code subjectType} into the table's mutually exclusive {@code
 * customer_id}/{@code admin_user_id} columns, and back.
 */
@Component
public class RefreshTokenPersistenceMapper {

  /**
   * @param refreshToken the domain refresh token to persist
   * @return its JPA entity shape
   */
  public RefreshTokenEntity toEntity(RefreshToken refreshToken) {
    boolean isAdmin = refreshToken.subjectType() == SubjectType.ADMIN;
    return new RefreshTokenEntity(
        refreshToken.id().value(),
        refreshToken.tokenHash(),
        isAdmin ? null : refreshToken.subjectId(),
        isAdmin ? refreshToken.subjectId() : null,
        refreshToken.familyId(),
        refreshToken.expiresAt(),
        refreshToken.revokedAt(),
        refreshToken.createdAt());
  }

  /**
   * @param entity the persisted JPA entity
   * @return the rebuilt domain refresh token ({@link RefreshToken#reconstitute})
   */
  public RefreshToken toDomain(RefreshTokenEntity entity) {
    boolean isAdmin = entity.getAdminUserId() != null;
    return RefreshToken.reconstitute(
        RefreshTokenId.of(entity.getId()),
        entity.getTokenHash(),
        isAdmin ? entity.getAdminUserId() : entity.getCustomerId(),
        isAdmin ? SubjectType.ADMIN : SubjectType.CUSTOMER,
        entity.getFamilyId(),
        entity.getExpiresAt(),
        entity.getRevokedAt(),
        entity.getCreatedAt());
  }
}
