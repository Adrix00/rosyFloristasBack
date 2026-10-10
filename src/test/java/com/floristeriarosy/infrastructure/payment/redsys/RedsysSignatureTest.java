package com.floristeriarosy.infrastructure.payment.redsys;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Redsys signature algorithm (ADR-017), verified against the documented sandbox key from
 * {@code GetNet.md}: {@code sq7HjrUOBfKmC576ILgskD5srU870gJ7}.
 */
class RedsysSignatureTest {

  private static final String SANDBOX_KEY = "sq7HjrUOBfKmC576ILgskD5srU870gJ7";

  @Test
  void signIsDeterministicForTheSameInputs() {
    String params = Base64.getEncoder().encodeToString("{\"amount\":\"1000\"}".getBytes());

    String first = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000001", params);
    String second = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000001", params);

    assertThat(first).isEqualTo(second);
  }

  @Test
  void signDiffersWhenTheOrderNumberDiffers() {
    String params = Base64.getEncoder().encodeToString("{\"amount\":\"1000\"}".getBytes());

    String forFirstOrder = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000001", params);
    String forSecondOrder = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000002", params);

    assertThat(forFirstOrder).isNotEqualTo(forSecondOrder);
  }

  @Test
  void signDiffersWhenTheParametersDiffer() {
    String first = RedsysSignature.sign(
        SANDBOX_KEY, "WEB-20260913-000001", Base64.getEncoder().encodeToString("{\"amount\":\"1000\"}".getBytes()));
    String second = RedsysSignature.sign(
        SANDBOX_KEY, "WEB-20260913-000001", Base64.getEncoder().encodeToString("{\"amount\":\"2000\"}".getBytes()));

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void verifyAcceptsASignatureThisSameAlgorithmProduced() {
    String params = Base64.getEncoder().encodeToString("{\"amount\":\"1000\"}".getBytes());
    String signature = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000001", params);

    boolean valid = RedsysSignature.verify(SANDBOX_KEY, "WEB-20260913-000001", params, signature);

    assertThat(valid).isTrue();
  }

  @Test
  void verifyRejectsATamperedSignature() {
    String params = Base64.getEncoder().encodeToString("{\"amount\":\"1000\"}".getBytes());
    String signature = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000001", params);

    boolean valid = RedsysSignature.verify(SANDBOX_KEY, "WEB-20260913-000001", params, signature + "x");

    assertThat(valid).isFalse();
  }

  @Test
  void signHandlesAnOrderNumberWhoseByteLengthIsAlreadyAMultipleOfEight() {
    String params = Base64.getEncoder().encodeToString("{}".getBytes());

    String signature = RedsysSignature.sign(SANDBOX_KEY, "12345678", params);

    assertThat(signature).isNotBlank();
  }

  @Test
  void verifyAcceptsAUrlSafeBase64Signature() {
    String params = Base64.getEncoder().encodeToString("{\"amount\":\"1000\"}".getBytes());
    String standardSignature = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000001", params);
    String urlSafeSignature = standardSignature.replace('+', '-').replace('/', '_');

    boolean valid = RedsysSignature.verify(SANDBOX_KEY, "WEB-20260913-000001", params, urlSafeSignature);

    assertThat(valid).isTrue();
  }

  @Test
  void verifyAcceptsAUrlSafeBase64MerchantParameters() {
    String params = Base64.getUrlEncoder().encodeToString("{\"amount\":\"1000\"}".getBytes());
    String signature = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000001", params);

    boolean valid = RedsysSignature.verify(SANDBOX_KEY, "WEB-20260913-000001", params, signature);

    assertThat(valid).isTrue();
  }
}
