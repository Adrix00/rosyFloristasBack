package com.floristeriarosy.application.shared.support;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.util.UUID;

/**
 * Resolves the display name inventory.md's {@code adminUserName}/{@code resolvedByAdminName}
 * fields promise: {@code admin_users} has no {@code name} column, so the email is what identifies
 * an admin to another admin reading the panel. Decrypts it (ADR-005) — never call this from a log
 * statement, only to populate a response DTO field.
 *
 * <p>Kept as one small helper rather than repeating the lookup-and-decrypt pair in every inventory
 * service that touches an admin id, same justification as {@code cart.support.CartFinder}.
 */
public final class AdminDisplayNameResolver {

  private AdminDisplayNameResolver() {}

  /**
   * @param adminReadPort resolves the admin's encrypted email by id
   * @param piiCryptoPort decrypts it
   * @param adminUserId the admin to name, or {@code null} for a system-generated action
   * @return the admin's email, or {@code null} if {@code adminUserId} is {@code null} or the admin
   *     no longer exists
   */
  public static String resolve(AdminReadPort adminReadPort, PiiCryptoPort piiCryptoPort, UUID adminUserId) {
    if (adminUserId == null) {
      return null;
    }
    return adminReadPort
        .findById(AdminId.of(adminUserId))
        .map(admin -> piiCryptoPort.decrypt(admin.emailEncrypted()))
        .orElse(null);
  }
}
