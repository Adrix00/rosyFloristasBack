package com.floristeriarosy.infrastructure.persistence.adapter.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.floristeriarosy.domain.exception.order.IdempotencyOperationInProgressException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
  @Autowired private PlatformTransactionManager transactionManager;

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

  @Test
  void findExpiredPendingKeysReturnsOnlyPendingExpiredRowsOldestFirst() {
    String older = insertPendingExpiring(Instant.now().minusSeconds(120));
    String newer = insertPendingExpiring(Instant.now().minusSeconds(60));
    String notYetExpired = insertPendingExpiring(Instant.now().plusSeconds(600));
    String completed = insertPendingExpiring(Instant.now().minusSeconds(90));
    adapter.markCompleted(completed, 202);

    var keys = adapter.findExpiredPendingKeys(Instant.now(), 1000);

    assertThat(keys).contains(older, newer).doesNotContain(notYetExpired, completed);
    assertThat(keys.indexOf(older)).isLessThan(keys.indexOf(newer));
  }

  @Test
  void findExpiredPendingKeysHonoursTheLimit() {
    insertPendingExpiring(Instant.now().minusSeconds(30));
    insertPendingExpiring(Instant.now().minusSeconds(30));

    assertThat(adapter.findExpiredPendingKeys(Instant.now(), 1)).hasSize(1);
  }

  @Test
  void lockExpiredPendingClaimsAnExpiredPendingRowAndRefusesTheOthers() {
    String expired = insertPendingExpiring(Instant.now().minusSeconds(30));
    String fresh = insertPendingExpiring(Instant.now().plusSeconds(600));
    String completed = insertPendingExpiring(Instant.now().minusSeconds(30));
    adapter.markCompleted(completed, 202);

    assertThat(adapter.lockExpiredPending(expired, Instant.now())).isPresent();
    assertThat(adapter.lockExpiredPending(fresh, Instant.now())).isEmpty();
    assertThat(adapter.lockExpiredPending(completed, Instant.now())).isEmpty();
  }

  @Test
  void lockExpiredPendingSkipsARowAnotherTransactionHoldsLocked() throws Exception {
    String key = insertPendingExpiring(Instant.now().minusSeconds(30));
    ExecutorService other = Executors.newSingleThreadExecutor();

    try {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                adapter.findForUpdate(key);
                Optional<?> claimedMeanwhile =
                    callOn(other, () -> adapter.lockExpiredPending(key, Instant.now()));
                assertThat(claimedMeanwhile).isEmpty();
              });
    } finally {
      other.shutdownNow();
    }

    assertThat(adapter.lockExpiredPending(key, Instant.now())).isPresent();
  }

  private String insertPendingExpiring(Instant expiresAt) {
    String key = UUID.randomUUID().toString();
    adapter.insertPending(key, FINGERPRINT, UUID.randomUUID(), PAYLOAD, expiresAt);
    return key;
  }

  private <T> T callOn(ExecutorService executor, Callable<T> task) {
    try {
      return executor.submit(task).get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
