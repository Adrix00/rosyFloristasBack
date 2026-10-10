package com.floristeriarosy.infrastructure.persistence.jpa.customer.repository;

import com.floristeriarosy.infrastructure.persistence.entity.customer.CustomerEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA queries for {@code customers} (customer.md, section 8). */
public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {

  /**
   * @param emailHash the HMAC of a normalized email
   * @return the customer using that email, if any
   */
  Optional<CustomerEntity> findByEmailHash(byte[] emailHash);
}
