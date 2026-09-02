package com.floristeriarosy.infrastructure.persistence.jpa.admin.repository;

import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.infrastructure.persistence.entity.admin.AdminUserEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data JPA repository for {@link AdminUserEntity}: writes and simple lookups (ADR-002). */
public interface AdminUserJpaRepository extends JpaRepository<AdminUserEntity, UUID> {

  /**
   * @param emailHash the HMAC of a normalized email
   * @return the entity using that email, if any
   */
  Optional<AdminUserEntity> findByEmailHash(byte[] emailHash);

  /**
   * @param role {@code OWNER} or {@code ADMIN}
   * @return how many rows of that role are currently {@code active}
   */
  long countByRoleAndActiveTrue(AdminRole role);

  /**
   * Locks every currently-active row of {@code role} for the rest of the caller's transaction
   * (ADR-009's {@code @Version} only guards a single row; the "at least one active OWNER"
   * invariant, admin.md rule 3.7, spans several rows, so a plain count is racy between two
   * concurrent requests each demoting/deactivating a *different* OWNER). A second such request
   * blocks here until the first commits, then re-counts against the post-commit state.
   *
   * @param role {@code OWNER} or {@code ADMIN}
   * @return the locked, currently-active rows of that role
   */
  @Transactional
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM AdminUserEntity a WHERE a.role = :role AND a.active = true")
  List<AdminUserEntity> findActiveByRoleForUpdate(@Param("role") AdminRole role);

  /**
   * Filters with the {@code :param IS NULL OR ...} idiom so a {@code null} filter matches
   * everything, without building a second query for every filter combination — the table has no
   * pagination to worry about (admin.md, section 4).
   *
   * @param active {@code null} for no filter, otherwise only admins with this status
   * @param role {@code null} for no filter, otherwise only admins with this role
   * @return the matching entities
   */
  @Query(
      "SELECT a FROM AdminUserEntity a "
          + "WHERE (:active IS NULL OR a.active = :active) "
          + "AND (:role IS NULL OR a.role = :role)")
  List<AdminUserEntity> findByFilters(
      @Param("active") Boolean active, @Param("role") AdminRole role);
}
