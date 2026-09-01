package com.floristeriarosy.infrastructure.web.advice;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;
import com.floristeriarosy.domain.exception.UnprocessableException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions and Bean Validation failures to RFC 7807 (ADR-012). One advice for
 * the whole application: each module's exceptions extend one of the three kind base classes in
 * {@code domain.exception}, so a new module needs no new handler method here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * Maps any {@link NotFoundException} to 404.
   *
   * @param exception the domain exception that was thrown
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body
   */
  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail handleNotFound(NotFoundException exception, HttpServletRequest request) {
    LOGGER.debug("404 on {}: {}", Encode.forJava(request.getRequestURI()), exception.getMessage());
    return problemDetail(HttpStatus.NOT_FOUND, exception, request);
  }

  /**
   * Maps any {@link ConflictException} to 409.
   *
   * @param exception the domain exception that was thrown
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body
   */
  @ExceptionHandler(ConflictException.class)
  public ProblemDetail handleConflict(ConflictException exception, HttpServletRequest request) {
    LOGGER.debug("409 on {}: {}", Encode.forJava(request.getRequestURI()), exception.getMessage());
    return problemDetail(HttpStatus.CONFLICT, exception, request);
  }

  /**
   * Maps any {@link UnprocessableException} to 422.
   *
   * @param exception the domain exception that was thrown
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body
   */
  @ExceptionHandler(UnprocessableException.class)
  public ProblemDetail handleUnprocessable(
      UnprocessableException exception, HttpServletRequest request) {
    LOGGER.debug("422 on {}: {}", Encode.forJava(request.getRequestURI()), exception.getMessage());
    return problemDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception, request);
  }

  /**
   * Maps a Bean Validation failure on a {@code @RequestBody} to 422, with one {@code errors[]}
   * entry per rejected field.
   *
   * @param exception the validation failure raised by Spring
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body, with {@code code} derived from the invalid DTO's package
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    String code = validationCodeFor(exception);
    LOGGER.debug(
        "422 on {}: code={} rejectedFields={}",
        Encode.forJava(request.getRequestURI()),
        code,
        exception.getBindingResult().getFieldErrorCount());

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "Validation failed");
    problem.setTitle("Validation failed");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    problem.setProperty(
        "errors",
        exception.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> new FieldViolation(fieldError.getField(), fieldError.getCode()))
            .toList());
    return problem;
  }

  /**
   * Catches anything not already mapped above and maps it to a generic 500. The exception is logged
   * in full server-side; the client never sees a stack trace or an internal message
   * (00-security-validation-integrity.md, section 9).
   *
   * @param exception the unmapped exception
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body, with {@code code=INTERNAL_ERROR}
   */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
    LOGGER.error("Unexpected error handling {}", Encode.forJava(request.getRequestURI()), exception);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    problem.setTitle("Internal error");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", "INTERNAL_ERROR");
    return problem;
  }

  /**
   * Builds the common RFC 7807 shape for a mapped domain exception, adding {@code code} when the
   * exception exposes one.
   *
   * @param status the HTTP status to report
   * @param exception the exception being mapped
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body
   */
  private ProblemDetail problemDetail(
      HttpStatus status, RuntimeException exception, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    problem.setTitle(status.getReasonPhrase());
    problem.setInstance(URI.create(request.getRequestURI()));
    if (exception instanceof HasErrorCode hasErrorCode) {
      problem.setProperty("code", hasErrorCode.errorCode());
    }
    return problem;
  }

  /**
   * Derives {@code <MODULE>_VALIDATION_FAILED} from the invalid DTO's package ({@code
   * infrastructure.web.request.<module>}), so a new module needs no new handler here (ADR-012).
   *
   * @param exception the validation failure
   * @return the module-scoped validation error code
   */
  private String validationCodeFor(MethodArgumentNotValidException exception) {
    String[] packageParts =
        exception.getParameter().getParameterType().getPackageName().split("\\.");
    String module = packageParts[packageParts.length - 1];
    return module.toUpperCase(Locale.ROOT) + "_VALIDATION_FAILED";
  }

  /** One rejected field of a {@link MethodArgumentNotValidException}. */
  private record FieldViolation(String field, String code) {}
}
