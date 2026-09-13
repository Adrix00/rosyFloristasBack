package com.floristeriarosy.infrastructure.payment.redsys;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redsys TPV Virtual configuration (ADR-017), bound from {@code app.payment.redsys.*}.
 *
 * @param merchantCode the FUC assigned by the bank
 * @param terminal the terminal number within that FUC
 * @param secretKey the merchant's Base64-encoded signing key
 * @param redirectUrl where the browser is sent to pay
 * @param restUrl the REST endpoint used for server-to-server operations (refunds)
 * @param notificationUrl the URL Redsys calls back with the result (ADR-017)
 * @param urlOk where the browser returns to on success
 * @param urlKo where the browser returns to on failure
 */
@ConfigurationProperties(prefix = "app.payment.redsys")
public record RedsysProperties(
    String merchantCode,
    String terminal,
    String secretKey,
    String redirectUrl,
    String restUrl,
    String notificationUrl,
    String urlOk,
    String urlKo) {}
