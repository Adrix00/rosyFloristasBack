package com.floristeriarosy.infrastructure.persistence.support.product;

/**
 * The correlated condition every "is this product visible?" query embeds (product.md, section
 * 3.3: {@code ACTIVE} status and at least one {@code ACTIVE} category). Shared as one constant so
 * {@code ProductJdbcRepository} and cart's {@code CartProjectionJdbcRepository} — the add-time
 * visibility check of cart.md, rule 3.6 — don't each carry their own copy to drift out of sync.
 *
 * <p>Expects the enclosing query to alias {@code products} as {@code p}; a caller embeds it as
 * {@code "p.id = ? AND (" + ProductVisibilitySql.CORRELATED_SUBQUERY + ")"}.
 */
public final class ProductVisibilitySql {

  /** The correlated condition body, without the wrapping parentheses. */
  public static final String CORRELATED_SUBQUERY =
      """
      p.status = 'ACTIVE'
        AND EXISTS (
          SELECT 1 FROM product_categories pc
          JOIN categories c ON c.id = pc.category_id AND c.status = 'ACTIVE'
          WHERE pc.product_id = p.id
        )
      """;

  private ProductVisibilitySql() {}
}
