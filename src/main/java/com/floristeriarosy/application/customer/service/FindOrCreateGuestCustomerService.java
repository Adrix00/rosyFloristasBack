package com.floristeriarosy.application.customer.service;

import com.floristeriarosy.application.customer.command.FindOrCreateGuestCustomerCommand;
import com.floristeriarosy.application.customer.port.in.FindOrCreateGuestCustomerUseCase;
import com.floristeriarosy.application.customer.port.out.CustomerReadPort;
import com.floristeriarosy.application.customer.port.out.CustomerWritePort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.customer.Customer;
import com.floristeriarosy.domain.model.customer.CustomerType;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link FindOrCreateGuestCustomerUseCase} (customer.md, rule 3.2): a {@code WEB}
 * guest checkout always anchors by email — create if new, refresh if a {@code GUEST} already
 * exists, leave unlinked if the email belongs to a {@code REGISTERED} account.
 */
@Service
@Transactional
public class FindOrCreateGuestCustomerService implements FindOrCreateGuestCustomerUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(FindOrCreateGuestCustomerService.class);

  private final CustomerReadPort readPort;
  private final CustomerWritePort writePort;
  private final PiiCryptoPort piiCryptoPort;

  /**
   * @param readPort locates an existing customer by email
   * @param writePort persists a new or refreshed guest
   * @param piiCryptoPort encrypts and hashes the buyer's details (ADR-005)
   */
  public FindOrCreateGuestCustomerService(
      CustomerReadPort readPort, CustomerWritePort writePort, PiiCryptoPort piiCryptoPort) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.piiCryptoPort = piiCryptoPort;
  }

  /**
   * @param command the checkout's buyer details
   * @return the linked customer's id, or {@code null} for a {@code REGISTERED} email match
   */
  @Override
  public CustomerId execute(FindOrCreateGuestCustomerCommand command) {
    LOGGER.debug("findOrCreateGuestCustomer");
    String normalizedEmail = command.email().trim().toLowerCase(Locale.ROOT);
    byte[] emailHash = piiCryptoPort.hmac(normalizedEmail);
    var existing = readPort.findByEmailHash(emailHash);
    if (existing.isPresent() && existing.get().type() == CustomerType.REGISTERED) {
      LOGGER.debug("findOrCreateGuestCustomer -> email belongs to a REGISTERED account, leaving unlinked");
      return null;
    }
    byte[] firstNameEncrypted = piiCryptoPort.encrypt(command.firstName());
    byte[] lastNameEncrypted = piiCryptoPort.encrypt(command.lastName());
    byte[] phoneEncrypted = piiCryptoPort.encrypt(command.phone());
    byte[] phoneHash = piiCryptoPort.hmac(command.phone());
    Customer customer;
    if (existing.isPresent()) {
      customer = existing.get();
      customer.updateGuestContact(firstNameEncrypted, lastNameEncrypted, phoneEncrypted, phoneHash);
    } else {
      byte[] emailEncrypted = piiCryptoPort.encrypt(normalizedEmail);
      customer =
          Customer.registerGuest(
              CustomerId.newId(),
              emailEncrypted,
              emailHash,
              firstNameEncrypted,
              lastNameEncrypted,
              phoneEncrypted,
              phoneHash);
    }
    CustomerId result = writePort.save(customer).id();
    LOGGER.debug("findOrCreateGuestCustomer -> id={}", result);
    return result;
  }
}
