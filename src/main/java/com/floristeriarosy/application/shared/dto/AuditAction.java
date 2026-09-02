package com.floristeriarosy.application.shared.dto;

import com.floristeriarosy.application.shared.port.out.AuditLogPort;

/** Action recorded by {@link AuditLogPort} (ADR-010). Matches {@code chk_audit_log_action}. */
public enum AuditAction {
  CREATE,
  UPDATE,
  DELETE,
  LOGIN,
  LOGIN_FAILED
}
