package com.floristeriarosy.infrastructure.security.totp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * RFC 6238 Appendix B fixes its test vectors to specific instants and an 8-digit HMAC-SHA1 code;
 * since {@code (x mod 10^8) mod 10^6 == x mod 10^6}, the last 6 digits of each published code are
 * exactly what a 6-digit truncation produces at the same instant. Two of the published instants,
 * 1111111109 and 1111111111, land on consecutive 30-second steps (37037036 and 37037037) and
 * double as the &plusmn;1-step-window fixtures below — no vector had to be invented for that.
 */
class HmacTotpTest {

  /** RFC 6238 Appendix B's shared secret, ASCII {@code "12345678901234567890"}, Base32-encoded. */
  private static final String RFC_6238_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

  private static Stream<Arguments> rfc6238Vectors() {
    return Stream.of(
        Arguments.of(59L, "287082"),
        Arguments.of(1111111109L, "081804"),
        Arguments.of(1111111111L, "050471"),
        Arguments.of(1234567890L, "005924"),
        Arguments.of(2000000000L, "279037"),
        Arguments.of(20000000000L, "353130"));
  }

  @ParameterizedTest
  @MethodSource("rfc6238Vectors")
  void acceptsEachRfc6238Vector(long unixTime, String expectedCode) {
    HmacTotp totp = totpAt(unixTime);

    Optional<Long> accepted = totp.verify(RFC_6238_SECRET, expectedCode, null);

    assertThat(accepted).contains(unixTime / 30);
  }

  @Test
  void acceptsACodeFromOneStepInTheFuture() {
    HmacTotp totpAtStep37037036 = totpAt(1111111109L);

    Optional<Long> accepted = totpAtStep37037036.verify(RFC_6238_SECRET, "050471", null);

    assertThat(accepted).contains(37037037L);
  }

  @Test
  void acceptsACodeFromOneStepInThePast() {
    HmacTotp totpAtStep37037037 = totpAt(1111111111L);

    Optional<Long> accepted = totpAtStep37037037.verify(RFC_6238_SECRET, "081804", null);

    assertThat(accepted).contains(37037036L);
  }

  @Test
  void rejectsACodeMoreThanOneStepAway() {
    HmacTotp totpAtStep1 = totpAt(59L);

    Optional<Long> accepted = totpAtStep1.verify(RFC_6238_SECRET, "005924", null);

    assertThat(accepted).isEmpty();
  }

  @Test
  void rejectsAStepAlreadyConsumed() {
    HmacTotp totp = totpAt(1111111111L);

    Optional<Long> accepted = totp.verify(RFC_6238_SECRET, "050471", 37037037L);

    assertThat(accepted).isEmpty();
  }

  @Test
  void rejectsTheSameCodeSubmittedTwice() {
    HmacTotp totp = totpAt(1111111111L);

    Optional<Long> first = totp.verify(RFC_6238_SECRET, "050471", null);
    Optional<Long> second = totp.verify(RFC_6238_SECRET, "050471", first.orElseThrow());

    assertThat(first).contains(37037037L);
    assertThat(second).isEmpty();
  }

  @Test
  void rejectsAWrongCode() {
    HmacTotp totp = totpAt(1111111111L);

    Optional<Long> accepted = totp.verify(RFC_6238_SECRET, "000000", null);

    assertThat(accepted).isEmpty();
  }

  @Test
  void generatesADifferentBase32SecretEachTime() {
    HmacTotp totp = new HmacTotp();

    String first = totp.generateSecret();
    String second = totp.generateSecret();

    assertThat(first).isNotEqualTo(second);
    assertThat(first).matches("[A-Z2-7]+");
  }

  @Test
  void otpauthUriCarriesTheSecretAndTheEmailAsLabel() {
    HmacTotp totp = new HmacTotp();

    String uri = totp.otpauthUri(RFC_6238_SECRET, "owner@rosy.test");

    assertThat(uri).startsWith("otpauth://totp/Rosy%20Floristas:owner@rosy.test");
    assertThat(uri).contains("secret=" + RFC_6238_SECRET);
    assertThat(uri).contains("issuer=Rosy%20Floristas");
  }

  /**
   * @param unixTime the instant to fix this instance's clock to
   * @return a {@link HmacTotp} that treats {@code unixTime} as "now"
   */
  private HmacTotp totpAt(long unixTime) {
    return new HmacTotp(Clock.fixed(Instant.ofEpochSecond(unixTime), ZoneOffset.UTC));
  }
}
