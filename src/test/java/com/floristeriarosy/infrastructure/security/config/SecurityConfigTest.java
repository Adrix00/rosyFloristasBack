package com.floristeriarosy.infrastructure.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.floristeriarosy.infrastructure.security.filter.PasswordChangeRequiredFilter;
import com.floristeriarosy.infrastructure.security.filter.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * {@link RateLimitFilter} and {@link PasswordChangeRequiredFilter} are {@code @Component} beans of
 * type {@code Filter}, which Spring Boot auto-registers as plain servlet filters in addition to
 * {@link SecurityConfig#securityFilterChain}'s own wiring. Without a disabled {@link
 * FilterRegistrationBean} for each, every request would run both filters twice — invisible today
 * only because both extend {@code OncePerRequestFilter}.
 */
class SecurityConfigTest {

  private final SecurityConfig config = new SecurityConfig();

  @Test
  void disablesTheAutoRegisteredRateLimitFilter() {
    FilterRegistrationBean<RateLimitFilter> registration =
        config.rateLimitFilterRegistration(mock(RateLimitFilter.class));

    assertThat(registration.isEnabled()).isFalse();
  }

  @Test
  void disablesTheAutoRegisteredPasswordChangeRequiredFilter() {
    FilterRegistrationBean<PasswordChangeRequiredFilter> registration =
        config.passwordChangeRequiredFilterRegistration(mock(PasswordChangeRequiredFilter.class));

    assertThat(registration.isEnabled()).isFalse();
  }
}
