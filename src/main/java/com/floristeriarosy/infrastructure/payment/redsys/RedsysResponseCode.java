package com.floristeriarosy.infrastructure.payment.redsys;

/** Redsys convention: a {@code Ds_Response} from 0000 to 0099 means the transaction is approved. */
public final class RedsysResponseCode {

  private static final String FOUR_DIGITS_WITH_LEADING_ZERO = "0\\d{3}";
  private static final int MAX_APPROVED = 99;

  private RedsysResponseCode() {}

  /**
   * @param responseCode the {@code Ds_Response} value, possibly {@code null}
   * @return whether Redsys considers the transaction approved (0000-0099)
   */
  public static boolean isApproved(String responseCode) {
    return responseCode != null
        && responseCode.matches(FOUR_DIGITS_WITH_LEADING_ZERO)
        && Integer.parseInt(responseCode) <= MAX_APPROVED;
  }
}
