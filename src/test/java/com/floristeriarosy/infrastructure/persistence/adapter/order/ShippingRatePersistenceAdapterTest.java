package com.floristeriarosy.infrastructure.persistence.adapter.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import com.floristeriarosy.domain.model.order.ShippingBand;
import com.floristeriarosy.domain.model.order.valueobject.ShippingBandId;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@code shipping_rates} already has seed data (order.md §2). The writes are exercised inside a
 * {@code TransactionTemplate}, the way the services call them: an adapter that updates an existing
 * row only shows its defects under an enclosing transaction (see {@code
 * TransactionalConstraintTranslationIntegrationTest}).
 */
@Testcontainers
@SpringBootTest
class ShippingRatePersistenceAdapterTest {

  private static final UUID NEAR = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID MIDDLE = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID FAR = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ShippingRatePersistenceAdapter adapter;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final ExecutorService other = Executors.newSingleThreadExecutor();

  @BeforeEach
  void restoreTheSeedBands() {
    jdbcTemplate.update("DELETE FROM shipping_rates WHERE id NOT IN (?, ?, ?)", NEAR, MIDDLE, FAR);
    restore(NEAR, 0, 1, "5.00", "50.00");
    restore(MIDDLE, 1, 5, "8.00", "70.00");
    restore(FAR, 5, 10, "10.00", "90.00");
  }

  @AfterEach
  void stopTheSecondTransactionThread() {
    other.shutdownNow();
  }

  @Test
  void findsTheBandForAShortDistance() {
    assertThat(adapter.findRateForDistance(new BigDecimal("0.5"))).isPresent();
  }

  @Test
  void findsNoBandBeyondTenKilometers() {
    assertThat(adapter.findRateForDistance(new BigDecimal("15.0"))).isEmpty();
  }

  @Test
  void findAllListsEveryBandByWhereItStarts() {
    assertThat(adapter.findAll()).extracting(b -> b.minKm().intValue()).containsExactly(0, 1, 5);
  }

  @Test
  void theRadiusIsTheEndOfTheFurthestActiveBand() {
    assertThat(adapter.findMaxDeliveryDistanceKm()).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("10"));

    jdbcTemplate.update("UPDATE shipping_rates SET active = false WHERE id = ?", FAR);
    assertThat(adapter.findMaxDeliveryDistanceKm()).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("5"));

    jdbcTemplate.update("UPDATE shipping_rates SET active = false");
    assertThat(adapter.findMaxDeliveryDistanceKm()).isEmpty();
  }

  @Test
  void updatingAnExistingBandInsideATransactionPersistsIt() {
    Timestamp before = updatedAt(MIDDLE);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              ShippingBand band = bandOf(adapter.findAllUnderTariffLock(), MIDDLE);
              band.replaceWith(new BigDecimal("1"), new BigDecimal("5"), new BigDecimal("9.50"), null, true);
              adapter.save(band);
            });

    assertThat(jdbcTemplate.queryForObject("SELECT price FROM shipping_rates WHERE id = ?", BigDecimal.class, MIDDLE))
        .isEqualByComparingTo("9.50");
    assertThat(jdbcTemplate.queryForObject("SELECT free_from_amount FROM shipping_rates WHERE id = ?", BigDecimal.class, MIDDLE))
        .isNull();
    assertThat(updatedAt(MIDDLE)).isAfter(before);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shipping_rates", Integer.class)).isEqualTo(3);
  }

  @Test
  void aNewBandInsideATransactionIsInserted() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status ->
                adapter.save(
                    ShippingBand.create(
                        ShippingBandId.newId(),
                        new BigDecimal("10"),
                        new BigDecimal("15"),
                        new BigDecimal("12.00"),
                        null,
                        true)));

    assertThat(adapter.findAll()).hasSize(4);
    assertThat(adapter.findMaxDeliveryDistanceKm()).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("15"));
  }

  @Test
  void overlappingActiveBandsAreRefusedByTheDatabaseBackstopAsTheSameDomainError() {
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status ->
                            adapter.save(
                                ShippingBand.create(
                                    ShippingBandId.newId(),
                                    new BigDecimal("3"),
                                    new BigDecimal("7"),
                                    new BigDecimal("6.00"),
                                    null,
                                    true))))
        .isInstanceOf(ShippingRatesInvalidException.class);

    assertThat(adapter.findAll()).hasSize(3);
  }

  @Test
  void aSecondTransactionTakingTheTariffLockWaitsForTheFirstToFinish() throws Exception {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    AtomicReference<Future<List<ShippingBand>>> second = new AtomicReference<>();

    List<ShippingBand> firstSaw =
        template.execute(
            status -> {
              List<ShippingBand> mine = adapter.findAllUnderTariffLock();
              // The second transaction starts now and must block on the rows this one holds.
              second.set(other.submit(() -> template.execute(s -> adapter.findAllUnderTariffLock())));
              awaitASessionWaitingOnALock();
              assertThat(second.get().isDone()).isFalse();
              return mine;
            });

    assertThat(firstSaw).hasSize(3);
    assertThat(second.get().get(10, TimeUnit.SECONDS)).hasSize(3);
  }

  private void awaitASessionWaitingOnALock() {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Integer waiting =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM pg_stat_activity"
                  + " WHERE datname = current_database() AND wait_event_type = 'Lock'",
              Integer.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      try {
        Thread.sleep(25);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    throw new AssertionError("the second transaction never blocked on the row locks");
  }

  private ShippingBand bandOf(List<ShippingBand> bands, UUID id) {
    return bands.stream().filter(b -> b.id().value().equals(id)).findFirst().orElseThrow();
  }

  private Timestamp updatedAt(UUID id) {
    return jdbcTemplate.queryForObject("SELECT updated_at FROM shipping_rates WHERE id = ?", Timestamp.class, id);
  }

  private void restore(UUID id, int minKm, int maxKm, String price, String freeFrom) {
    jdbcTemplate.update(
        "UPDATE shipping_rates SET min_km = ?, max_km = ?, price = ?, free_from_amount = ?, active = true WHERE id = ?",
        minKm,
        maxKm,
        new BigDecimal(price),
        new BigDecimal(freeFrom),
        id);
  }
}
