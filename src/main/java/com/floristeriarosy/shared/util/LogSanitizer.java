package com.floristeriarosy.shared.util;

import java.util.regex.Pattern;

/**
 * Strips CR/LF from free-text values before they reach a log line, so a caller can't forge fake log
 * entries by putting a newline in a name or a path segment (CWE-117, log injection).
 *
 * <p>Only for values that reach a log call raw: request-supplied text (a name, a description, an
 * {@code idOrSlug} path segment). A UUID, an int or an enum can't carry a newline and never needs
 * this.
 */
public final class LogSanitizer {

  private static final Pattern CONTROL_CHARS = Pattern.compile("[\r\n]");

  private LogSanitizer() {}

  /**
   * @param value the raw, possibly attacker-controlled text to log
   * @return {@code value} with every CR/LF replaced by a space, or {@code null} unchanged
   */
  public static String sanitize(String value) {
    return value == null ? null : CONTROL_CHARS.matcher(value).replaceAll(" ");
  }
}
