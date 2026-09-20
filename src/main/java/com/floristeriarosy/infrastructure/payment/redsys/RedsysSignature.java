package com.floristeriarosy.infrastructure.payment.redsys;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Redsys TPV Virtual signature algorithm (ADR-017): a per-order 3DES-CBC-derived key, then
 * HMAC-SHA256 over the Base64 {@code Ds_MerchantParameters}. Pure and stateless — the same
 * computation signs an outgoing request and verifies an incoming notification.
 */
public final class RedsysSignature {

  private static final String DESEDE_TRANSFORMATION = "DESede/CBC/NoPadding";
  private static final byte[] ZERO_IV = new byte[8];

  private RedsysSignature() {}

  /**
   * @param merchantKeyBase64 the merchant's secret key, Base64-encoded, as provided by the bank
   * @param orderNumber the order number this signature is diversified by
   * @param merchantParametersBase64 the Base64-encoded {@code Ds_MerchantParameters} to sign
   * @return the Base64-encoded {@code Ds_Signature}
   */
  public static String sign(String merchantKeyBase64, String orderNumber, String merchantParametersBase64) {
    byte[] orderKey = deriveOrderKey(merchantKeyBase64, orderNumber);
    return hmacSha256Base64(orderKey, merchantParametersBase64);
  }

  /**
   * @param merchantKeyBase64 the merchant's secret key, Base64-encoded
   * @param orderNumber the order number the received signature is diversified by
   * @param merchantParametersBase64 the Base64-encoded {@code Ds_MerchantParameters} received
   * @param receivedSignatureBase64 the {@code Ds_Signature} received alongside it
   * @return whether the received signature matches what this merchant key would have produced
   */
  public static boolean verify(
      String merchantKeyBase64,
      String orderNumber,
      String merchantParametersBase64,
      String receivedSignatureBase64) {
    String expected = sign(merchantKeyBase64, orderNumber, merchantParametersBase64);
    return constantTimeEquals(expected, receivedSignatureBase64);
  }

  /**
   * @param merchantKeyBase64 the merchant's secret key, Base64-encoded
   * @param orderNumber the order number to diversify the key by
   * @return the per-order 3DES-derived key
   */
  private static byte[] deriveOrderKey(String merchantKeyBase64, String orderNumber) {
    try {
      byte[] key = Base64.getDecoder().decode(merchantKeyBase64);
      byte[] padded = padToMultipleOf8(orderNumber.getBytes(StandardCharsets.UTF_8));
      Cipher cipher = Cipher.getInstance(DESEDE_TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "DESede"), new IvParameterSpec(ZERO_IV));
      return cipher.doFinal(padded);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not derive the Redsys per-order key", e);
    }
  }

  /**
   * @param key the HMAC key
   * @param value the value to authenticate
   * @return the Base64-encoded HMAC-SHA256 of {@code value} under {@code key}
   */
  private static String hmacSha256Base64(byte[] key, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not compute the Redsys HMAC", e);
    }
  }

  /**
   * @param input the bytes to pad
   * @return {@code input} padded with trailing zero bytes to the next multiple of 8, unchanged if
   *     already a multiple of 8
   */
  private static byte[] padToMultipleOf8(byte[] input) {
    int remainder = input.length % 8;
    return remainder == 0 ? input : Arrays.copyOf(input, input.length + (8 - remainder));
  }

  /**
   * @param a the first value
   * @param b the second value
   * @return whether {@code a} and {@code b} decode to the same bytes, compared in constant time to
   *     avoid a timing side-channel on signature verification. Both standard and URL-safe Base64 are
   *     accepted.
   */
  private static boolean constantTimeEquals(String a, String b) {
    try {
      return MessageDigest.isEqual(RedsysBase64.decode(a), RedsysBase64.decode(b));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
