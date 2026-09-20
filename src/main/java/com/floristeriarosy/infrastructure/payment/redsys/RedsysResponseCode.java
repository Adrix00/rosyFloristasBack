package com.floristeriarosy.infrastructure.payment.redsys;

/**
 * Redsys {@code Ds_Response} conventions: 0000-0099 approves an authorization; a refund
 * ({@code TransactionType 3}) is approved with 0900, "authorized for refunds and confirmations"
 * (observed against the Redsys sandbox).
 */
public final class RedsysResponseCode {

  private static final String FOUR_DIGITS_WITH_LEADING_ZERO = "0\\d{3}";
  private static final int MAX_APPROVED = 99;
  private static final String REFUND_APPROVED = "0900";

  private RedsysResponseCode() {}

  /**
   * @param responseCode the {@code Ds_Response} of a refund, possibly {@code null}
   * @return whether Redsys approved the refund (0900)
   */
  public static boolean isRefundApproved(String responseCode) {
    return REFUND_APPROVED.equals(responseCode);
  }

  /**
   * @param responseCode the {@code Ds_Response} of an authorization, possibly {@code null}
   * @return whether Redsys considers the authorization approved (0000-0099)
   */
  public static boolean isApproved(String responseCode) {
    return responseCode != null
        && responseCode.matches(FOUR_DIGITS_WITH_LEADING_ZERO)
        && Integer.parseInt(responseCode) <= MAX_APPROVED;
  }
}
