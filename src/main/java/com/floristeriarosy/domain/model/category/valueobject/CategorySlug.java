package com.floristeriarosy.domain.model.category.valueobject;

import com.floristeriarosy.domain.exception.category.CategorySlugReservedException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * URL slug of a category, generated from its name. Never supplied by the caller (category.md,
 * section 3.1).
 */
public final class CategorySlug {

  private static final Logger LOGGER = LoggerFactory.getLogger(CategorySlug.class);

  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

  // Rutas literales bajo /categories/ (category.md, sección 3.1): un slug generado no puede
  // chocar con ellas.
  private static final Set<String> RESERVED = Set.of("all", "positions");

  private final String value;

  /**
   * @param value an already-lowercase, hyphenated, non-reserved slug
   * @throws IllegalArgumentException {@code value} does not match the slug format
   */
  private CategorySlug(String value) {
    Objects.requireNonNull(value, "value");
    if (!SLUG_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid slug: " + value);
    }
    this.value = value;
  }

  /**
   * Wraps an already-generated, trusted slug value (e.g. read back from persistence). Skips the
   * reserved-word check {@link #generateFrom(String)} performs, since a persisted slug was already
   * validated when it was created.
   *
   * @param value the trusted slug value
   * @return the wrapped slug
   */
  public static CategorySlug of(String value) {
    return new CategorySlug(value);
  }

  /**
   * Normalizes {@code name} into a slug: lowercase, accents stripped, non-alphanumeric runs
   * collapsed to a single hyphen, leading/trailing hyphens trimmed.
   *
   * @param name the category name to derive a slug from
   * @return the generated slug
   * @throws CategorySlugReservedException the generated slug collides with a literal route segment
   *     under {@code /categories/} (e.g. {@code all}, {@code positions})
   */
  public static CategorySlug generateFrom(String name) {
    Objects.requireNonNull(name, "name");
    LOGGER.debug("generateFrom name={}", name);

    String normalized =
        Normalizer.normalize(name.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
    normalized = DIACRITICS.matcher(normalized).replaceAll("");
    normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll("-");
    normalized = normalized.replaceAll("^-+|-+$", "");
    if (RESERVED.contains(normalized)) {
      throw new CategorySlugReservedException(
          "'" + name + "' generates the reserved slug '" + normalized + "'");
    }
    CategorySlug result = new CategorySlug(normalized);

    LOGGER.debug("generateFrom name={} -> slug={}", name, result.value);
    return result;
  }

  /**
   * @return the slug value, e.g. {@code "ramos-de-novia"}
   */
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
