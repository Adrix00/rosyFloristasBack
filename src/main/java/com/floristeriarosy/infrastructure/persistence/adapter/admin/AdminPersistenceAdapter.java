package com.floristeriarosy.infrastructure.persistence.adapter.admin;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.exception.admin.AdminEmailAlreadyExistsException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import com.floristeriarosy.infrastructure.persistence.entity.admin.AdminUserEntity;
import com.floristeriarosy.infrastructure.persistence.jpa.admin.repository.AdminUserJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.admin.AdminPersistenceMapper;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/**
 * Implements the admin output ports (ADR-003) with plain JPA (ADR-002, admin.md section 8): no
 * pagination, no JDBC needed.
 */
@Repository
public class AdminPersistenceAdapter implements AdminReadPort, AdminWritePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminPersistenceAdapter.class);

  private final AdminUserJpaRepository jpaRepository;
  private final AdminPersistenceMapper mapper;

  /**
   * @param jpaRepository writes and simple lookups
   * @param mapper converts between the domain {@link Admin} and the JPA {@link AdminUserEntity}
   */
  public AdminPersistenceAdapter(AdminUserJpaRepository jpaRepository, AdminPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.mapper = mapper;
  }

  /**
   * @param id the admin to load
   * @return the admin, if it exists
   */
  @Override
  public Optional<Admin> findById(AdminId id) {
    LOGGER.debug("findById id={}", id);
    Optional<Admin> result = jpaRepository.findById(id.value()).map(mapper::toDomain);
    LOGGER.debug("findById id={} -> found={}", id, result.isPresent());
    return result;
  }

  /**
   * @param emailHash the HMAC of a normalized email
   * @return the admin using that email, if any
   */
  @Override
  public Optional<Admin> findByEmailHash(byte[] emailHash) {
    LOGGER.debug("findByEmailHash");
    Optional<Admin> result = jpaRepository.findByEmailHash(emailHash).map(mapper::toDomain);
    LOGGER.debug("findByEmailHash -> found={}", result.isPresent());
    return result;
  }

  /**
   * @param active {@code null} for no filter, otherwise only admins with this status
   * @param role {@code null} for no filter, otherwise only admins with this role
   * @return the matching admins
   */
  @Override
  public List<Admin> findAll(Boolean active, AdminRole role) {
    LOGGER.debug("findAll active={} role={}", active, role);
    List<Admin> result =
        jpaRepository.findByFilters(active, role).stream().map(mapper::toDomain).toList();
    LOGGER.debug("findAll -> count={}", result.size());
    return result;
  }

  /**
   * @return how many {@code OWNER} rows are currently {@code active}
   */
  @Override
  public long countActiveOwners() {
    LOGGER.debug("countActiveOwners");
    long result = jpaRepository.countByRoleAndActiveTrue(AdminRole.OWNER);
    LOGGER.debug("countActiveOwners -> {}", result);
    return result;
  }

  /**
   * @return how many {@code OWNER} rows are currently {@code active}, locked until the caller's
   *     transaction commits (rule 3.7's TOCTOU guard — see {@link AdminReadPort#countActiveOwnersForUpdate()})
   */
  @Override
  public long countActiveOwnersForUpdate() {
    LOGGER.debug("countActiveOwnersForUpdate");
    long result = jpaRepository.findActiveByRoleForUpdate(AdminRole.OWNER).size();
    LOGGER.debug("countActiveOwnersForUpdate -> {}", result);
    return result;
  }

  /**
   * @param admin the admin to insert or update
   * @return the saved admin, with timestamps populated by the database
   * @throws AdminEmailAlreadyExistsException the email unique constraint was violated
   * @throws ResourceModifiedException the admin was changed concurrently (ADR-009)
   */
  @Override
  public Admin save(Admin admin) {
    LOGGER.debug("save id={}", admin.id());
    AdminUserEntity entity = mapper.toEntity(admin);
    try {
      Admin result = mapper.toDomain(jpaRepository.save(entity));
      LOGGER.debug("save id={} -> saved", result.id());
      return result;
    } catch (ObjectOptimisticLockingFailureException conflict) {
      throw new ResourceModifiedException("Admin " + admin.id() + " was modified concurrently");
    } catch (DataIntegrityViolationException violation) {
      throw translate(violation);
    }
  }

  /**
   * Translates a database constraint violation into the business exception it represents. The
   * constraint's name never reaches the client (00-security-validation-integrity.md).
   *
   * @param violation the low-level constraint violation caught around the save
   * @return the business exception to throw instead, or {@code violation} itself if the
   *     constraint is not one this module owns
   */
  private RuntimeException translate(DataIntegrityViolationException violation) {
    String message = String.valueOf(violation.getMostSpecificCause().getMessage());
    LOGGER.debug("save -> constraint violation: {}", message);
    if (message.contains("uq_admin_users_email_hash")) {
      return new AdminEmailAlreadyExistsException("An admin with this email already exists");
    }
    return violation;
  }
}
