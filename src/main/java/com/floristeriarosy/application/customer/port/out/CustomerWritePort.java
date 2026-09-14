package com.floristeriarosy.application.customer.port.out;

import com.floristeriarosy.domain.model.customer.Customer;

/** Write access to {@code customers}, minimal slice (customer.md, section 8). */
public interface CustomerWritePort {

  /**
   * @param customer the customer to insert or update
   * @return the saved customer, with timestamps populated by the database
   */
  Customer save(Customer customer);
}
