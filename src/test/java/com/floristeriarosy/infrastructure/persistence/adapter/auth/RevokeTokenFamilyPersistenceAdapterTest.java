package com.floristeriarosy.infrastructure.persistence.adapter.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import com.floristeriarosy.infrastructure.persistence.adapter.admin.AdminPersistenceAdapter;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the migrations against real PostgreSQL, then exercises {@link
 * RevokeTokenFamilyPersistenceAdapter} (ADR-008): revoking a subject's sessions must not touch
 * another subject's live refresh tokens.
 */
@Testcontainers
@SpringBootTest
class RevokeTokenFamilyPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private RevokeTokenFamilyPersistenceAdapter adapter;
  @Autowired private AdminPersistenceAdapter adminAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Admin newAdmin() {
    String suffix = UUID.randomUUID().toString();
    return adminAdapter.save(
        Admin.create(
            AdminId.newId(),
            ("encrypted-" + suffix).getBytes(StandardCharsets.UTF_8),
            ("hash-" + suffix).getBytes(StandardCharsets.UTF_8),
            "argon2-hash",
            AdminRole.ADMIN));
  }

  private void insertLiveRefreshToken(UUID id, UUID adminUserId) {
    jdbcTemplate.update(
        "INSERT INTO refresh_tokens (id, token_hash, admin_user_id, family_id, expires_at, created_at) "
            + "VALUES (?, ?, ?, ?, ?, now())",
        id,
        ("token-hash-" + id).getBytes(StandardCharsets.UTF_8),
        adminUserId,
        UUID.randomUUID(),
        Timestamp.from(Instant.now().plusSeconds(3600)));
  }

  private Timestamp revokedAtOf(UUID tokenId) {
    return jdbcTemplate.queryForObject(
        "SELECT revoked_at FROM refresh_tokens WHERE id = ?", Timestamp.class, tokenId);
  }

  @Test
  void revokesEveryLiveTokenOfTheSubject() {
    Admin admin = newAdmin();
    UUID tokenOne = UUID.randomUUID();
    UUID tokenTwo = UUID.randomUUID();
    insertLiveRefreshToken(tokenOne, admin.id().value());
    insertLiveRefreshToken(tokenTwo, admin.id().value());

    adapter.revokeAllForSubject(admin.id().value());

    assertThat(revokedAtOf(tokenOne)).isNotNull();
    assertThat(revokedAtOf(tokenTwo)).isNotNull();
  }

  @Test
  void neverTouchesAnotherSubjectsLiveTokens() {
    Admin revokedSubject = newAdmin();
    Admin untouchedSubject = newAdmin();
    UUID revokedToken = UUID.randomUUID();
    UUID untouchedToken = UUID.randomUUID();
    insertLiveRefreshToken(revokedToken, revokedSubject.id().value());
    insertLiveRefreshToken(untouchedToken, untouchedSubject.id().value());

    adapter.revokeAllForSubject(revokedSubject.id().value());

    assertThat(revokedAtOf(revokedToken)).isNotNull();
    assertThat(revokedAtOf(untouchedToken)).isNull();
  }
}
