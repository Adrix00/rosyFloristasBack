package com.floristeriarosy.infrastructure.payment.redsys;

import java.util.Base64;

/**
 * Redsys may send either standard Base64 ({@code +/}) or URL-safe Base64 ({@code -_}) in
 * {@code Ds_MerchantParameters} and {@code Ds_Signature}. This decoder accepts both alphabets so
 * the signature verification does not fail because of padding/alphabet differences.
 */
public final class RedsysBase64 {

  private RedsysBase64() {}

  /**
   * @param value a Base64 value, possibly URL-safe and possibly unpadded
   * @return the decoded bytes
   */
  public static byte[] decode(String value) {
    String normalized = value.replace('-', '+').replace('_', '/');
    int padding = (4 - normalized.length() % 4) % 4;
    String padded = normalized + "=".repeat(padding);
    return Base64.getDecoder().decode(padded);
  }
}
