package com.floristeriarosy.application.payment.port.in;

/**
 * Verifies and resolves Redsys's checkout notification (ADR-017). No controller-owned business
 * logic beyond signature verification — {@code order}'s {@code ConfirmCheckoutUseCase} does the rest.
 */
public interface ReceiveRedsysNotificationUseCase {

  /**
   * @param encodedMerchantParameters the raw {@code Ds_MerchantParameters} field, Base64-encoded
   * @param receivedSignature the raw {@code Ds_Signature} field
   */
  void execute(String encodedMerchantParameters, String receivedSignature);
}
