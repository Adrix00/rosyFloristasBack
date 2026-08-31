package com.floristeriarosy.infrastructure.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Placeholder while {@code feature/auth} is not implemented yet (dev-plan.md): every endpoint is
 * open, no {@code @PreAuthorize} is enforced. Role gating from category.md §4 is a tracked gap to
 * close once JWT auth lands.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    // Stateless: no HttpSession, no session cookie, so there is no ambient credential for a
    // forged cross-site request to ride on. CSRF protection exists to defend a cookie-based
    // session; auth.md's refresh token will carry SameSite=Strict for that reason (00-security
    // §1) once it exists. Disabling the CSRF filter here is what Spring itself documents for a
    // stateless resource server, not a shortcut.
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
    return http.build();
  }
}
