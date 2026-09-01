package com.floristeriarosy.infrastructure.persistence.support.product;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds {@code products.search_text}: name, description and the text-typed values of {@code
 * attributes}, lower-cased with diacritics stripped (ADR-006). {@code unaccent()} is not used in
 * SQL — it is {@code STABLE}, not {@code IMMUTABLE}, and PostgreSQL 16 rejects it in a generated
 * column; normalizing here in Java sidesteps that.
 *
 * <p>Not a {@code Mapper}: it derives one text field from several, rather than converting between
 * two object shapes, so it lives outside {@code infrastructure.persistence.mapper} (ADR-002).
 */
public final class ProductSearchTextBuilder {

  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");

  private ProductSearchTextBuilder() {}

  /**
   * @param name the product name
   * @param description the product description, or {@code null}
   * @param attributes the attribute values; only {@link String} values contribute
   * @return the normalized, space-joined search text
   */
  public static String build(String name, String description, Map<String, Object> attributes) {
    StringBuilder text = new StringBuilder(normalize(name));
    if (description != null) {
      text.append(' ').append(normalize(description));
    }
    for (Object value : attributes.values()) {
      if (value instanceof String text1) {
        text.append(' ').append(normalize(text1));
      }
    }
    return text.toString();
  }

  /**
   * @param value the raw text to normalize
   * @return {@code value} lower-cased, with diacritics stripped
   */
  private static String normalize(String value) {
    String decomposed = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
    return DIACRITICS.matcher(decomposed).replaceAll("");
  }
}
