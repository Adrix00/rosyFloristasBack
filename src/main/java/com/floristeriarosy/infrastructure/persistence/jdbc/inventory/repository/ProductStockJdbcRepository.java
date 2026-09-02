package com.floristeriarosy.infrastructure.persistence.jdbc.inventory.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC writes for {@code products.stock} (ADR-002; ADR-009; inventory.md, section 3.1): every
 * write here is a conditional {@code UPDATE ... RETURNING stock}, never a {@code SELECT} first.
 */
@Repository
public class ProductStockJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductStockJdbcRepository.class);

  private static final String DECREMENT_SQL =
      """
      UPDATE products SET stock = stock - ?, updated_at = ?
      WHERE id = ? AND stock IS NOT NULL AND stock >= ?
      RETURNING stock
      """;

  private static final String INCREMENT_SQL =
      """
      UPDATE products SET stock = stock + ?, updated_at = ?
      WHERE id = ? AND stock IS NOT NULL
      RETURNING stock
      """;

  private static final String SET_INITIAL_SQL =
      "UPDATE products SET stock = ?, updated_at = ? WHERE id = ? RETURNING stock";

  private static final String CLEAR_SQL = "UPDATE products SET stock = NULL, updated_at = ? WHERE id = ?";

  private final JdbcTemplate jdbcTemplate;

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public ProductStockJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param productId the product to decrement
   * @param quantity the positive amount to subtract
   * @return the resulting stock, if the row was affected; empty if zero rows matched
   */
  public Optional<Integer> decrementConditional(UUID productId, int quantity) {
    LOGGER.debug("decrementConditional productId={} quantity={}", productId, quantity);
    Optional<Integer> result =
        firstRow(DECREMENT_SQL, quantity, Timestamp.from(Instant.now()), productId, quantity);
    LOGGER.debug("decrementConditional productId={} -> present={}", productId, result.isPresent());
    return result;
  }

  /**
   * @param productId the product to increment
   * @param quantity the positive amount to add
   * @return the resulting stock, if the row was affected; empty if the product is unmanaged
   */
  public Optional<Integer> incrementConditional(UUID productId, int quantity) {
    LOGGER.debug("incrementConditional productId={} quantity={}", productId, quantity);
    Optional<Integer> result = firstRow(INCREMENT_SQL, quantity, Timestamp.from(Instant.now()), productId);
    LOGGER.debug("incrementConditional productId={} -> present={}", productId, result.isPresent());
    return result;
  }

  /**
   * @param productId the product to set the starting stock for
   * @param quantity the starting stock
   * @return the resulting stock, equal to {@code quantity}
   */
  public int setInitial(UUID productId, int quantity) {
    LOGGER.debug("setInitial productId={} quantity={}", productId, quantity);
    int result =
        firstRow(SET_INITIAL_SQL, quantity, Timestamp.from(Instant.now()), productId)
            .orElseThrow(() -> new IllegalStateException("Product " + productId + " not found"));
    LOGGER.debug("setInitial productId={} -> {}", productId, result);
    return result;
  }

  /**
   * @param productId the product to deactivate
   */
  public void clear(UUID productId) {
    LOGGER.debug("clear productId={}", productId);
    jdbcTemplate.update(CLEAR_SQL, Timestamp.from(Instant.now()), productId);
    LOGGER.debug("clear productId={} -> cleared", productId);
  }

  /**
   * @param sql a single-row {@code UPDATE ... RETURNING stock}
   * @param params the SQL's bind parameters, in order
   * @return the {@code stock} column of the affected row, if any
   */
  private Optional<Integer> firstRow(String sql, Object... params) {
    List<Integer> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("stock"), params);
    return rows.stream().findFirst();
  }
}
