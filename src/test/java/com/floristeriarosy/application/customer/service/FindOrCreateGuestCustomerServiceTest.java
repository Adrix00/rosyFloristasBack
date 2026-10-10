package com.floristeriarosy.application.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.customer.command.FindOrCreateGuestCustomerCommand;
import com.floristeriarosy.application.customer.port.out.CustomerReadPort;
import com.floristeriarosy.application.customer.port.out.CustomerWritePort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.customer.Customer;
import com.floristeriarosy.domain.model.customer.CustomerStatus;
import com.floristeriarosy.domain.model.customer.CustomerType;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@link FindOrCreateGuestCustomerService} — the three branches of customer.md, rule 3.2. */
class FindOrCreateGuestCustomerServiceTest {

  private final CustomerReadPort readPort = mock(CustomerReadPort.class);
  private final CustomerWritePort writePort = mock(CustomerWritePort.class);
  private final PiiCryptoPort piiCryptoPort = mock(PiiCryptoPort.class);
  private final FindOrCreateGuestCustomerService service = new FindOrCreateGuestCustomerService(readPort, writePort, piiCryptoPort);

  private FindOrCreateGuestCustomerCommand command() {
    return new FindOrCreateGuestCustomerCommand("ana@example.com", "Ana", "García", "600111222");
  }

  @Test
  void createsANewGuestWhenNoRowExistsForTheEmail() {
    when(piiCryptoPort.hmac(any())).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(piiCryptoPort.encrypt(any())).thenReturn("enc".getBytes(StandardCharsets.UTF_8));
    when(readPort.findByEmailHash(any())).thenReturn(Optional.empty());
    when(writePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CustomerId result = service.execute(command());

    assertThat(result).isNotNull();
    verify(writePort).save(any());
  }

  @Test
  void reusesAnExistingGuestAndRefreshesContact() {
    when(piiCryptoPort.hmac(any())).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(piiCryptoPort.encrypt(any())).thenReturn("enc".getBytes(StandardCharsets.UTF_8));
    Customer existingGuest =
        Customer.registerGuest(
            CustomerId.newId(), "old".getBytes(StandardCharsets.UTF_8), "hash".getBytes(StandardCharsets.UTF_8),
            "old".getBytes(StandardCharsets.UTF_8), "old".getBytes(StandardCharsets.UTF_8),
            "old".getBytes(StandardCharsets.UTF_8), "hash".getBytes(StandardCharsets.UTF_8));
    when(readPort.findByEmailHash(any())).thenReturn(Optional.of(existingGuest));
    when(writePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CustomerId result = service.execute(command());

    assertThat(result).isEqualTo(existingGuest.id());
  }

  @Test
  void doesNotLinkToAnExistingRegisteredCustomer() {
    when(piiCryptoPort.hmac(any())).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    Customer registered =
        Customer.reconstitute(
            CustomerId.newId(), CustomerType.REGISTERED, CustomerStatus.ACTIVE,
            "e".getBytes(StandardCharsets.UTF_8), "h".getBytes(StandardCharsets.UTF_8), "f".getBytes(StandardCharsets.UTF_8),
            "l".getBytes(StandardCharsets.UTF_8), "p".getBytes(StandardCharsets.UTF_8), "ph".getBytes(StandardCharsets.UTF_8),
            0L, null, null);
    when(readPort.findByEmailHash(any())).thenReturn(Optional.of(registered));

    CustomerId result = service.execute(command());

    assertThat(result).isNull();
    verify(writePort, never()).save(any());
  }
}
