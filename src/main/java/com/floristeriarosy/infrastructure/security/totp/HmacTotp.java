package com.floristeriarosy.infrastructure.security.totp;

import com.floristeriarosy.application.auth.port.out.TotpPort;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implements {@link TotpPort} by hand with {@code javax.crypto.Mac} (RFC 6238 over RFC 4226's HOTP,
 * HMAC-SHA1, 6 digits, 30-second steps) — no TOTP library, per the JDK primitives this project
 * already uses elsewhere (BouncyCastle backs Argon2, not this).
 */
@Component
public class HmacTotp implements TotpPort {

  private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final String HMAC_ALGORITHM = "HmacSHA1";
  private static final String ISSUER = "Rosy Floristas";
  private static final int SECRET_BYTES = 20;
  private static final int TIME_STEP_SECONDS = 30;
  private static final int CODE_DIGITS = 6;
  private static final int CODE_MODULUS = 1_000_000;
  private static final int WINDOW_STEPS = 1;

  private static final Logger LOGGER = LoggerFactory.getLogger(HmacTotp.class);

  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * @return a new, random, Base32-encoded secret (20 bytes of entropy)
   */
  @Override
  public String generateSecret() {
    LOGGER.debug("generateSecret");
    byte[] secret = new byte[SECRET_BYTES];
    secureRandom.nextBytes(secret);
    String result = base32Encode(secret);
    LOGGER.debug("generateSecret -> generated");
    return result;
  }

  /**
   * @param secret the admin's Base32-encoded TOTP secret
   * @param code the 6-digit code presented by the caller
   * @param lastUsedStep the last step this secret has already consumed, or {@code null} if none
   * @return the accepted step, or empty if the code is wrong, out of window, or already consumed
   */
  @Override
  public Optional<Long> verify(String secret, String code, Long lastUsedStep) {
    byte[] key = base32Decode(secret);
    long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
    for (long delta = -WINDOW_STEPS; delta <= WINDOW_STEPS; delta++) {
      long step = currentStep + delta;
      String candidate = hotp(key, step);
      if (constantTimeEquals(candidate, code)) {
        if (lastUsedStep != null && step <= lastUsedStep) {
          LOGGER.debug("verify -> step {} already used", step);
          return Optional.empty();
        }
        LOGGER.debug("verify -> accepted step {}", step);
        return Optional.of(step);
      }
    }
    LOGGER.debug("verify -> no matching step");
    return Optional.empty();
  }

  /**
   * @param secret the admin's Base32-encoded TOTP secret
   * @param email the admin's email, shown as the account label in the authenticator app
   * @return the {@code otpauth://totp/...} URI for the enrollment QR code
   */
  @Override
  public String otpauthUri(String secret, String email) {
    String label = urlEncode(ISSUER + ":" + email);
    String issuer = urlEncode(ISSUER);
    return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer;
  }

  /**
   * @param key the raw (decoded) HOTP secret
   * @param counter the time step
   * @return the 6-digit code for that step (RFC 4226, dynamic truncation)
   */
  private String hotp(byte[] key, long counter) {
    byte[] counterBytes = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
      byte[] hash = mac.doFinal(counterBytes);
      int offset = hash[hash.length - 1] & 0x0F;
      int binary =
          ((hash[offset] & 0x7F) << 24)
              | ((hash[offset + 1] & 0xFF) << 16)
              | ((hash[offset + 2] & 0xFF) << 8)
              | (hash[offset + 3] & 0xFF);
      return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", binary % CODE_MODULUS);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to compute TOTP code", e);
    }
  }

  /**
   * @param a one code
   * @param b the other code
   * @return whether they are equal, compared in constant time (RFC 6238: never leak a partial match
   *     through timing)
   */
  private boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @param value the text to embed in the {@code otpauth://} URI
   * @return {@code value}, percent-encoded, with spaces as {@code %20} rather than {@code +}
   *     (correct for a URI path/query, unlike raw {@link URLEncoder} output)
   */
  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /**
   * @param data the raw bytes to encode
   * @return the Base32 (RFC 4648) encoding of {@code data}, uppercase, unpadded
   */
  private static String base32Encode(byte[] data) {
    StringBuilder result = new StringBuilder();
    int buffer = 0;
    int bitsLeft = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xFF);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        int index = (buffer >> (bitsLeft - 5)) & 0x1F;
        result.append(BASE32_ALPHABET.charAt(index));
        bitsLeft -= 5;
      }
    }
    if (bitsLeft > 0) {
      int index = (buffer << (5 - bitsLeft)) & 0x1F;
      result.append(BASE32_ALPHABET.charAt(index));
    }
    return result.toString();
  }

  /**
   * @param encoded a Base32 (RFC 4648) string, as produced by {@link #base32Encode}
   * @return the decoded raw bytes
   */
  private static byte[] base32Decode(String encoded) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int buffer = 0;
    int bitsLeft = 0;
    for (char c : encoded.trim().toUpperCase(Locale.ROOT).toCharArray()) {
      int index = BASE32_ALPHABET.indexOf(c);
      if (index < 0) {
        continue;
      }
      buffer = (buffer << 5) | index;
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        out.write((buffer >> (bitsLeft - 8)) & 0xFF);
        bitsLeft -= 8;
      }
    }
    return out.toByteArray();
  }
}
