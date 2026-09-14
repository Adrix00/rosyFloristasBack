package com.floristeriarosy.application.order.dto;

import java.util.Map;

/**
 * @param redirectUrl the Redsys form-post target
 * @param formFields the signed {@code Ds_*} fields to post
 */
public record CheckoutRedirectDto(String redirectUrl, Map<String, String> formFields) {

  /** Defensively copies {@code formFields} (SpotBugs EI_EXPOSE_REP2). */
  public CheckoutRedirectDto {
    formFields = Map.copyOf(formFields);
  }
}
