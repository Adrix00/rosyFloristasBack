package com.floristeriarosy.infrastructure.security.jwt;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Resolves the authenticated admin's id from the current request's JWT, the same way {@code
 * CartController.resolveCustomerId} resolves a customer — shared here because inventory's write
 * endpoints need it too (inventory.md §5/§6: {@code adminUserId} on every stock movement and alert
 * resolution), and a controller-level helper duplicated per controller would drift.
 */
public final class CurrentAdminResolver {

  private CurrentAdminResolver() {}

  /**
   * @return the authenticated caller's admin id, or {@code null} if the current authentication is
   *     not an {@code ADMIN}-subject JWT (an anonymous caller, or a {@code CUSTOMER} token — never
   *     reachable here in practice, since every endpoint that calls this is {@code
   *     @PreAuthorize("hasRole('ADMIN')")}, but this stays a safe default rather than throwing)
   */
  public static UUID resolve() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      Jwt jwt = jwtAuthentication.getToken();
      if ("ADMIN".equals(jwt.getClaimAsString(AccessTokenJwtClaims.SUBJECT_TYPE))) {
        return UUID.fromString(jwt.getSubject());
      }
    }
    return null;
  }
}
