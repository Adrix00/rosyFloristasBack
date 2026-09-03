package com.floristeriarosy.infrastructure.security.config;

import com.floristeriarosy.infrastructure.security.filter.PasswordChangeRequiredFilter;
import com.floristeriarosy.infrastructure.security.filter.RateLimitFilter;
import com.floristeriarosy.infrastructure.security.jwt.AccessTokenJwtClaims;
import com.floristeriarosy.infrastructure.security.jwt.AccessTypeJwtValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Wires JWT-based authentication (ADR-008) and method-level authorization on top of it.
 *
 * <p>{@code authorizeHttpRequests} stays {@code permitAll()} on purpose (auth.md's endpoints are
 * public by design, and every other endpoint is gated by {@code @PreAuthorize} on its service, per
 * ADR-001/ADR-003 — the filter chain is not the place role rules live). An anonymous caller hitting
 * an annotated service still gets 401 (translated by {@code ExceptionTranslationFilter} because
 * anonymous authentication is never "fully authenticated"); an authenticated caller with the wrong
 * role gets 403.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  /**
   * Builds the security filter chain: CSRF (unchanged), stateless sessions, JWT resource server
   * with the {@code typ = "access"} guard, the password-change gate, and security-layer 401/403
   * routed through the same {@code ProblemDetail} advice as the rest of the API (ADR-012).
   *
   * @param http the HTTP security builder Spring provides
   * @param jwtDecoder verifies signature, expiry and {@code typ} of every {@code Authorization}
   *     bearer token
   * @param rateLimitFilter enforces ADR-016 on auth.md's three brute-forceable endpoints, before
   *     CSRF and before any use case runs
   * @param passwordChangeRequiredFilter enforces auth.md rule 3.9 once a JWT is authenticated
   * @param handlerExceptionResolver where a security-layer 401/403 is delegated, so it comes out as
   *     the same {@code ProblemDetail} shape as a domain exception
   * @return the configured filter chain
   * @throws Exception propagated from {@link HttpSecurity#build()}
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtDecoder jwtDecoder,
      RateLimitFilter rateLimitFilter,
      PasswordChangeRequiredFilter passwordChangeRequiredFilter,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver)
      throws Exception {
    // Stateless: no HttpSession, so a server-side CSRF token repository is not an option — the
    // token lives in a cookie the SPA reads back and echoes as X-XSRF-TOKEN on every mutating
    // request. addFilterAfter forces the token to be read (and therefore its Set-Cookie written)
    // on every request, not only ones the app happens to read CsrfToken on — Spring's own
    // documented pattern for a stateless CSRF cookie (see CsrfCookieFilter below).
    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .addFilterBefore(rateLimitFilter, CsrfFilter.class)
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwt ->
                        jwt.decoder(jwtDecoder)
                            .jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .addFilterAfter(passwordChangeRequiredFilter, BearerTokenAuthenticationFilter.class)
        .exceptionHandling(
            exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint(
                        (request, response, authException) ->
                            handlerExceptionResolver.resolveException(
                                request, response, null, authException))
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) ->
                            handlerExceptionResolver.resolveException(
                                request, response, null, accessDeniedException)))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
    return http.build();
  }

  /**
   * @param secret {@code app.security.jwt.secret}, the shared HS256 signing key
   * @return a decoder that verifies signature, expiry and {@code typ = "access"} — the last check
   *     is what stops an {@code mfaToken} from authenticating as a real session
   */
  @Bean
  public JwtDecoder jwtDecoder(@Value("${app.security.jwt.secret}") String secret) {
    SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> validator =
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefault(), new AccessTypeJwtValidator());
    decoder.setJwtValidator(validator);
    return decoder;
  }

  /**
   * @return a converter whose principal name is the JWT subject (the admin id {@code
   *     AdminController} reads via {@code Authentication#getName()}) and whose authorities map
   *     {@code role = OWNER} to {@code ROLE_OWNER} + {@code ROLE_ADMIN} (admin.md, section 1:
   *     {@code OWNER} is an {@code ADMIN} in everything but managing administrators, so granting
   *     both spares every {@code @PreAuthorize} from enumerating them)
   */
  private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(this::authoritiesOf);
    return converter;
  }

  /**
   * @param jwt the authenticated JWT
   * @return the granted authorities for its {@code role} claim, or none for a token without one
   *     (e.g. an {@code mfa} token, which never reaches an authorized endpoint anyway)
   */
  private Collection<GrantedAuthority> authoritiesOf(Jwt jwt) {
    String role = jwt.getClaimAsString(AccessTokenJwtClaims.ROLE);
    if (role == null) {
      return List.of();
    }
    if ("OWNER".equals(role)) {
      return List.of(
          new SimpleGrantedAuthority("ROLE_OWNER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
    return List.of(new SimpleGrantedAuthority("ROLE_" + role));
  }

  /**
   * Reads the request-scoped {@link CsrfToken} on every request so its deferred value is resolved
   * and {@link CookieCsrfTokenRepository} writes the {@code XSRF-TOKEN} cookie even on requests
   * that never otherwise touch the token (e.g. a plain {@code GET}).
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
