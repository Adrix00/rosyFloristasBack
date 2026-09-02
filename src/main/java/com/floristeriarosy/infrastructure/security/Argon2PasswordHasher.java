package com.floristeriarosy.infrastructure.security;

import com.floristeriarosy.application.auth.port.out.PasswordHasherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/** Implements {@link PasswordHasherPort} with Spring Security's Argon2id encoder (ADR-005). */
@Component
public class Argon2PasswordHasher implements PasswordHasherPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(Argon2PasswordHasher.class);

  private final Argon2PasswordEncoder encoder =
      Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

  /**
   * @param rawPassword the plaintext password to hash
   * @return the Argon2id hash
   */
  @Override
  public String hash(String rawPassword) {
    LOGGER.debug("hash");
    String result = encoder.encode(rawPassword);
    LOGGER.debug("hash -> hashed");
    return result;
  }

  /**
   * @param rawPassword the plaintext password a caller supplied
   * @param hash a previously stored Argon2id hash
   * @return whether {@code rawPassword} matches {@code hash}
   */
  @Override
  public boolean matches(String rawPassword, String hash) {
    LOGGER.debug("matches");
    boolean result = encoder.matches(rawPassword, hash);
    LOGGER.debug("matches -> {}", result);
    return result;
  }
}
