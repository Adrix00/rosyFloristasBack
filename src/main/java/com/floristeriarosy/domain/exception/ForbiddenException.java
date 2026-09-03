package com.floristeriarosy.domain.exception;

/**
 * The caller is authenticated but not allowed to perform this action. Mapped to HTTP 403. Distinct
 * from a role-based 403 raised by {@code @PreAuthorize} (a Spring Security {@code
 * AccessDeniedException}, never a domain exception): this is for a business rule, such as a session
 * limited to changing a provisional password (auth.md, rule 3.9).
 */
public abstract class ForbiddenException extends RuntimeException {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  protected ForbiddenException(String message) {
    super(message);
  }
}
