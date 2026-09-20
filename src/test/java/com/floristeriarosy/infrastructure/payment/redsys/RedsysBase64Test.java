package com.floristeriarosy.infrastructure.payment.redsys;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/** {@link RedsysBase64} accepts both standard and URL-safe Base64. */
class RedsysBase64Test {

  @Test
  void decodesStandardBase64() {
    String standard = Base64.getEncoder().encodeToString("hello".getBytes());
    assertThat(new String(RedsysBase64.decode(standard))).isEqualTo("hello");
  }

  @Test
  void decodesUrlSafeBase64() {
    String standard = Base64.getEncoder().encodeToString(new byte[] {-1, -2, -3, -4, -5});
    String urlSafe = Base64.getUrlEncoder().encodeToString(new byte[] {-1, -2, -3, -4, -5});

    assertThat(RedsysBase64.decode(urlSafe)).containsExactly(RedsysBase64.decode(standard));
  }

  @Test
  void decodesUrlSafeWithoutPadding() {
    String unpadded = Base64.getUrlEncoder().withoutPadding().encodeToString("abc".getBytes());

    assertThat(new String(RedsysBase64.decode(unpadded))).isEqualTo("abc");
  }
}
