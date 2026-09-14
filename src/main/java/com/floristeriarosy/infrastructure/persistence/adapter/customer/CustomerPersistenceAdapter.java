package com.floristeriarosy.infrastructure.persistence.adapter.customer;

import com.floristeriarosy.application.customer.port.out.CustomerReadPort;
import com.floristeriarosy.application.customer.port.out.CustomerWritePort;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.model.customer.Customer;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import com.floristeriarosy.infrastructure.persistence.entity.customer.CustomerEntity;
import com.floristeriarosy.infrastructure.persistence.jpa.customer.repository.CustomerJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.customer.CustomerPersistenceMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/**
 * Implements the customer output ports (ADR-003) with plain JPA (ADR-002, customer.md section 8):
 * no pagination, no JDBC needed for the minimal slice.
 */
@Repository
public class CustomerPersistenceAdapter implements CustomerReadPort, CustomerWritePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(CustomerPersistenceAdapter.class);

  private final CustomerJpaRepository jpaRepository;
  private final CustomerPersistenceMapper mapper;

  /**
   * @param jpaRepository writes and simple lookups
   * @param mapper converts between the domain {@link Customer} and the JPA {@link CustomerEntity}
   */
  public CustomerPersistenceAdapter(CustomerJpaRepository jpaRepository, CustomerPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.mapper = mapper;
  }

  /**
   * @param id the customer to load
   * @return the customer, if it exists
   */
  @Override
  public Optional<Customer> findById(CustomerId id) {
    LOGGER.debug("findById id={}", id);
    Optional<Customer> result = jpaRepository.findById(id.value()).map(mapper::toDomain);
    LOGGER.debug("findById id={} -> found={}", id, result.isPresent());
    return result;
  }

  /**
   * @param emailHash the HMAC of a normalized email
   * @return the customer using that email, if any
   */
  @Override
  public Optional<Customer> findByEmailHash(byte[] emailHash) {
    LOGGER.debug("findByEmailHash");
    Optional<Customer> result = jpaRepository.findByEmailHash(emailHash).map(mapper::toDomain);
    LOGGER.debug("findByEmailHash -> found={}", result.isPresent());
    return result;
  }

  /**
   * Updates the managed entity in place when {@code customer.id()} already exists, so the {@code
   * @Version} Hibernate loaded is what gets checked — a freshly built detached entity would always
   * carry {@code version = 0} and be mistaken for a new row (ADR-009). Builds a fresh entity only
   * for a genuinely new customer.
   *
   * @param customer the customer to insert or update
   * @return the saved customer, with timestamps populated by the database
   * @throws ResourceModifiedException the customer was changed concurrently
   */
  @Override
  public Customer save(Customer customer) {
    LOGGER.debug("save id={}", customer.id());
    CustomerEntity entity =
        jpaRepository
            .findById(customer.id().value())
            .map(existing -> applyChanges(existing, customer))
            .orElseGet(() -> mapper.toEntity(customer));
    try {
      Customer result = mapper.toDomain(jpaRepository.saveAndFlush(entity));
      LOGGER.debug("save id={} -> saved", result.id());
      return result;
    } catch (ObjectOptimisticLockingFailureException conflict) {
      throw new ResourceModifiedException("Customer " + customer.id() + " was modified concurrently");
    }
  }

  /**
   * Copies the mutable guest-contact fields onto a managed entity.
   *
   * @param existing the entity Hibernate already loaded
   * @param customer the domain customer carrying the new values
   * @return the same managed entity, now mutated
   */
  private CustomerEntity applyChanges(CustomerEntity existing, Customer customer) {
    existing.setStatus(customer.status());
    existing.setEmailEncrypted(customer.emailEncrypted());
    existing.setEmailHash(customer.emailHash());
    existing.setFirstNameEncrypted(customer.firstNameEncrypted());
    existing.setLastNameEncrypted(customer.lastNameEncrypted());
    existing.setPhoneEncrypted(customer.phoneEncrypted());
    existing.setPhoneHash(customer.phoneHash());
    return existing;
  }
}
