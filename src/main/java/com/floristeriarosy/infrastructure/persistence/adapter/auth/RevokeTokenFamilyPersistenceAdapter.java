package com.floristeriarosy.infrastructure.persistence.adapter.auth;

import com.floristeriarosy.application.auth.port.out.RevokeTokenFamilyPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link RevokeTokenFamilyPort} with plain JDBC (ADR-002): a single, unconditional bulk
 * update, no domain object to load or map.
 */
@Repository
public class RevokeTokenFamilyPersistenceAdapter implements RevokeTokenFamilyPort {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RevokeTokenFamilyPersistenceAdapter.class);

  private static final String REVOKE_ALL_FOR_SUBJECT =
      "UPDATE refresh_tokens SET revoked_at = now() "
          + "WHERE (customer_id = ? OR admin_user_id = ?) AND revoked_at IS NULL";

  private static final String REVOKE_FAMILY =
      "UPDATE refresh_tokens SET revoked_at = now() "
          + "WHERE family_id = ? AND revoked_at IS NULL";

  private final JdbcTemplate jdbcTemplate;

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public RevokeTokenFamilyPersistenceAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param subjectId the customer or admin user whose refresh token families are revoked
   */
  @Override
  public void revokeAllForSubject(UUID subjectId) {
    LOGGER.debug("revokeAllForSubject subjectId={}", subjectId);
    int updated = jdbcTemplate.update(REVOKE_ALL_FOR_SUBJECT, subjectId, subjectId);
    LOGGER.debug("revokeAllForSubject subjectId={} -> {} rows revoked", subjectId, updated);
  }

  /**
   * @param familyId the rotation family to revoke
   */
  @Override
  public void revokeFamily(UUID familyId) {
    LOGGER.debug("revokeFamily familyId={}", familyId);
    int updated = jdbcTemplate.update(REVOKE_FAMILY, familyId);
    LOGGER.debug("revokeFamily familyId={} -> {} rows revoked", familyId, updated);
  }
}
