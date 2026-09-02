package com.floristeriarosy.domain.model.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Domain rules of {@link Admin} (admin.md, rules 3.2, 3.4, 3.5, 3.6). */
class AdminTest {

  private static final byte[] EMAIL_ENCRYPTED = "encrypted".getBytes(StandardCharsets.UTF_8);
  private static final byte[] EMAIL_HASH = "hash".getBytes(StandardCharsets.UTF_8);

  private Admin newOwner() {
    return Admin.create(
        AdminId.newId(), EMAIL_ENCRYPTED, EMAIL_HASH, "argon2-provisional", AdminRole.OWNER);
  }

  @Test
  void createIsBornActiveWithoutTotpAndRequiringAPasswordChange() {
    Admin admin = newOwner();

    assertThat(admin.active()).isTrue();
    assertThat(admin.totpEnabled()).isFalse();
    assertThat(admin.totpSecretEncrypted()).isNull();
    assertThat(admin.passwordChangeRequired()).isTrue();
    assertThat(admin.version()).isZero();
  }

  @Test
  void replaceUpdatesEmailAndRoleOnly() {
    Admin admin = newOwner();
    byte[] newEmailEncrypted = "new-encrypted".getBytes(StandardCharsets.UTF_8);
    byte[] newEmailHash = "new-hash".getBytes(StandardCharsets.UTF_8);

    admin.replace(newEmailEncrypted, newEmailHash, AdminRole.ADMIN);

    assertThat(admin.emailEncrypted()).isEqualTo(newEmailEncrypted);
    assertThat(admin.emailHash()).isEqualTo(newEmailHash);
    assertThat(admin.role()).isEqualTo(AdminRole.ADMIN);
  }

  @Test
  void deactivateThenActivateRestoresAccess() {
    Admin admin = newOwner();
    String originalPasswordHash = admin.passwordHash();

    admin.deactivate();
    assertThat(admin.active()).isFalse();

    admin.activate();
    assertThat(admin.active()).isTrue();
    assertThat(admin.role()).isEqualTo(AdminRole.OWNER);
    assertThat(admin.passwordHash()).isEqualTo(originalPasswordHash);
  }

  @Test
  void resetPasswordSetsANewHashAndRequiresAChange() {
    Admin admin = newOwner();
    admin.changeOwnPassword("chosen-by-admin");

    admin.resetPassword("owner-generated-provisional");

    assertThat(admin.passwordHash()).isEqualTo("owner-generated-provisional");
    assertThat(admin.passwordChangeRequired()).isTrue();
  }

  @Test
  void changeOwnPasswordClearsThePasswordChangeRequiredFlag() {
    Admin admin = newOwner();

    admin.changeOwnPassword("chosen-by-admin");

    assertThat(admin.passwordHash()).isEqualTo("chosen-by-admin");
    assertThat(admin.passwordChangeRequired()).isFalse();
  }

  @Test
  void resetTotpClearsEnrollmentButLeavesThePasswordUntouched() {
    Admin admin =
        Admin.reconstitute(
            AdminId.newId(),
            EMAIL_ENCRYPTED,
            EMAIL_HASH,
            "argon2-hash",
            AdminRole.ADMIN,
            "totp-secret".getBytes(StandardCharsets.UTF_8),
            true,
            42L,
            false,
            true,
            0L,
            null,
            null);

    admin.resetTotp();

    assertThat(admin.totpSecretEncrypted()).isNull();
    assertThat(admin.totpEnabled()).isFalse();
    assertThat(admin.totpLastUsedStep()).isNull();
    assertThat(admin.passwordHash()).isEqualTo("argon2-hash");
  }
}
