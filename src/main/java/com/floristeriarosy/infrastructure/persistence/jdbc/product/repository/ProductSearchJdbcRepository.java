package com.floristeriarosy.infrastructure.persistence.jdbc.product.repository;

import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductSearchCriteria;
import com.floristeriarosy.application.product.dto.ProductSuggestionDto;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.infrastructure.persistence.jdbc.product.rowmapper.ProductSuggestionRowMapper;
import com.floristeriarosy.infrastructure.persistence.jdbc.product.rowmapper.ProductSummaryRowMapper;
import com.floristeriarosy.infrastructure.persistence.support.product.ProductActiveSalePriceSql;
import com.floristeriarosy.infrastructure.persistence.support.product.ProductSearchTextBuilder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC reads for product search and autocomplete (ADR-002, ADR-006): full-text over {@code
 * search_vector}, trigram over {@code search_text}, filters, joins, pagination and projections —
 * everything {@code ProductJdbcRepository} is not, kept separate so neither class grows unwieldy.
 */
@Repository
public class ProductSearchJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductSearchJdbcRepository.class);

  private static final String VISIBLE_PRODUCTS_SQL =
      "SELECT p.id, p.name, p.slug, p.price, ("
          + ProductActiveSalePriceSql.CORRELATED_SUBQUERY
          + ") AS active_sale_price "
          + """
          FROM products p
          WHERE p.status = 'ACTIVE'
            AND EXISTS (
              SELECT 1 FROM product_categories pc
              JOIN categories c ON c.id = pc.category_id AND c.status = 'ACTIVE'
              WHERE pc.product_id = p.id
            )
          """;

  private static final String AUTOCOMPLETE_SQL =
      """
      SELECT p.name, p.slug
      FROM products p
      WHERE p.status = 'ACTIVE'
        AND EXISTS (
          SELECT 1 FROM product_categories pc
          JOIN categories c ON c.id = pc.category_id AND c.status = 'ACTIVE'
          WHERE pc.product_id = p.id
        )
        AND (p.search_text LIKE ? OR p.search_text % ?)
      ORDER BY similarity(p.search_text, ?) DESC, p.name
      LIMIT ?
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ProductSummaryRowMapper summaryRowMapper = new ProductSummaryRowMapper();
  private final ProductSuggestionRowMapper suggestionRowMapper = new ProductSuggestionRowMapper();

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public ProductSearchJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Builds a filtered, paginated listing of visible products. Filters over the effective price
   * (with an active discount applied, if any) run in an outer query, since PostgreSQL cannot
   * reference a {@code SELECT}-list alias from the same query's {@code WHERE} clause.
   *
   * @param criteria the combinable filters and the requested page
   * @return the matching visible products, paginated
   */
  public PageResult<ProductSummaryDto> search(ProductSearchCriteria criteria) {
    LOGGER.debug(
        "search category={} minPrice={} maxPrice={} onSale={} attributeKeys={} page={} size={}",
        criteria.categoryIdOrSlug(),
        criteria.minPrice(),
        criteria.maxPrice(),
        criteria.onSale(),
        criteria.attributeFilters().keySet(),
        criteria.page(),
        criteria.size());

    StringBuilder inner = new StringBuilder(VISIBLE_PRODUCTS_SQL);
    List<Object> innerParams = new ArrayList<>();
    appendFullTextFilter(inner, innerParams, criteria.q());
    appendCategoryFilter(inner, innerParams, criteria.categoryIdOrSlug());
    appendAttributeFilters(inner, innerParams, criteria.attributeFilters());

    StringBuilder outer = new StringBuilder();
    List<Object> outerParams = new ArrayList<>();
    appendPriceFilters(outer, outerParams, criteria.minPrice(), criteria.maxPrice());
    appendOnSaleFilter(outer, criteria.onSale());

    String outerWhere = outer.isEmpty() ? "" : " WHERE " + outer.substring(" AND ".length());
    String dataSql = "SELECT * FROM (" + inner + ") t" + outerWhere + " ORDER BY t.name LIMIT ? OFFSET ?";
    String countSql = "SELECT COUNT(*) FROM (" + inner + ") t" + outerWhere;

    List<Object> sharedParams = new ArrayList<>(innerParams);
    sharedParams.addAll(outerParams);
    List<Object> dataParams = new ArrayList<>(sharedParams);
    dataParams.add(criteria.size());
    dataParams.add(criteria.page() * criteria.size());

    List<ProductSummaryDto> items = jdbcTemplate.query(dataSql, summaryRowMapper, dataParams.toArray());
    Long total = jdbcTemplate.queryForObject(countSql, Long.class, sharedParams.toArray());
    PageResult<ProductSummaryDto> result =
        new PageResult<>(items, total == null ? 0 : total, criteria.page(), criteria.size());

    LOGGER.debug("search -> totalElements={}", result.totalElements());
    return result;
  }

  /**
   * @param q the raw text typed so far, not yet normalized
   * @param limit the maximum number of suggestions to return
   * @return the matching visible names and slugs, most similar first
   */
  public List<ProductSuggestionDto> autocomplete(String q, int limit) {
    LOGGER.debug("autocomplete limit={}", limit);
    String normalized = ProductSearchTextBuilder.normalize(q);
    List<ProductSuggestionDto> result =
        jdbcTemplate.query(
            AUTOCOMPLETE_SQL, suggestionRowMapper, normalized + "%", normalized, normalized, limit);
    LOGGER.debug("autocomplete -> count={}", result.size());
    return result;
  }

  /**
   * @param sql the inner query being built
   * @param params the inner query's bind parameters, in order
   * @param q free text to match, or {@code null}/blank to skip the filter
   */
  private void appendFullTextFilter(StringBuilder sql, List<Object> params, String q) {
    if (q != null && !q.isBlank()) {
      sql.append(" AND p.search_vector @@ plainto_tsquery('spanish', ?)");
      params.add(q);
    }
  }

  /**
   * @param sql the inner query being built
   * @param params the inner query's bind parameters, in order
   * @param categoryIdOrSlug a category's id or slug, or {@code null}/blank to skip the filter
   */
  private void appendCategoryFilter(StringBuilder sql, List<Object> params, String categoryIdOrSlug) {
    if (categoryIdOrSlug == null || categoryIdOrSlug.isBlank()) {
      return;
    }
    sql.append(
        " AND EXISTS (SELECT 1 FROM product_categories pc2 JOIN categories c2"
            + " ON c2.id = pc2.category_id WHERE pc2.product_id = p.id AND ");
    Optional<UUID> parsed = parseUuid(categoryIdOrSlug);
    if (parsed.isPresent()) {
      sql.append("c2.id = ?)");
      params.add(parsed.get());
    } else {
      sql.append("c2.slug = ?)");
      params.add(categoryIdOrSlug);
    }
  }

  /**
   * @param sql the inner query being built
   * @param params the inner query's bind parameters, in order
   * @param attributeFilters already-typed attribute filters, keyed by declared attribute key
   */
  private void appendAttributeFilters(StringBuilder sql, List<Object> params, Map<String, Object> attributeFilters) {
    for (Map.Entry<String, Object> attribute : attributeFilters.entrySet()) {
      sql.append(" AND p.attributes @> ?::jsonb");
      params.add(toJsonContainmentFragment(attribute.getKey(), attribute.getValue()));
    }
  }

  /**
   * @param sql the outer query being built
   * @param params the outer query's bind parameters, in order
   * @param minPrice minimum effective price, or {@code null} to skip the filter
   * @param maxPrice maximum effective price, or {@code null} to skip the filter
   */
  private void appendPriceFilters(
      StringBuilder sql, List<Object> params, BigDecimal minPrice, BigDecimal maxPrice) {
    if (minPrice != null) {
      sql.append(" AND COALESCE(t.active_sale_price, t.price) >= ?");
      params.add(minPrice);
    }
    if (maxPrice != null) {
      sql.append(" AND COALESCE(t.active_sale_price, t.price) <= ?");
      params.add(maxPrice);
    }
  }

  /**
   * @param sql the outer query being built
   * @param onSale whether to only return products with a currently active discount
   */
  private void appendOnSaleFilter(StringBuilder sql, boolean onSale) {
    if (onSale) {
      sql.append(" AND t.active_sale_price IS NOT NULL");
    }
  }

  /**
   * Builds the single-key JSON object by hand rather than via a JSON library: the codebase has
   * none on its classpath, and the value's type is already constrained to exactly the three
   * cases below (the service coerces it against the attribute's declared {@code data_type}
   * before this is called).
   *
   * @param key the declared attribute key
   * @param value the already-typed filter value ({@link String}, {@link BigDecimal} or {@link
   *     Boolean})
   * @return the single-key JSON object to bind as the {@code jsonb} containment operand
   */
  private String toJsonContainmentFragment(String key, Object value) {
    return "{\"" + escapeJson(key) + "\":" + jsonLiteral(value) + "}";
  }

  /**
   * @param value a {@link String}, {@link BigDecimal} or {@link Boolean}
   * @return its JSON literal representation
   */
  private String jsonLiteral(Object value) {
    if (value instanceof String text) {
      return "\"" + escapeJson(text) + "\"";
    }
    return value.toString();
  }

  /**
   * @param value the raw text to embed in a JSON string
   * @return {@code value} with every character JSON requires escaped ({@code \}, {@code "} and
   *     every control character below {@code U+0020}) — a TEXT attribute value containing a raw
   *     control character would otherwise fail the {@code ::jsonb} cast
   */
  private String escapeJson(String value) {
    StringBuilder escaped = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\\' -> escaped.append("\\\\");
        case '"' -> escaped.append("\\\"");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        default -> {
          if (c < 0x20) {
            escaped.append(String.format("\\u%04x", (int) c));
          } else {
            escaped.append(c);
          }
        }
      }
    }
    return escaped.toString();
  }

  /**
   * @param value the raw candidate
   * @return {@code value} parsed as a {@link UUID}, if it is one
   */
  private Optional<UUID> parseUuid(String value) {
    try {
      return Optional.of(UUID.fromString(value));
    } catch (IllegalArgumentException notAUuid) {
      return Optional.empty();
    }
  }
}
