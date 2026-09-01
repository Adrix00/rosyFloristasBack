package com.floristeriarosy.shared.util;

import org.owasp.encoder.Encode;

/**
 * Escapes free-text values before they reach a log line, so a caller can't forge fake log entries
 * by putting a newline in a name or a path segment (CWE-117, log injection), or manipulate a
 * terminal viewing the raw log file via an ANSI escape sequence.
 *
 * <p>Delegates to OWASP Java Encoder's {@link Encode#forJava}, which escapes every Java control
 * character, not just CR/LF: broader coverage than a plain regex, at the cost of showing the
 * escape sequence literally in the log line instead of silently dropping it.
 *
 * <p>Only for values that reach a log call raw: request-supplied text (a name, a description, an
 * {@code idOrSlug} path segment). A UUID, an int or an enum can't carry a control character and
 * never needs this.
 */
public final class LogSanitizer {

  private LogSanitizer() {}

  /**
   * @param value the raw, possibly attacker-controlled text to log
   * @return {@code value} with every control character escaped, or {@code null} unchanged
   */
  public static String sanitize(String value) {
    return value == null ? null : Encode.forJava(value);
  }
}
