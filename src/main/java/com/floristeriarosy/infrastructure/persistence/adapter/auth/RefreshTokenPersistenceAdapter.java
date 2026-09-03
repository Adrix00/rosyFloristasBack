package com.floristeriarosy.infrastructure.persistence.adapter.auth;

import com.floristeriarosy.application.auth.port.out.RefreshTokenReadPort;
import com.floristeriarosy.application.auth.port.out.RefreshTokenWritePort;
import com.floristeriarosy.domain.model.auth.RefreshToken;
import com.floristeriarosy.domain.model.auth.valueobject.RefreshTokenId;
import com.floristeriarosy.infrastructure.persistence.entity.auth.RefreshTokenEntity;
import com.floristeriarosy.infrastructure.persistence.jpa.auth.repository.RefreshTokenJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.auth.RefreshTokenPersistenceMapper;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Implements the refresh-token output ports (ADR-003) with plain JPA (ADR-002, auth.md section 8):
 * no pagination, no JDBC needed.
 */
@Repository
public class RefreshTokenPersistenceAdapter implements RefreshTokenReadPort, RefreshTokenWritePort {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RefreshTokenPersistenceAdapter.class);

  private final RefreshTokenJpaRepository jpaRepository;
  private final RefreshTokenPersistenceMapper mapper;

  /**
   * @param jpaRepository writes and simple lookups
   * @param mapper converts between the domain {@link RefreshToken} and its JPA entity
   */
  public RefreshTokenPersistenceAdapter(
      RefreshTokenJpaRepository jpaRepository, RefreshTokenPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.mapper = mapper;
  }

  /**
   * @param tokenHash SHA-256 of the plaintext token presented in the cookie
   * @return the matching row, if any
   */
  @Override
  public Optional<RefreshToken> findByHash(byte[] tokenHash) {
    LOGGER.debug("findByHash");
    Optional<RefreshToken> result = jpaRepository.findByTokenHash(tokenHash).map(mapper::toDomain);
    LOGGER.debug("findByHash -> found={}", result.isPresent());
    return result;
  }

  /**
   * @param refreshToken the row to insert
   * @return the saved row, with timestamps populated by the database
   */
  @Override
  public RefreshToken save(RefreshToken refreshToken) {
    LOGGER.debug("save id={} familyId={}", refreshToken.id(), refreshToken.familyId());
    RefreshTokenEntity entity = mapper.toEntity(refreshToken);
    RefreshToken result = mapper.toDomain(jpaRepository.save(entity));
    LOGGER.debug("save id={} -> saved", result.id());
    return result;
  }

  /**
   * @param id the row to revoke
   * @param revokedAt the revocation instant
   */
  @Override
  public void revoke(RefreshTokenId id, Instant revokedAt) {
    LOGGER.debug("revoke id={}", id);
    int updated = jpaRepository.revoke(id.value(), revokedAt);
    LOGGER.debug("revoke id={} -> {} row(s) updated", id, updated);
  }
}
