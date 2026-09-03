package com.floristeriarosy.domain.model.auth;

/**
 * Who a token or a refresh session belongs to (auth.md, section 2). {@code CUSTOMER} exists because
 * {@code refresh_tokens.customer_id} requires it, even though customer login is deferred to {@code
 * feature/customer}.
 */
public enum SubjectType {
  CUSTOMER,
  ADMIN
}
