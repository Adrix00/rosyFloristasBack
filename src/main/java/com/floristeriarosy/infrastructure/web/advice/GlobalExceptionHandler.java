package com.floristeriarosy.infrastructure.web.advice;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;
import com.floristeriarosy.domain.exception.UnprocessableException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
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

  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail handleNotFound(NotFoundException exception, HttpServletRequest request) {
    return problemDetail(HttpStatus.NOT_FOUND, exception, request);
  }

  @ExceptionHandler(ConflictException.class)
  public ProblemDetail handleConflict(ConflictException exception, HttpServletRequest request) {
    return problemDetail(HttpStatus.CONFLICT, exception, request);
  }

  @ExceptionHandler(UnprocessableException.class)
  public ProblemDetail handleUnprocessable(
      UnprocessableException exception, HttpServletRequest request) {
    return problemDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception, request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    String code = validationCodeFor(exception);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed");
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

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
    // Detalle solo en el log del servidor: la respuesta al cliente nunca lleva traza ni mensaje
    // interno (00-security-validation-integrity.md, sección 9).
    LOGGER.error("Unexpected error handling {}", request.getRequestURI(), exception);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    problem.setTitle("Internal error");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", "INTERNAL_ERROR");
    return problem;
  }

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

  // El DTO invalido vive en infrastructure.web.request.<modulo>: de ahi sale el prefijo del
  // codigo (ADR-012, "<MODULO>_VALIDATION_FAILED") sin que cada modulo tenga que declarar su
  // propio manejador aqui.
  private String validationCodeFor(MethodArgumentNotValidException exception) {
    String[] packageParts =
        exception.getParameter().getParameterType().getPackageName().split("\\.");
    String module = packageParts[packageParts.length - 1];
    return module.toUpperCase(Locale.ROOT) + "_VALIDATION_FAILED";
  }

  private record FieldViolation(String field, String code) {}
}
