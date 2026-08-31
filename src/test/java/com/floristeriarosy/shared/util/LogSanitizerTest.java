package com.floristeriarosy.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

  @Test
  void stripsCarriageReturnAndNewline() {
    assertThat(LogSanitizer.sanitize("Ramos\r\nFAKE LOG LINE injected=true"))
        .isEqualTo("Ramos  FAKE LOG LINE injected=true");
  }

  @Test
  void leavesOrdinaryTextUnchanged() {
    assertThat(LogSanitizer.sanitize("Ramos de novia")).isEqualTo("Ramos de novia");
  }

  @Test
  void passesNullThrough() {
    assertThat(LogSanitizer.sanitize(null)).isNull();
  }
}
