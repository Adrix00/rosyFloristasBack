package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.dto.CheckoutStatusDto;
import com.floristeriarosy.application.order.query.GetCheckoutStatusQuery;

/** Lets the frontend resolve a checkout attempt after returning from Redsys (ADR-017). */
public interface GetCheckoutStatusUseCase {

  /**
   * @param query the idempotency key to look up
   * @return the current status
   */
  CheckoutStatusDto execute(GetCheckoutStatusQuery query);
}
