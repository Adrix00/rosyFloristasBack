package com.floristeriarosy.application.shared.port.out;

import com.floristeriarosy.application.shared.dto.AuditAction;
import java.util.List;
import java.util.UUID;

/**
 * Records an administrative action in {@code audit_log} (ADR-010). Carries only the names of the
 * fields that changed, never their values: {@code changes} is written as {@code NULL} by every
 * caller today, since no module using this port yet is on {@code chk_audit_log_changes_pii_free}'s
 * allow-list (admin.md, section 3.8 — {@code admin_users} itself is explicitly excluded, being PII
 * bearing).
 */
public interface AuditLogPort {

  /**
   * @param adminUserId the administrator who performed the action
   * @param action what kind of action it was
   * @param entityType the entity type acted upon, e.g. {@code "admin_user"}
   * @param entityId the entity acted upon
   * @param changedFields the names of the fields that changed, never their values
   */
  void record(
      UUID adminUserId,
      AuditAction action,
      String entityType,
      UUID entityId,
      List<String> changedFields);
}
