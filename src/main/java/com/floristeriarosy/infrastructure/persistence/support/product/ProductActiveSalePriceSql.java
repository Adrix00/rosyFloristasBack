package com.floristeriarosy.infrastructure.persistence.support.product;

/**
 * The correlated subquery every product listing/search query embeds to compute a product's
 * effective price (product.md, section 3.1: vigency window and, when limited, unsold units
 * remaining). Shared as one constant so the four JDBC repositories that need it — {@code
 * ProductJdbcRepository}, {@code ProductSearchJdbcRepository}, {@code
 * ProductSuggestionJdbcRepository} — don't each carry their own copy to drift out of sync.
 *
 * <p>Expects the enclosing query to alias {@code products} as {@code p}; a caller embeds it as
 * {@code "(" + ProductActiveSalePriceSql.CORRELATED_SUBQUERY + ") AS active_sale_price"}.
 */
public final class ProductActiveSalePriceSql {

  /** The correlated subquery body, without the wrapping parentheses or column alias. */
  public static final String CORRELATED_SUBQUERY =
      """
      SELECT d.sale_price FROM product_discounts d
      WHERE d.product_id = p.id
        AND tstzrange(d.starts_at, d.ends_at, '[)') @> now()
        AND (d.quantity_limit IS NULL OR d.quantity_sold < d.quantity_limit)
      """;

  private ProductActiveSalePriceSql() {}
}
