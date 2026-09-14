package com.floristeriarosy.infrastructure.persistence.adapter.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.order.dto.ShippingRate;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** {@code shipping_rates} already has seed data (order.md §2) — no fixture needed. */
@Testcontainers
@SpringBootTest
class ShippingRatePersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ShippingRatePersistenceAdapter adapter;

  @Test
  void findsTheBandForAShortDistance() {
    var rate = adapter.findRateForDistance(new BigDecimal("0.5"));

    assertThat(rate).isPresent();
  }

  @Test
  void findsNoBandBeyondTenKilometers() {
    var rate = adapter.findRateForDistance(new BigDecimal("15.0"));

    assertThat(rate).isEmpty();
  }
}
