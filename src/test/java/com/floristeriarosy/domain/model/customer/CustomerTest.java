package com.floristeriarosy.domain.model.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Domain rules of {@link Customer}, minimal slice (customer.md, rule 3.2 only). */
class CustomerTest {

  private static final byte[] ENCRYPTED = "encrypted".getBytes(StandardCharsets.UTF_8);
  private static final byte[] HASH = "hash".getBytes(StandardCharsets.UTF_8);

  @Test
  void registerGuestIsBornActiveGuestWithNoPassword() {
    Customer customer = Customer.registerGuest(CustomerId.newId(), ENCRYPTED, HASH, ENCRYPTED, ENCRYPTED, ENCRYPTED, HASH);

    assertThat(customer.type()).isEqualTo(CustomerType.GUEST);
    assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
  }

  @Test
  void updateGuestContactReplacesNameAndPhone() {
    Customer customer = Customer.registerGuest(CustomerId.newId(), ENCRYPTED, HASH, ENCRYPTED, ENCRYPTED, ENCRYPTED, HASH);
    byte[] newName = "new-name".getBytes(StandardCharsets.UTF_8);
    byte[] newPhone = "new-phone".getBytes(StandardCharsets.UTF_8);
    byte[] newPhoneHash = "new-phone-hash".getBytes(StandardCharsets.UTF_8);

    customer.updateGuestContact(newName, newName, newPhone, newPhoneHash);

    assertThat(customer.firstNameEncrypted()).isEqualTo(newName);
    assertThat(customer.phoneEncrypted()).isEqualTo(newPhone);
  }
}
