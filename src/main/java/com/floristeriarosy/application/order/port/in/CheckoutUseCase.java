package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.command.CheckoutCommand;
import com.floristeriarosy.application.order.dto.CheckoutRedirectDto;

/** Fase 1 of the checkout (order.md, §3.2): reserve, then redirect. */
public interface CheckoutUseCase {

  /**
   * @param command everything needed to reserve and build the redirect
   * @return the signed Redsys redirect
   */
  CheckoutRedirectDto execute(CheckoutCommand command);
}
