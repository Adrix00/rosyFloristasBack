package com.floristeriarosy.infrastructure.persistence.jdbc.cart.repository;

import com.floristeriarosy.application.cart.dto.CartCatalogEntryDto;
import com.floristeriarosy.infrastructure.persistence.support.product.ProductActiveSalePriceSql;
import com.floristeriarosy.infrastructure.persistence.support.product.ProductVisibilitySql;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC reads backing cart (ADR-002): the batch product-pricing/display projection for {@code GET
 * /cart} and every write use case's response (cart.md §8), plus the single-product visibility and
 * stock checks a write use case needs before mutating.
 */
@Repository
public class CartProjectionJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(CartProjectionJdbcRepository.class);

  private static final String IS_VISIBLE_SQL =
      "SELECT EXISTS (SELECT 1 FROM products p WHERE p.id = ? AND ("
          + ProductVisibilitySql.CORRELATED_SUBQUERY
          + "))";

  private static final String AVAILABLE_STOCK_SQL = "SELECT stock FROM products WHERE id = ?";

  private final JdbcTemplate jdbcTemplate;

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public CartProjectionJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param id the product to check
   * @return {@code true} if {@code status = ACTIVE} and it has at least one {@code ACTIVE}
   *     category (product.md §3.3; cart.md, rule 3.6)
   */
  public boolean isVisible(UUID id) {
    LOGGER.debug("isVisible id={}", id);
    Boolean result = jdbcTemplate.queryForObject(IS_VISIBLE_SQL, Boolean.class, id);
    boolean visible = Boolean.TRUE.equals(result);
    LOGGER.debug("isVisible id={} -> {}", id, visible);
    return visible;
  }

  /**
   * @param id the product to check
   * @return the current managed stock, or empty if inventory is unmanaged (cart.md, rule 3.3)
   */
  public Optional<Integer> availableStock(UUID id) {
    LOGGER.debug("availableStock id={}", id);
    List<Integer> rows =
        jdbcTemplate.query(AVAILABLE_STOCK_SQL, (rs, rowNum) -> (Integer) rs.getObject("stock"), id);
    Optional<Integer> result = rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    LOGGER.debug("availableStock id={} -> {}", id, result.orElse(null));
    return result;
  }

  /**
   * @param productIds the products to look up
   * @return one entry per found product, with its display data (cart.md §8)
   */
  public List<CartCatalogEntryDto> findCatalogEntries(Set<UUID> productIds) {
    LOGGER.debug("findCatalogEntries count={}", productIds.size());
    String sql =
        "SELECT p.id, p.name, p.slug, p.price, p.status, p.stock, ("
            + ProductActiveSalePriceSql.CORRELATED_SUBQUERY
            + ") AS active_sale_price FROM products p WHERE p.id IN ("
            + placeholders(productIds.size())
            + ")";
    List<CartCatalogEntryDto> result =
        jdbcTemplate.query(sql, (rs, rowNum) -> mapCatalogEntry(rs), productIds.toArray());
    LOGGER.debug("findCatalogEntries -> count={}", result.size());
    return result;
  }

  /**
   * @param rs the current row of a {@link #findCatalogEntries} query
   * @return the row mapped to a catalog entry; {@code mainImageUrl} is always {@code null}
   *     (tracked gap, same as product.md's own listings)
   * @throws SQLException propagated from a column read
   */
  private CartCatalogEntryDto mapCatalogEntry(ResultSet rs) throws SQLException {
    BigDecimal price = rs.getBigDecimal("price");
    BigDecimal activeSalePrice = rs.getBigDecimal("active_sale_price");
    BigDecimal effectivePrice = activeSalePrice != null ? activeSalePrice : price;
    Integer stock = (Integer) rs.getObject("stock");
    return new CartCatalogEntryDto(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("slug"),
        null,
        price,
        effectivePrice,
        activeSalePrice != null,
        rs.getString("status"),
        stock);
  }

  /**
   * @param count how many bind placeholders to generate
   * @return {@code count} comma-separated {@code ?} placeholders
   */
  private String placeholders(int count) {
    return IntStream.range(0, count).mapToObj(i -> "?").collect(Collectors.joining(","));
  }
}
