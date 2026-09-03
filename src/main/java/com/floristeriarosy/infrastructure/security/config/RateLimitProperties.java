package com.floristeriarosy.infrastructure.security.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.rate-limit.*} (ADR-016). Only {@code adminLogin}, {@code adminMfa} and {@code
 * refresh} are read by {@link com.floristeriarosy.infrastructure.security.filter.RateLimitFilter}
 * in this branch; the {@code customer-*} rows in {@code application.yml} are values ADR-016 fixed
 * ahead of time for {@code feature/customer} and are simply unused until then.
 *
 * @param trustedProxies CIDR ranges allowed to set {@code CF-Connecting-IP}; empty means the
 *     header is never trusted and the socket address is always used
 * @param adminLogin limits for {@code POST /auth/admin/login}
 * @param adminMfa limits for {@code POST /auth/admin/mfa}
 * @param refresh limits for {@code POST /auth/refresh}
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    List<String> trustedProxies, Endpoint adminLogin, Endpoint adminMfa, Endpoint refresh) {

  /** Defensively copies {@code trustedProxies} (SpotBugs EI_EXPOSE_REP/REP2). */
  public RateLimitProperties {
    trustedProxies = List.copyOf(trustedProxies);
  }

  /**
   * One endpoint's dual bucket configuration (ADR-016): the more restrictive of the two decides,
   * and both are always consumed.
   *
   * @param identifierCapacity max attempts per {@code identifierWindow} for one identifier
   * @param identifierWindow the identifier bucket's refill window
   * @param ipCapacity max attempts per {@code ipWindow} for one client IP
   * @param ipWindow the IP bucket's refill window
   */
  public record Endpoint(
      long identifierCapacity, Duration identifierWindow, long ipCapacity, Duration ipWindow) {}
}
