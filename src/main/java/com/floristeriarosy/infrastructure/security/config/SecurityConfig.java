package com.floristeriarosy.infrastructure.security.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Placeholder while {@code feature/auth} is not implemented yet (dev-plan.md): every endpoint is
 * open, no {@code @PreAuthorize} is enforced. Role gating from category.md §4 is a tracked gap to
 * close once JWT auth lands.
 */
@Configuration
public class SecurityConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);

  /**
   * Builds the (currently permissive) security filter chain. Runs once at startup.
   *
   * @param http the HTTP security builder Spring provides
   * @return the configured filter chain
   * @throws Exception propagated from {@link HttpSecurity#build()}
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    LOGGER.warn(
        "SecurityConfig placeholder active: every endpoint is open, no @PreAuthorize enforced "
            + "(dev-plan.md, pending feature/auth)");

    // Stateless: no HttpSession, so a server-side CSRF token repository is not an option — the
    // token lives in a cookie the SPA reads back and echoes as X-XSRF-TOKEN on every mutating
    // request. addFilterAfter forces the token to be read (and therefore its Set-Cookie written)
    // on every request, not only ones the app happens to read CsrfToken on — Spring's own
    // documented pattern for a stateless CSRF cookie (see CsrfCookieFilter below).
    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
    return http.build();
  }

  /**
   * Reads the request-scoped {@link CsrfToken} on every request so its deferred value is
   * resolved and {@link CookieCsrfTokenRepository} writes the {@code XSRF-TOKEN} cookie even on
   * requests that never otherwise touch the token (e.g. a plain {@code GET}).
   */
  private static final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
      if (csrfToken != null) {
        csrfToken.getToken();
      }
      filterChain.doFilter(request, response);
    }
  }
}
