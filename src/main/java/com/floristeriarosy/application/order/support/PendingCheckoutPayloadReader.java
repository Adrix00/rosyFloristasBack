package com.floristeriarosy.application.order.support;

import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Decrypts and deserializes the {@link PendingCheckoutPayload} held in {@code
 * idempotency_keys.checkout_payload_encrypted} (ADR-005, ADR-017). One place for what the
 * confirmation, the notification and the abandoned-checkout cleanup all need.
 *
 * <p>Placed in {@code application.order.support} for the same reason as {@link
 * OrderReversalComponent}: a plain internal helper, no {@code *UseCase} interface, never swapped.
 */
@Component
public class PendingCheckoutPayloadReader {

  private final PiiCryptoPort piiCryptoPort;
  private final ObjectMapper objectMapper;

  /**
   * @param piiCryptoPort decrypts the payload
   * @param objectMapper deserializes it; rebuilt so this reader does not hold the caller's mutable
   *     reference (SpotBugs EI_EXPOSE_REP2)
   */
  public PendingCheckoutPayloadReader(PiiCryptoPort piiCryptoPort, ObjectMapper objectMapper) {
    this.piiCryptoPort = piiCryptoPort;
    this.objectMapper = objectMapper.rebuild().build();
  }

  /**
   * @param encryptedPayload the stored ciphertext
   * @return the decoded payload
   * @throws IllegalStateException the plaintext is not a valid payload; the message never carries
   *     any fragment of it
   */
  public PendingCheckoutPayload read(byte[] encryptedPayload) {
    String plaintext = piiCryptoPort.decrypt(encryptedPayload);
    try {
      return objectMapper.readValue(plaintext, PendingCheckoutPayload.class);
    } catch (JacksonException e) {
      throw new IllegalStateException(
          "Could not deserialize the pending checkout payload (" + e.getClass().getSimpleName() + ")");
    }
  }
}
