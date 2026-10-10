package com.floristeriarosy.application.order.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** {@link PendingCheckoutPayloadReader} — one decrypt-and-parse for every reader of the payload. */
class PendingCheckoutPayloadReaderTest {

  private final PiiCryptoPort piiCryptoPort = mock(PiiCryptoPort.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PendingCheckoutPayloadReader reader =
      new PendingCheckoutPayloadReader(piiCryptoPort, objectMapper);

  @Test
  void decryptsAndParsesAPayload() throws Exception {
    PendingCheckoutPayload payload =
        new PendingCheckoutPayload(
            "WEB",
            "NONE",
            "WEB-20260920-000001",
            null,
            "session-1",
            "Ana",
            "García",
            "ana@example.com",
            "600111222",
            List.of(),
            null,
            null,
            new BigDecimal("20.00"),
            BigDecimal.ZERO);
    when(piiCryptoPort.decrypt(new byte[] {1})).thenReturn(objectMapper.writeValueAsString(payload));

    assertThat(reader.read(new byte[] {1}).orderNumber()).isEqualTo("WEB-20260920-000001");
  }

  @Test
  void anUnparseablePlaintextFailsWithoutLeakingAnyFragmentOfIt() {
    when(piiCryptoPort.decrypt(new byte[] {1})).thenReturn("{\"buyerEmail\":\"secret@example.com\" oops");

    assertThatThrownBy(() -> reader.read(new byte[] {1}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("secret@example.com")
        .hasNoCause();
  }
}
