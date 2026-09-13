package com.floristeriarosy.application.payment.dto;

import java.util.Map;

/**
 * @param redirectUrl the Redsys form-post target (test or production, per configuration)
 * @param formFields the signed {@code Ds_*} fields to post as a form
 */
public record RedsysRedirect(String redirectUrl, Map<String, String> formFields) {

  /** Defensively copies {@code formFields} (SpotBugs EI_EXPOSE_REP2). */
  public RedsysRedirect {
    formFields = Map.copyOf(formFields);
  }

  @Override
  public Map<String, String> formFields() {
    return formFields;
  }
}
