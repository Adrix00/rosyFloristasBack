package com.floristeriarosy.infrastructure.persistence.adapter.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.customer.Customer;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class CustomerPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private CustomerPersistenceAdapter adapter;

  @Test
  void savingThenFindingByEmailHashRoundTrips() {
    byte[] emailHash = "unique-hash-1".getBytes(StandardCharsets.UTF_8);
    Customer customer =
        Customer.registerGuest(
            CustomerId.newId(), "enc".getBytes(StandardCharsets.UTF_8), emailHash,
            "enc".getBytes(StandardCharsets.UTF_8), "enc".getBytes(StandardCharsets.UTF_8),
            "enc".getBytes(StandardCharsets.UTF_8), "phone-hash".getBytes(StandardCharsets.UTF_8));

    adapter.save(customer);

    assertThat(adapter.findByEmailHash(emailHash)).isPresent();
  }
}
