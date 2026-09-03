package com.floristeriarosy.infrastructure.security.filter;

import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.RateLimitExceededException;
import com.floristeriarosy.domain.model.auth.AccessTokenClaims;
import com.floristeriarosy.domain.model.auth.RefreshToken;
import com.floristeriarosy.infrastructure.security.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Enforces ADR-016's dual-bucket rate limit on {@code /auth/admin/login}, {@code /auth/admin/mfa}
 * and {@code /auth/refresh} — the only endpoints in this branch the ADR gives numbers to. Runs
 * before CSRF and before any use case, so a throttled request costs no Argon2id verification.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private static final String ADMIN_LOGIN_PATH = "/api/v1/auth/admin/login";
  private static final String ADMIN_MFA_PATH = "/api/v1/auth/admin/mfa";
  private static final String REFRESH_PATH = "/api/v1/auth/refresh";
  private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
  private static final String CF_CONNECTING_IP_HEADER = "CF-Connecting-IP";

  /** Longest window any bucket in this filter uses today (ADR-016: refresh, 1h). */
  private static final Duration IDLE_EVICTION_THRESHOLD = Duration.ofHours(2);

  private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitFilter.class);

  private final RateLimitProperties properties;
  private final PiiCryptoPort piiCryptoPort;
  private final AccessTokenPort accessTokenPort;
  private final ObjectMapper objectMapper;
  private final HandlerExceptionResolver handlerExceptionResolver;
  private final List<IpAddressMatcher> trustedProxyMatchers;
  private final Map<String, TrackedBucket> buckets = new ConcurrentHashMap<>();

  /**
   * @param properties the configured limits and trusted-proxy ranges (ADR-016)
   * @param piiCryptoPort computes the email HMAC used as the {@code admin/login} identifier key
   * @param accessTokenPort decodes the {@code mfaToken} to key {@code admin/mfa} by admin id
   * @param handlerExceptionResolver routes a rejection through the same {@code
   *     GlobalExceptionHandler} the rest of the API uses (ADR-012), instead of a raw servlet error
   */
  public RateLimitFilter(
      RateLimitProperties properties,
      PiiCryptoPort piiCryptoPort,
      AccessTokenPort accessTokenPort,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
    this.properties = properties;
    this.piiCryptoPort = piiCryptoPort;
    this.accessTokenPort = accessTokenPort;
    this.objectMapper = new ObjectMapper();
    this.handlerExceptionResolver = handlerExceptionResolver;
    this.trustedProxyMatchers =
        properties.trustedProxies().stream().map(IpAddressMatcher::new).toList();
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
    RateLimitProperties.Endpoint endpoint = endpointFor(request);
    if (endpoint == null) {
      filterChain.doFilter(request, response);
      return;
    }

    CachedBodyRequestWrapper cachedRequest = new CachedBodyRequestWrapper(request);
    String identifierKey = identifierKeyFor(request, cachedRequest);
    String ipKey = clientIp(request);

    ConsumptionProbe identifierProbe = null;
    if (identifierKey != null) {
      Bucket identifierBucket =
          bucketFor(
              request,
              "identifier",
              identifierKey,
              endpoint.identifierCapacity(),
              endpoint.identifierWindow());
      identifierProbe = identifierBucket.tryConsumeAndReturnRemaining(1);
    }
    Bucket ipBucket = bucketFor(request, "ip", ipKey, endpoint.ipCapacity(), endpoint.ipWindow());
    ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);

    boolean rejected = (identifierProbe != null && !identifierProbe.isConsumed()) || !ipProbe.isConsumed();
    if (rejected) {
      long waitNanos =
          Math.max(
              identifierProbe == null ? 0 : identifierProbe.getNanosToWaitForRefill(),
              ipProbe.getNanosToWaitForRefill());
      long retryAfterSeconds = Math.max(1, Duration.ofNanos(waitNanos).toSeconds());
      LOGGER.debug("rateLimit -> 429 on {}", Encode.forJava(request.getRequestURI()));
      handlerExceptionResolver.resolveException(
          request, response, null, new RateLimitExceededException("Rate limit exceeded", retryAfterSeconds));
      return;
    }

    filterChain.doFilter(cachedRequest, response);
  }

  /**
   * @param request the incoming request
   * @return the ADR-016 configuration for this request's endpoint, or {@code null} if this
   *     request is not one of the three rate-limited in this branch
   */
  private RateLimitProperties.Endpoint endpointFor(HttpServletRequest request) {
    if (!HttpMethod.POST.matches(request.getMethod())) {
      return null;
    }
    String path = request.getRequestURI();
    if (ADMIN_LOGIN_PATH.equals(path)) {
      return properties.adminLogin();
    }
    if (ADMIN_MFA_PATH.equals(path)) {
      return properties.adminMfa();
    }
    if (REFRESH_PATH.equals(path)) {
      return properties.refresh();
    }
    return null;
  }

  /**
   * @param request the original request, to tell endpoints apart
   * @param cachedRequest the body-cached request, safe to read from more than once
   * @return the identifier bucket key for this endpoint, or {@code null} if none can be resolved
   *     (the request then relies on the IP bucket alone; a request this malformed fails Bean
   *     Validation downstream regardless)
   */
  private String identifierKeyFor(HttpServletRequest request, CachedBodyRequestWrapper cachedRequest) {
    String path = request.getRequestURI();
    if (ADMIN_LOGIN_PATH.equals(path)) {
      String email = jsonField(cachedRequest, "email");
      return email == null ? null : bytesToHex(piiCryptoPort.hmac(normalizeEmail(email)));
    }
    if (ADMIN_MFA_PATH.equals(path)) {
      String mfaToken = jsonField(cachedRequest, "mfaToken");
      if (mfaToken == null) {
        return null;
      }
      return accessTokenPort.parse(mfaToken).map(AccessTokenClaims::subjectId).map(Object::toString).orElse(null);
    }
    if (REFRESH_PATH.equals(path)) {
      String cookie = cookieValue(request, REFRESH_TOKEN_COOKIE);
      return cookie == null ? null : bytesToHex(RefreshToken.hash(cookie));
    }
    return null;
  }

  /**
   * @param request the incoming request
   * @return the trusted-proxy-aware client IP: {@code CF-Connecting-IP} only when the socket
   *     address falls inside {@code app.rate-limit.trusted-proxies} (ADR-016); the socket address
   *     otherwise
   */
  private String clientIp(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    boolean fromTrustedProxy = trustedProxyMatchers.stream().anyMatch(matcher -> matcher.matches(remoteAddr));
    if (fromTrustedProxy) {
      String forwarded = request.getHeader(CF_CONNECTING_IP_HEADER);
      if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.trim();
      }
    }
    return remoteAddr;
  }

  /**
   * @param request the incoming request, for logging only
   * @param bucketType {@code "identifier"} or {@code "ip"}, keeps the two namespaces apart
   * @param key the identifier or IP this bucket is scoped to
   * @param capacity the bucket's capacity
   * @param window the refill window, greedy over its whole span (ADR-016)
   * @return the existing or newly created bucket for {@code key}
   */
  private Bucket bucketFor(
      HttpServletRequest request, String bucketType, String key, long capacity, Duration window) {
    String path = request.getRequestURI();
    String mapKey = path + ':' + bucketType + ':' + key;
    TrackedBucket tracked =
        buckets.computeIfAbsent(mapKey, ignored -> new TrackedBucket(newBucket(capacity, window)));
    tracked.touch();
    return tracked.bucket();
  }

  /**
   * @param capacity the bucket's capacity
   * @param window the refill window
   * @return a new bucket that refills greedily over {@code window}, not with a hard reset at the
   *     boundary (ADR-016)
   */
  private Bucket newBucket(long capacity, Duration window) {
    Bandwidth limit = Bandwidth.builder().capacity(capacity).refillGreedy(capacity, window).build();
    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Evicts buckets nobody has touched in {@link #IDLE_EVICTION_THRESHOLD}, so the map cannot grow
   * without bound (ADR-016).
   */
  @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
  void evictIdleBuckets() {
    Instant cutoff = Instant.now().minus(IDLE_EVICTION_THRESHOLD);
    int sizeBefore = buckets.size();
    buckets.values().removeIf(tracked -> tracked.lastAccessedAt().isBefore(cutoff));
    LOGGER.debug("evictIdleBuckets {} -> {} buckets", sizeBefore, buckets.size());
  }

  /**
   * @param request the request whose cookies to search
   * @param name the cookie name
   * @return its value, or {@code null} if absent
   */
  private String cookieValue(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  /**
   * @param request the body-cached request
   * @param fieldName the JSON field to read
   * @return its text value, or {@code null} if the body is not parseable JSON or lacks the field
   */
  private String jsonField(CachedBodyRequestWrapper request, String fieldName) {
    try {
      JsonNode node = objectMapper.readTree(request.cachedBody());
      JsonNode field = node == null ? null : node.get(fieldName);
      return field == null || field.isNull() ? null : field.asText();
    } catch (JacksonException malformed) {
      return null;
    }
  }

  /**
   * @param email the raw email
   * @return {@code email}, trimmed and lower-cased (00-security-validation-integrity.md, section
   *     4: normalize before hashing so equivalent inputs share one hash)
   */
  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * @param bytes the bytes to encode
   * @return their lowercase hex representation, for use as a plain map key
   */
  private String bytesToHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      hex.append(String.format(Locale.ROOT, "%02x", b));
    }
    return hex.toString();
  }

  /** A bucket plus the last instant it was touched, for {@link #evictIdleBuckets()}. */
  private static final class TrackedBucket {

    private final Bucket bucket;
    private volatile Instant lastAccessedAt;

    TrackedBucket(Bucket bucket) {
      this.bucket = bucket;
      this.lastAccessedAt = Instant.now();
    }

    void touch() {
      this.lastAccessedAt = Instant.now();
    }

    Bucket bucket() {
      return bucket;
    }

    Instant lastAccessedAt() {
      return lastAccessedAt;
    }
  }

  /**
   * Wraps the request so its body can be read here, for the identifier key, and again downstream
   * by the real message converter — a plain {@code HttpServletRequest}'s input stream can only be
   * consumed once.
   */
  private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
      super(request);
      this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    byte[] cachedBody() {
      return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
      return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {

      private final ByteArrayInputStream buffer;

      CachedBodyServletInputStream(byte[] body) {
        this.buffer = new ByteArrayInputStream(body);
      }

      @Override
      public boolean isFinished() {
        return buffer.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException("Async body reads are not used in this project");
      }

      @Override
      public int read() {
        return buffer.read();
      }
    }
  }
}
