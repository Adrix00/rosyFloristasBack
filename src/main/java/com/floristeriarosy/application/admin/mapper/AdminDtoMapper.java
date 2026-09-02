package com.floristeriarosy.application.admin.mapper;

import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.admin.Admin;
import org.springframework.stereotype.Component;

/**
 * Domain to application DTO (00-project-principles.md #10: Application Mapper). Unlike a plain
 * field-copy mapper, this one needs {@link PiiCryptoPort} to decrypt the email for display
 * (admin.md, section 6) — a Spring component with constructor injection, not a static utility.
 */
@Component
public class AdminDtoMapper {

  private final PiiCryptoPort piiCryptoPort;

  /**
   * @param piiCryptoPort decrypts the stored email
   */
  public AdminDtoMapper(PiiCryptoPort piiCryptoPort) {
    this.piiCryptoPort = piiCryptoPort;
  }

  /**
   * @param admin the domain admin to expose
   * @return its read shape, with the email decrypted and never {@code passwordHash}/{@code
   *     totpSecretEncrypted}/{@code totpLastUsedStep}
   */
  public AdminDto toDto(Admin admin) {
    return new AdminDto(
        admin.id().value(),
        piiCryptoPort.decrypt(admin.emailEncrypted()),
        admin.role(),
        admin.active(),
        admin.totpEnabled(),
        admin.passwordChangeRequired(),
        admin.createdAt(),
        admin.updatedAt());
  }
}
