package com.floristeriarosy.infrastructure.web.controller.payment;

import com.floristeriarosy.application.payment.port.in.ReceiveRedsysNotificationUseCase;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/payments/redsys/notification} (payment.md §4): unauthenticated, verified by
 * signature instead. Excluded from CSRF in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/payments/redsys")
public class RedsysNotificationController {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RedsysNotificationController.class);

  private final ReceiveRedsysNotificationUseCase receiveRedsysNotificationUseCase;

  /**
   * @param receiveRedsysNotificationUseCase verifies and resolves the notification
   */
  public RedsysNotificationController(
      ReceiveRedsysNotificationUseCase receiveRedsysNotificationUseCase) {
    this.receiveRedsysNotificationUseCase = receiveRedsysNotificationUseCase;
  }

  /**
   * @param dsSignatureVersion Redsys's signature version field, unused but required to be accepted
   * @param dsMerchantParameters the Base64-encoded parameters
   * @param dsSignature the signature to verify
   * @return {@code 200 OK} with plain-text body {@code "OK"} — Redsys retries until it sees exactly
   *     this
   */
  @PostMapping("/notification")
  public ResponseEntity<String> notification(
      @RequestParam("Ds_SignatureVersion") String dsSignatureVersion,
      @RequestParam("Ds_MerchantParameters") String dsMerchantParameters,
      @RequestParam("Ds_Signature") String dsSignature) {
    LOGGER.debug(
        "POST /api/v1/payments/redsys/notification dsSignatureVersion={}",
        Encode.forJava(dsSignatureVersion));
    receiveRedsysNotificationUseCase.execute(dsMerchantParameters, dsSignature);
    LOGGER.debug("POST /api/v1/payments/redsys/notification -> OK");
    return ResponseEntity.ok("OK");
  }
}
