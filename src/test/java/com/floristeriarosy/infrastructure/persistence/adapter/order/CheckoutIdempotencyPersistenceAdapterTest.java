package com.floristeriarosy.infrastructure.persistence.adapter.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.floristeriarosy.domain.exception.order.IdempotencyOperationInProgressException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
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
class CheckoutIdempotencyPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private CheckoutIdempotencyPersistenceAdapter adapter;

  private static final byte[] FINGERPRINT = "fp".getBytes(StandardCharsets.UTF_8);
  private static final byte[] PAYLOAD = "encrypted-payload".getBytes(StandardCharsets.UTF_8);

  @Test
  void insertPendingThenFindReturnsTheLock() {
    String key = UUID.randomUUID().toString();
    UUID resourceId = UUID.randomUUID();

    adapter.insertPending(key, FINGERPRINT, resourceId, PAYLOAD, Instant.now().plusSeconds(600));

    var lock = adapter.find(key).orElseThrow();
    assertThat(lock.status()).isEqualTo("PENDING");
    assertThat(lock.resourceId()).isEqualTo(resourceId);
  }

  @Test
  void insertPendingTwiceWithTheSameKeyThrows() {
    String key = UUID.randomUUID().toString();
    adapter.insertPending(key, FINGERPRINT, UUID.randomUUID(), PAYLOAD, Instant.now().plusSeconds(600));

    assertThatExceptionOfType(IdempotencyOperationInProgressException.class)
        .isThrownBy(() -> adapter.insertPending(key, FINGERPRINT, UUID.randomUUID(), PAYLOAD, Instant.now()));
  }

  @Test
  void markCompletedUpdatesTheStatus() {
    String key = UUID.randomUUID().toString();
    adapter.insertPending(key, FINGERPRINT, UUID.randomUUID(), PAYLOAD, Instant.now().plusSeconds(600));

    adapter.markCompleted(key, 202);

    assertThat(adapter.find(key).orElseThrow().status()).isEqualTo("COMPLETED");
  }
}
