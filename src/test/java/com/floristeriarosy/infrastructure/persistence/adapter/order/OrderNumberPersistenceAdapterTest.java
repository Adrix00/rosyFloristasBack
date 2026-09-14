package com.floristeriarosy.infrastructure.persistence.adapter.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.order.OrderChannel;
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
class OrderNumberPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private OrderNumberPersistenceAdapter adapter;

  @Test
  void nextProducesAFormattedIncreasingNumber() {
    var first = adapter.next(OrderChannel.WEB);
    var second = adapter.next(OrderChannel.WEB);

    assertThat(first.value()).matches("WEB-\\d{8}-\\d{6}");
    assertThat(second.value()).isNotEqualTo(first.value());
  }
}
