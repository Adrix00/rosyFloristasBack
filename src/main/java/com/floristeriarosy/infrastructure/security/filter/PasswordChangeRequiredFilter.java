package com.floristeriarosy.infrastructure.security.filter;

import com.floristeriarosy.domain.exception.auth.PasswordChangeRequiredException;
import com.floristeriarosy.infrastructure.security.jwt.AccessTokenJwtClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Enforces auth.md rule 3.9: a session whose access token carries {@code pwd_change_required} may
 * only reach {@code POST /api/v1/admin/me/password} or {@code POST /api/v1/auth/logout}; every
 * other request gets 403 {@code PASSWORD_CHANGE_REQUIRED}. One rule, in one filter, instead of a
 * repeated check in every use case.
 */
@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

  private static final String ALLOWED_PASSWORD_CHANGE_PATH = "/api/v1/admin/me/password";
  private static final String ALLOWED_LOGOUT_PATH = "/api/v1/auth/logout";

  private static final Logger LOGGER = LoggerFactory.getLogger(PasswordChangeRequiredFilter.class);

  private final HandlerExceptionResolver handlerExceptionResolver;

  /**
   * @param handlerExceptionResolver routes the rejection through the same {@code
   *     GlobalExceptionHandler} the rest of the API uses (ADR-012), instead of a raw servlet error
   */
  public PasswordChangeRequiredFilter(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
    this.handlerExceptionResolver = handlerExceptionResolver;
  }

  /**
   * @param request the incoming request
   * @param response the response to write to
   * @param filterChain the rest of the filter chain
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (requiresPasswordChange() && !isAllowedWhilePasswordChangeRequired(request)) {
      LOGGER.debug("passwordChangeRequired -> 403 on {}", Encode.forJava(request.getRequestURI()));
      handlerExceptionResolver.resolveException(
          request,
          response,
          null,
          new PasswordChangeRequiredException("This session must change its password first"));
      return;
    }
    filterChain.doFilter(request, response);
  }

  /**
   * @return whether the current request's authenticated JWT carries {@code pwd_change_required =
   *     true}
   */
  private boolean requiresPasswordChange() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication instanceof JwtAuthenticationToken jwtAuthentication
        && Boolean.TRUE.equals(
            jwtAuthentication.getToken().getClaim(AccessTokenJwtClaims.PASSWORD_CHANGE_REQUIRED));
  }

  /**
   * @param request the incoming request
   * @return whether {@code request} is one of the two endpoints reachable during a
   *     password-change-required session
   */
  private boolean isAllowedWhilePasswordChangeRequired(HttpServletRequest request) {
    if (!HttpMethod.POST.matches(request.getMethod())) {
      return false;
    }
    String path = request.getRequestURI();
    return ALLOWED_PASSWORD_CHANGE_PATH.equals(path) || ALLOWED_LOGOUT_PATH.equals(path);
  }
}
