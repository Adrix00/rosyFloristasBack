package com.floristeriarosy.application.customer.port.out;

import com.floristeriarosy.domain.model.customer.Customer;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import java.util.Optional;

/** Read access to {@code customers}, minimal slice (customer.md, section 8). */
public interface CustomerReadPort {

  /**
   * @param id the customer to load
   * @return the customer, if it exists
   */
  Optional<Customer> findById(CustomerId id);

  /**
   * @param emailHash the HMAC of a normalized email
   * @return the customer using that email, if any (any type, any status)
   */
  Optional<Customer> findByEmailHash(byte[] emailHash);
}
