package com.floristeriarosy.infrastructure.web.response.order;

import java.util.Map;

/**
 * @param redirectUrl where the frontend must POST the customer's browser
 * @param formFields the signed fields to post as a form
 */
public record CheckoutResponse(String redirectUrl, Map<String, String> formFields) {

  /** Defensively copies {@code formFields} (SpotBugs EI_EXPOSE_REP2). */
  public CheckoutResponse {
    formFields = Map.copyOf(formFields);
  }

  @Override
  public Map<String, String> formFields() {
    return formFields;
  }
}
