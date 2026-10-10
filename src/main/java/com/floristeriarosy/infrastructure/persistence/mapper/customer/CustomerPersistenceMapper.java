package com.floristeriarosy.infrastructure.persistence.mapper.customer;

import com.floristeriarosy.domain.model.customer.Customer;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import com.floristeriarosy.infrastructure.persistence.entity.customer.CustomerEntity;
import org.springframework.stereotype.Component;

/** Domain ↔ JPA entity conversions (ADR-002: Persistence Mapper). */
@Component
public class CustomerPersistenceMapper {

  /**
   * @param customer the domain customer to persist
   * @return its JPA entity shape
   */
  public CustomerEntity toEntity(Customer customer) {
    return new CustomerEntity(
        customer.id().value(),
        customer.type(),
        customer.status(),
        customer.emailEncrypted(),
        customer.emailHash(),
        customer.phoneEncrypted(),
        customer.phoneHash(),
        customer.firstNameEncrypted(),
        customer.lastNameEncrypted(),
        null,
        null,
        null,
        customer.version(),
        customer.createdAt(),
        customer.updatedAt());
  }

  /**
   * @param entity the persisted JPA entity
   * @return the rebuilt domain customer ({@link Customer#reconstitute})
   */
  public Customer toDomain(CustomerEntity entity) {
    return Customer.reconstitute(
        CustomerId.of(entity.getId()),
        entity.getType(),
        entity.getStatus(),
        entity.getEmailEncrypted(),
        entity.getEmailHash(),
        entity.getFirstNameEncrypted(),
        entity.getLastNameEncrypted(),
        entity.getPhoneEncrypted(),
        entity.getPhoneHash(),
        entity.getVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
