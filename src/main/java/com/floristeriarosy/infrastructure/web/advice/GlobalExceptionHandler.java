package com.floristeriarosy.infrastructure.web.advice;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.ForbiddenException;
import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.HasErrorDetails;
import com.floristeriarosy.domain.exception.NotFoundException;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.exception.TooManyRequestsException;
import com.floristeriarosy.domain.exception.UnauthorizedException;
import com.floristeriarosy.domain.exception.UnprocessableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Locale;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates domain exceptions and Bean Validation failures to RFC 7807 (ADR-012). One advice for
 * the whole application: each module's exceptions extend one of the three kind base classes in
 * {@code domain.exception}, so a new module needs no new handler method here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

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
    LOGGER.debug(
        "409 on {}: {}", Encode.forJava(request.getRequestURI()), Encode.forJava(exception.getMessage()));
    return problemDetail(HttpStatus.CONFLICT, exception, request);
  }

  /**
   * Backstop for an {@code @Version} conflict that reached the advice untranslated (ADR-009).
   * Every persistence adapter is expected to flush inside its own {@code try} and translate this
   * into {@link ResourceModifiedException} itself; this handler exists so that an adapter which
   * forgets to answers 409 anyway, never a 500 with an internal message on the wire.
   *
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body, with {@code code=RESOURCE_MODIFIED}
   */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLocking(HttpServletRequest request) {
    LOGGER.debug("409 on {}: optimistic locking conflict", Encode.forJava(request.getRequestURI()));
    return problemDetail(
        HttpStatus.CONFLICT,
        new ResourceModifiedException("The resource was modified concurrently"),
        request);
  }

  /**
   * Backstop for a database constraint violation that reached the advice untranslated. The
   * constraint's name and the driver's message never leave the server (00-security-validation-
   * integrity.md, section 9): a violation is a conflict the client caused, so it is 409, not 500,
   * but it says nothing about which constraint.
   *
   * @param exception the constraint violation the driver raised
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body, with {@code code=CONFLICT}
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrityViolation(
      DataIntegrityViolationException exception, HttpServletRequest request) {
    LOGGER.warn(
        "409 on {}: untranslated constraint violation",
        Encode.forJava(request.getRequestURI()),
        exception);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "The request conflicts with the current state of the resource");
    problem.setTitle(HttpStatus.CONFLICT.getReasonPhrase());
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", "CONFLICT");
    return problem;
  }

  /**
   * Maps a malformed path variable or query parameter to 400 — a {@code UUID} path segment that is
   * not a UUID, or {@code ?minPrice=abc}. Without this handler Spring's own resolution failure
   * falls through to {@link #handleUnexpected}, which reports a caller's typo as a 500.
   *
   * <p>Names the parameter, never the rejected value: the name comes from the controller's own
   * signature, the value is caller-controlled.
   *
   * @param exception the type-conversion failure Spring raised
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body, with {@code code=BAD_REQUEST}
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleTypeMismatch(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    LOGGER.debug(
        "400 on {}: parameter {} has the wrong type",
        Encode.forJava(request.getRequestURI()),
        Encode.forJava(exception.getName()));
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + exception.getName() + "'");
    problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", "BAD_REQUEST");
    return problem;
  }

  /**
   * Maps any {@link UnauthorizedException} to 401.
   *
   * @param exception the domain exception that was thrown
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body
   */
  @ExceptionHandler(UnauthorizedException.class)
  public ProblemDetail handleUnauthorized(
      UnauthorizedException exception, HttpServletRequest request) {
    LOGGER.debug("401 on {}: {}", Encode.forJava(request.getRequestURI()), exception.getMessage());
    return problemDetail(HttpStatus.UNAUTHORIZED, exception, request);
  }

  /**
   * Maps any {@link ForbiddenException} to 403.
   *
   * @param exception the domain exception that was thrown
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body
   */
  @ExceptionHandler(ForbiddenException.class)
  public ProblemDetail handleForbidden(ForbiddenException exception, HttpServletRequest request) {
    LOGGER.debug("403 on {}: {}", Encode.forJava(request.getRequestURI()), exception.getMessage());
    return problemDetail(HttpStatus.FORBIDDEN, exception, request);
  }

  /**
   * Maps a Spring Security {@link AccessDeniedException} to 403 — or to 401 when the caller behind
   * a {@code @PreAuthorize} denial turns out to be anonymous.
   *
   * <p>{@code @PreAuthorize} denial (Spring Security 6.3+'s {@code AuthorizationDeniedException})
   * is one exception type for two different causes: an anonymous caller and an authenticated
   * caller with the wrong role. They used to be told apart by {@code ExceptionTranslationFilter}
   * itself, but a {@code @ExceptionHandler} here intercepts the exception inside
   * {@code DispatcherServlet}'s own dispatch, before it can ever reach that filter — so this method
   * re-does the same anonymous check {@code ExceptionTranslationFilter} would have, instead of
   * always answering 403 (which would leak a role-existence signal to an anonymous caller: auth.md
   * rule 3.3, 00-security rule 7). A {@link CsrfException} is excluded from that check: an invalid
   * or missing CSRF token is 403 unconditionally, authenticated or not — CSRF protection is orthogonal
   * to who the caller is. Never exposes the framework's own message: a CSRF failure message can be
   * more specific than 00-security's "no internal detail" rule wants on the wire.
   *
   * @param exception the exception Spring Security's method security or CSRF filter raised
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean anonymous = authentication == null || TRUST_RESOLVER.isAnonymous(authentication);
    if (anonymous && !(exception instanceof CsrfException)) {
      return unauthenticatedProblem(request);
    }
    LOGGER.debug("403 on {}: access denied", Encode.forJava(request.getRequestURI()));
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access is denied");
    problem.setTitle(HttpStatus.FORBIDDEN.getReasonPhrase());
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }

  /**
   * Maps a Spring Security {@link AuthenticationException} to 401 — a missing, malformed, expired
   * or wrong-{@code typ} bearer token (ADR-008). Never exposes the decoder's own message.
   *
   * @param exception the exception Spring Security's resource server filter raised
   * @param request the failed request, for {@code instance}
   * @return the RFC 7807 body
   */
  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthenticationException(
      AuthenticationException exception, HttpServletRequest request) {
    return unauthenticatedProblem(request);
  }

  /**
   * @param request the failed request, for {@code instance}
   * @return the 401 RFC 7807 body shared by an anonymous caller and an outright authentication
   *     failure — never the framework's own message, which can describe why decoding failed
   */
  private ProblemDetail unauthenticatedProblem(HttpServletRequest request) {
    LOGGER.debug("401 on {}: authentication required", Encode.forJava(request.getRequestURI()));
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required");
    problem.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }

  /**
   * Maps any {@link TooManyRequestsException} to 429, with a {@code Retry-After} header (ADR-016).
   * The response is identical whether the bucket's identifier corresponds to a real account or
   * not — a 429 that only appeared for real accounts would itself be an account enumerator.
   *
   * @param exception the domain exception that was thrown
   * @param request the failed request, for {@code instance}
   * @param response the response to add the {@code Retry-After} header to
   * @return the RFC 7807 body
   */
  @ExceptionHandler(TooManyRequestsException.class)
  public ProblemDetail handleTooManyRequests(
      TooManyRequestsException exception, HttpServletRequest request, HttpServletResponse response) {
    LOGGER.debug("429 on {}: {}", Encode.forJava(request.getRequestURI()), exception.getMessage());
    response.setHeader("Retry-After", String.valueOf(exception.retryAfterSeconds()));
    return problemDetail(HttpStatus.TOO_MANY_REQUESTS, exception, request);
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
    LOGGER.error(
        "Unexpected error handling {}", Encode.forJava(request.getRequestURI()), exception);
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
    if (exception instanceof HasErrorDetails hasErrorDetails) {
      hasErrorDetails.errorDetails().forEach(problem::setProperty);
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
