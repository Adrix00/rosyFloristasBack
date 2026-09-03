package com.floristeriarosy.infrastructure.persistence.adapter.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import com.floristeriarosy.domain.model.auth.RefreshToken;
import com.floristeriarosy.domain.model.auth.SubjectType;
import com.floristeriarosy.domain.model.auth.valueobject.RefreshTokenId;
import com.floristeriarosy.infrastructure.persistence.adapter.admin.AdminPersistenceAdapter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the migrations against real PostgreSQL, then exercises the adapter (auth.md, ADR-008). */
@Testcontainers
@SpringBootTest
class RefreshTokenPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private RefreshTokenPersistenceAdapter adapter;
  @Autowired private AdminPersistenceAdapter adminAdapter;

  /**
   * @return a family for a real, persisted admin — {@code refresh_tokens.admin_user_id} has a
   *     foreign key, so the row must exist first
   */
  private RefreshToken newAdminFamily() {
    String suffix = UUID.randomUUID().toString();
    Admin admin =
        adminAdapter.save(
            Admin.create(
                AdminId.newId(),
                ("encrypted-" + suffix).getBytes(StandardCharsets.UTF_8),
                ("hash-" + suffix).getBytes(StandardCharsets.UTF_8),
                "argon2-hash",
                AdminRole.ADMIN));
    return RefreshToken.startFamily(
        RefreshTokenId.newId(),
        RefreshToken.hash(UUID.randomUUID().toString()),
        admin.id().value(),
        SubjectType.ADMIN,
        Instant.now().plusSeconds(3600));
  }

  @Test
  void savesAndFindsByHash() {
    RefreshToken token = newAdminFamily();

    RefreshToken saved = adapter.save(token);

    Optional<RefreshToken> found = adapter.findByHash(token.tokenHash());
    assertThat(found).isPresent();
    assertThat(found.orElseThrow().id()).isEqualTo(saved.id());
    assertThat(found.orElseThrow().subjectId()).isEqualTo(token.subjectId());
    assertThat(found.orElseThrow().subjectType()).isEqualTo(SubjectType.ADMIN);
    assertThat(found.orElseThrow().familyId()).isEqualTo(token.familyId());
    assertThat(found.orElseThrow().createdAt()).isNotNull();
  }

  @Test
  void findByHashReturnsEmptyForAnUnknownHash() {
    Optional<RefreshToken> found = adapter.findByHash(RefreshToken.hash("never-saved"));

    assertThat(found).isEmpty();
  }

  @Test
  void revokeMarksTheRowRevoked() {
    RefreshToken token = adapter.save(newAdminFamily());

    adapter.revoke(token.id(), Instant.now());

    RefreshToken reloaded = adapter.findByHash(token.tokenHash()).orElseThrow();
    assertThat(reloaded.isRevoked()).isTrue();
  }

  @Test
  void rotationLeavesBothRowsOfTheFamilyIndependentlyFindable() {
    RefreshToken original = adapter.save(newAdminFamily());
    RefreshToken rotated =
        adapter.save(original.rotate(RefreshTokenId.newId(), RefreshToken.hash("rotated-token")));

    assertThat(adapter.findByHash(original.tokenHash())).isPresent();
    assertThat(adapter.findByHash(rotated.tokenHash())).isPresent();
    assertThat(rotated.familyId()).isEqualTo(original.familyId());
    assertThat(rotated.expiresAt()).isEqualTo(original.expiresAt());
  }
}
