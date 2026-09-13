package com.floristeriarosy.application.payment.port.out;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.util.Optional;

/** Read access to {@code payments} (payment.md, section 8). */
public interface PaymentReadPort {

  /**
   * @param id the payment to load
   * @return the payment, if it exists
   */
  Optional<Payment> findById(PaymentId id);

  /**
   * @param orderId the order whose payment to load
   * @return the order's payment, if one was captured
   */
  Optional<Payment> findByOrderId(OrderId orderId);
}
