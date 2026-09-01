package com.floristeriarosy.domain.model.product.valueobject;

import com.floristeriarosy.domain.exception.product.ProductSlugReservedException;
import com.floristeriarosy.shared.util.LogSanitizer;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * URL slug of a product, generated from its name. Never supplied by the caller (product.md,
 * section 3.1).
 */
public final class ProductSlug {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductSlug.class);

  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

  // Rutas literales bajo /products/ (product.md, sección 3.1): un slug generado no puede
  // chocar con ellas.
  private static final Set<String> RESERVED = Set.of("suggestions", "all");

  private final String value;

  /**
   * @param value an already-lowercase, hyphenated, non-reserved slug
   * @throws IllegalArgumentException {@code value} does not match the slug format
   */
  private ProductSlug(String value) {
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
  public static ProductSlug of(String value) {
    return new ProductSlug(value);
  }

  /**
   * Normalizes {@code name} into a slug: lowercase, accents stripped, non-alphanumeric runs
   * collapsed to a single hyphen, leading/trailing hyphens trimmed.
   *
   * @param name the product name to derive a slug from
   * @return the generated slug
   * @throws ProductSlugReservedException the generated slug collides with a literal route segment
   *     under {@code /products/} (e.g. {@code suggestions}, {@code all})
   */
  public static ProductSlug generateFrom(String name) {
    Objects.requireNonNull(name, "name");
    LOGGER.debug("generateFrom name={}", LogSanitizer.sanitize(name));

    String normalized =
        Normalizer.normalize(name.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
    normalized = DIACRITICS.matcher(normalized).replaceAll("");
    normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll("-");
    normalized = normalized.replaceAll("^-+|-+$", "");
    if (RESERVED.contains(normalized)) {
      throw new ProductSlugReservedException(
          "'" + LogSanitizer.sanitize(name) + "' generates the reserved slug '" + normalized + "'");
    }
    ProductSlug result = new ProductSlug(normalized);

    LOGGER.debug("generateFrom name={} -> slug={}", LogSanitizer.sanitize(name), result.value);
    return result;
  }

  /**
   * @return the slug value
   */
  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ProductSlug productSlug && value.equals(productSlug.value);
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
