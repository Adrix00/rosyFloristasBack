package com.floristeriarosy.infrastructure.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.RateLimitExceededException;
import com.floristeriarosy.infrastructure.security.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** {@link RateLimitFilter}: ADR-016's dual bucket, exhaustion, and the uniform 429. */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

  private static final String ADMIN_LOGIN_PATH = "/api/v1/auth/admin/login";
  private static final RateLimitProperties.Endpoint TINY_LIMIT =
      new RateLimitProperties.Endpoint(2, Duration.ofMinutes(15), 2, Duration.ofMinutes(15));
  private static final RateLimitProperties.Endpoint ROOMY_LIMIT =
      new RateLimitProperties.Endpoint(100, Duration.ofMinutes(15), 2, Duration.ofMinutes(15));

  @Mock private PiiCryptoPort piiCryptoPort;
  @Mock private AccessTokenPort accessTokenPort;
  @Mock private HandlerExceptionResolver handlerExceptionResolver;
  @Mock private FilterChain filterChain;

  private RateLimitFilter filterWith(RateLimitProperties.Endpoint adminLogin) {
    RateLimitProperties properties = new RateLimitProperties(List.of(), adminLogin, ROOMY_LIMIT, ROOMY_LIMIT);
    return new RateLimitFilter(properties, piiCryptoPort, accessTokenPort, handlerExceptionResolver);
  }

  private MockHttpServletRequest loginRequestFor(String email) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", ADMIN_LOGIN_PATH);
    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
    request.setContent(
        ("{\"email\":\"" + email + "\",\"password\":\"whatever\"}").getBytes(StandardCharsets.UTF_8));
    return request;
  }

  @Test
  void allowsRequestsUpToTheIdentifierCapacity() throws Exception {
    RateLimitFilter filter = filterWith(TINY_LIMIT);
    when(piiCryptoPort.hmac(any())).thenReturn("fixed-identifier".getBytes(StandardCharsets.UTF_8));

    for (int i = 0; i < 2; i++) {
      filter.doFilter(loginRequestFor("owner@rosy.test"), new MockHttpServletResponse(), filterChain);
    }

    verify(filterChain, times(2)).doFilter(any(), any());
    verify(handlerExceptionResolver, never()).resolveException(any(), any(), any(), any());
  }

  @Test
  void rejectsWithA429AndRetryAfterOnceTheIdentifierBucketIsExhausted() throws Exception {
    RateLimitFilter filter = filterWith(TINY_LIMIT);
    when(piiCryptoPort.hmac(any())).thenReturn("fixed-identifier".getBytes(StandardCharsets.UTF_8));

    filter.doFilter(loginRequestFor("owner@rosy.test"), new MockHttpServletResponse(), filterChain);
    filter.doFilter(loginRequestFor("owner@rosy.test"), new MockHttpServletResponse(), filterChain);
    filter.doFilter(loginRequestFor("owner@rosy.test"), new MockHttpServletResponse(), filterChain);

    verify(filterChain, times(2)).doFilter(any(), any());
    ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
    verify(handlerExceptionResolver).resolveException(any(), any(), isNull(), captor.capture());
    assertThat(captor.getValue()).isInstanceOf(RateLimitExceededException.class);
    assertThat(((RateLimitExceededException) captor.getValue()).retryAfterSeconds()).isPositive();
  }

  @Test
  void givesTheSameRejectionToAnUnknownIdentifierAsToAKnownOne() throws Exception {
    // Different identifier keys, same outcome: nothing in the filter distinguishes "this HMAC
    // belongs to a real account" from "this HMAC belongs to nothing" (00-security, rule 7).
    RateLimitFilter unknownFilter = filterWith(TINY_LIMIT);
    when(piiCryptoPort.hmac(any())).thenReturn("unknown-email-hash".getBytes(StandardCharsets.UTF_8));
    for (int i = 0; i < 3; i++) {
      unknownFilter.doFilter(
          loginRequestFor("unknown@rosy.test"), new MockHttpServletResponse(), filterChain);
    }

    RateLimitFilter knownFilter = filterWith(TINY_LIMIT);
    when(piiCryptoPort.hmac(any())).thenReturn("known-email-hash".getBytes(StandardCharsets.UTF_8));
    for (int i = 0; i < 3; i++) {
      knownFilter.doFilter(loginRequestFor("owner@rosy.test"), new MockHttpServletResponse(), filterChain);
    }

    verify(filterChain, times(4)).doFilter(any(), any());
    verify(handlerExceptionResolver, times(2))
        .resolveException(any(), any(), isNull(), any(RateLimitExceededException.class));
  }

  @Test
  void doesNotRateLimitAnUnrelatedEndpoint() throws Exception {
    RateLimitFilter filter = filterWith(TINY_LIMIT);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/categories");

    for (int i = 0; i < 5; i++) {
      filter.doFilter(request, new MockHttpServletResponse(), filterChain);
    }

    verify(filterChain, times(5)).doFilter(any(), any());
    verify(handlerExceptionResolver, never()).resolveException(any(), any(), any(), any());
    verify(piiCryptoPort, never()).hmac(any());
  }

  @Test
  void fallsBackToTheIpBucketWhenTheBodyHasNoIdentifiableEmail() throws Exception {
    RateLimitFilter filter = filterWith(ROOMY_LIMIT);
    MockHttpServletRequest malformed = new MockHttpServletRequest("POST", ADMIN_LOGIN_PATH);
    malformed.setContentType(MediaType.APPLICATION_JSON_VALUE);
    malformed.setContent("not json at all".getBytes(StandardCharsets.UTF_8));

    filter.doFilter(malformed, new MockHttpServletResponse(), filterChain);
    filter.doFilter(malformed, new MockHttpServletResponse(), filterChain);
    filter.doFilter(malformed, new MockHttpServletResponse(), filterChain);

    // IP capacity is 2 in ROOMY_LIMIT's own ip bucket definition here — third call rejected.
    verify(filterChain, times(2)).doFilter(any(), any());
    verify(piiCryptoPort, never()).hmac(any());
  }
}
