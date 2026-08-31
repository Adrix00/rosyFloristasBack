package com.floristeriarosy.domain.exception;

/**
 * Implemented by every domain exception that maps to a published API error code (ADR-012). The code
 * is a plain identifier; HTTP status mapping stays in infrastructure/web/advice.
 */
public interface HasErrorCode {

  /**
   * @return the published error code (ADR-012), e.g. {@code "CATEGORY_NOT_FOUND"}
   */
  String errorCode();
}
