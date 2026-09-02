package com.floristeriarosy.application.admin.port.out;

import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.util.List;
import java.util.Optional;

/** Reads {@code admin_users} (admin.md, section 8). */
public interface AdminReadPort {

  /**
   * @param id the admin to load
   * @return the admin, if it exists
   */
  Optional<Admin> findById(AdminId id);

  /**
   * @param emailHash the HMAC of a normalized email
   * @return the admin using that email, if any
   */
  Optional<Admin> findByEmailHash(byte[] emailHash);

  /**
   * @param active {@code null} for no filter, otherwise only admins with this status
   * @param role {@code null} for no filter, otherwise only admins with this role
   * @return the matching admins
   */
  List<Admin> findAll(Boolean active, AdminRole role);

  /**
   * Counted directly against the database, inside the caller's transaction, so admin.md's rule 3.7
   * is checked against the real, current count — never against what the frontend believed.
   *
   * @return how many {@code OWNER} rows are currently {@code active}
   */
  long countActiveOwners();

  /**
   * Same count as {@link #countActiveOwners()}, but locks every active {@code OWNER} row for the
   * rest of the caller's transaction — a plain count is racy when two concurrent requests each
   * demote/deactivate a *different* OWNER, since neither row's own {@code @Version} conflicts with
   * the other. Use this variant, not {@link #countActiveOwners()}, immediately before rejecting or
   * allowing a change that could remove the last active {@code OWNER} (admin.md, rule 3.7).
   *
   * @return how many {@code OWNER} rows are currently {@code active}, locked until commit
   */
  long countActiveOwnersForUpdate();
}
