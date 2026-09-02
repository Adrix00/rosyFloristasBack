package com.floristeriarosy.infrastructure.persistence.adapter.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import com.floristeriarosy.infrastructure.persistence.adapter.admin.AdminPersistenceAdapter;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
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
 * Runs the migrations against real PostgreSQL, then exercises {@link AuditLogPersistenceAdapter}
 * (ADR-010, admin.md rule 3.8): {@code changes} must stay {@code NULL} for {@code admin_user}, a
 * PII-bearing entity type excluded from {@code chk_audit_log_changes_pii_free}'s allow-list.
 */
@Testcontainers
@SpringBootTest
class AuditLogPersistenceAdapterTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private AuditLogPersistenceAdapter adapter;
  @Autowired private AdminPersistenceAdapter adminAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void recordsAnAdminUserActionWithChangesAlwaysNull() throws SQLException {
    String suffix = UUID.randomUUID().toString();
    Admin actor =
        adminAdapter.save(
            Admin.create(
                AdminId.newId(),
                ("encrypted-" + suffix).getBytes(StandardCharsets.UTF_8),
                ("hash-" + suffix).getBytes(StandardCharsets.UTF_8),
                "argon2-hash",
                AdminRole.OWNER));
    UUID entityId = UUID.randomUUID();

    adapter.record(actor.id().value(), AuditAction.UPDATE, "admin_user", entityId, List.of("email", "role"));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT admin_user_id, action, entity_type, changed_fields, changes "
                + "FROM audit_log WHERE entity_type = 'admin_user' AND entity_id = ?",
            entityId);

    assertThat(row.get("admin_user_id")).isEqualTo(actor.id().value());
    assertThat(row.get("action")).isEqualTo("UPDATE");
    assertThat(row.get("changes")).isNull();
    Array changedFields = (Array) row.get("changed_fields");
    assertThat((String[]) changedFields.getArray()).containsExactly("email", "role");
  }
}
