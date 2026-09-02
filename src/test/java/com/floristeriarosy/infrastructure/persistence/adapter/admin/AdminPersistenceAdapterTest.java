package com.floristeriarosy.infrastructure.persistence.adapter.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.application.admin.command.ChangeAdminStatusCommand;
import com.floristeriarosy.application.admin.service.ChangeAdminStatusService;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.ResourceModifiedException;
import com.floristeriarosy.domain.exception.admin.AdminEmailAlreadyExistsException;
import com.floristeriarosy.domain.exception.admin.LastOwnerCannotBeRemovedException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the migrations against real PostgreSQL, then exercises the admin adapter (admin.md, ADR-009). */
@Testcontainers
@SpringBootTest
class AdminPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private AdminPersistenceAdapter adapter;
  @Autowired private ChangeAdminStatusService changeAdminStatusService;
  @Autowired private PiiCryptoPort piiCryptoPort;

  /**
   * Unlike {@link #newAdmin(AdminRole)}, this builds a genuinely decryptable admin — needed only
   * for the two owners in {@link #rejectsConcurrentDeactivationOfTwoDifferentActiveOwners()},
   * which goes through {@link ChangeAdminStatusService} and its response mapper actually decrypts
   * the stored email.
   *
   * @param role the admin's role
   * @return a saved admin with a real, decryptable {@code emailEncrypted}
   */
  private Admin newDecryptableAdmin(AdminRole role) {
    String email = "owner-" + UUID.randomUUID() + "@rosy.test";
    return Admin.create(AdminId.newId(), piiCryptoPort.encrypt(email), piiCryptoPort.hmac(email), "argon2-hash", role);
  }

  private Admin newAdmin(AdminRole role) {
    String suffix = UUID.randomUUID().toString();
    return Admin.create(
        AdminId.newId(),
        ("encrypted-" + suffix).getBytes(StandardCharsets.UTF_8),
        ("hash-" + suffix).getBytes(StandardCharsets.UTF_8),
        "argon2-hash",
        role);
  }

  @Test
  void savesAndFindsByIdAndEmailHash() {
    Admin admin = newAdmin(AdminRole.ADMIN);

    Admin saved = adapter.save(admin);

    assertThat(adapter.findById(saved.id())).isPresent();
    assertThat(adapter.findByEmailHash(saved.emailHash())).isPresent();
    assertThat(saved.createdAt()).isNotNull();
  }

  @Test
  void rejectsADuplicateEmailHash() {
    Admin first = newAdmin(AdminRole.ADMIN);
    Admin saved = adapter.save(first);
    Admin duplicate =
        Admin.create(
            AdminId.newId(), "other-encrypted".getBytes(StandardCharsets.UTF_8), saved.emailHash(), "hash", AdminRole.OWNER);

    assertThatThrownBy(() -> adapter.save(duplicate)).isInstanceOf(AdminEmailAlreadyExistsException.class);
  }

  @Test
  void countsOnlyActiveOwners() {
    Admin activeOwnerOne = adapter.save(newAdmin(AdminRole.OWNER));
    Admin activeOwnerTwo = adapter.save(newAdmin(AdminRole.OWNER));
    adapter.save(newAdmin(AdminRole.ADMIN));
    long countWithBothActive = adapter.countActiveOwners();

    activeOwnerTwo.deactivate();
    adapter.save(activeOwnerTwo);
    long countAfterDeactivatingOne = adapter.countActiveOwners();

    assertThat(countWithBothActive).isGreaterThanOrEqualTo(2L);
    assertThat(countAfterDeactivatingOne).isEqualTo(countWithBothActive - 1);
    assertThat(adapter.findById(activeOwnerOne.id())).isPresent();
  }

  @Test
  void findAllFiltersByActiveAndRole() {
    Admin owner = adapter.save(newAdmin(AdminRole.OWNER));
    Admin admin = adapter.save(newAdmin(AdminRole.ADMIN));

    List<Admin> owners = adapter.findAll(null, AdminRole.OWNER);
    List<Admin> activeOnly = adapter.findAll(true, null);

    assertThat(owners).extracting(a -> a.id()).contains(owner.id());
    assertThat(owners).extracting(a -> a.id()).doesNotContain(admin.id());
    assertThat(activeOnly).extracting(a -> a.id()).contains(owner.id(), admin.id());
  }

  /**
   * Regression test for the TOCTOU gap a security review found in rule 3.7: two active {@code
   * OWNER}s, each deactivated by a concurrent request, must not both succeed and leave zero active
   * {@code OWNER}s. {@code countActiveOwnersForUpdate()}'s row lock (not {@code
   * countActiveOwners()}, which two racing plain reads could both see as {@code 2}) forces one
   * request to wait for the other's transaction to commit before it re-counts.
   */
  @Test
  void rejectsConcurrentDeactivationOfTwoDifferentActiveOwners() throws Exception {
    // Other test methods in this class share the same Testcontainers database and may leave
    // active OWNER rows behind; without clearing them first, this test's premise (exactly two
    // active OWNERs) is not guaranteed, and the race becomes flaky.
    deactivateAllActiveOwners();
    Admin ownerOne = adapter.save(newDecryptableAdmin(AdminRole.OWNER));
    Admin ownerTwo = adapter.save(newDecryptableAdmin(AdminRole.OWNER));
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Boolean>> deactivations =
          List.of(
              () -> attemptDeactivate(ownerOne.id()),
              () -> attemptDeactivate(ownerTwo.id()));
      List<Future<Boolean>> results = executor.invokeAll(deactivations);

      long successCount = 0;
      long rejectedCount = 0;
      for (Future<Boolean> result : results) {
        if (result.get()) {
          successCount++;
        } else {
          rejectedCount++;
        }
      }
      assertThat(successCount).isEqualTo(1);
      assertThat(rejectedCount).isEqualTo(1);
      assertThat(adapter.countActiveOwners()).isEqualTo(1L);
    } finally {
      executor.shutdown();
    }
  }

  /** Directly deactivates every active {@code OWNER}, bypassing rule 3.7 (adapter, not service). */
  private void deactivateAllActiveOwners() {
    for (Admin owner : adapter.findAll(true, AdminRole.OWNER)) {
      owner.deactivate();
      adapter.save(owner);
    }
  }

  /**
   * @param id the owner to deactivate
   * @return {@code true} if the deactivation succeeded, {@code false} if it was correctly rejected
   */
  private boolean attemptDeactivate(AdminId id) {
    try {
      changeAdminStatusService.execute(new ChangeAdminStatusCommand(id.value(), id.value(), false));
      return true;
    } catch (LastOwnerCannotBeRemovedException rejected) {
      return false;
    }
  }

  /**
   * Mirrors {@code ProductPersistenceAdapterTest#rejectsAConcurrentEditOfTheSameProduct}: {@code
   * save} always re-reads the currently managed entity, so a version conflict only surfaces when
   * two writes genuinely race.
   */
  @Test
  void rejectsAConcurrentEditOfTheSameAdmin() throws Exception {
    Admin saved = adapter.save(newAdmin(AdminRole.ADMIN));
    AdminId id = saved.id();
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Boolean>> edits =
          List.of(
              () -> attemptConcurrentEdit(id, barrier, "editado-primero-" + UUID.randomUUID()),
              () -> attemptConcurrentEdit(id, barrier, "editado-segundo-" + UUID.randomUUID()));
      List<Future<Boolean>> results = executor.invokeAll(edits);

      long successCount = 0;
      long conflictCount = 0;
      for (Future<Boolean> result : results) {
        if (result.get()) {
          successCount++;
        } else {
          conflictCount++;
        }
      }
      assertThat(successCount).isEqualTo(1);
      assertThat(conflictCount).isEqualTo(1);
    } finally {
      executor.shutdown();
    }
  }

  /**
   * Each writer sets a distinct, freshly-generated {@code emailEncrypted}: unlike toggling
   * between the two {@link AdminRole} values, this guarantees the entity is genuinely dirty for
   * whichever writer applies second, so Hibernate always performs (and can lose) the version
   * check instead of silently skipping a no-op update.
   */
  private boolean attemptConcurrentEdit(AdminId id, CyclicBarrier barrier, String newEmailEncrypted) throws Exception {
    Admin loaded = adapter.findById(id).orElseThrow();
    loaded.replace(newEmailEncrypted.getBytes(StandardCharsets.UTF_8), loaded.emailHash(), loaded.role());
    barrier.await();
    try {
      adapter.save(loaded);
      return true;
    } catch (ResourceModifiedException conflict) {
      return false;
    }
  }
}
