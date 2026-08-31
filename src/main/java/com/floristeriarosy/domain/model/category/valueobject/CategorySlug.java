package com.floristeriarosy.domain.model.category.valueobject;

import com.floristeriarosy.domain.exception.category.CategorySlugReservedException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * URL slug of a category, generated from its name. Never supplied by the caller (category.md,
 * section 3.1).
 */
public final class CategorySlug {

  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

  // Rutas literales bajo /categories/ (category.md, sección 3.1): un slug generado no puede
  // chocar con ellas.
  private static final Set<String> RESERVED = Set.of("all", "positions");

  private final String value;

  private CategorySlug(String value) {
    Objects.requireNonNull(value, "value");
    if (!SLUG_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid slug: " + value);
    }
    this.value = value;
  }

  /** Wraps an already-generated, trusted slug value (e.g. read back from persistence). */
  public static CategorySlug of(String value) {
    return new CategorySlug(value);
  }

  public static CategorySlug generateFrom(String name) {
    Objects.requireNonNull(name, "name");
    String normalized =
        Normalizer.normalize(name.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
    normalized = DIACRITICS.matcher(normalized).replaceAll("");
    normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll("-");
    normalized = normalized.replaceAll("^-+|-+$", "");
    if (RESERVED.contains(normalized)) {
      throw new CategorySlugReservedException(
          "'" + name + "' generates the reserved slug '" + normalized + "'");
    }
    return new CategorySlug(normalized);
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CategorySlug categorySlug && value.equals(categorySlug.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value;
  }
}
