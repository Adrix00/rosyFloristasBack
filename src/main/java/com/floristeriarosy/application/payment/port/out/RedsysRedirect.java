package com.floristeriarosy.application.payment.port.out;

import java.util.Map;

/**
 * @param redirectUrl the Redsys form-post target (test or production, per configuration)
 * @param formFields the signed {@code Ds_*} fields to post as a form
 */
public record RedsysRedirect(String redirectUrl, Map<String, String> formFields) {}
