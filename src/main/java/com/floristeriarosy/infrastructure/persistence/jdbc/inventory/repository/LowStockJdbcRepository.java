package com.floristeriarosy.infrastructure.persistence.jdbc.inventory.repository;

import com.floristeriarosy.application.inventory.dto.LowStockCandidate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC reads for the {@code LOW_STOCK} detection query (ADR-002; inventory.md, section 3.8). */
@Repository
public class LowStockJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(LowStockJdbcRepository.class);

  private static final String FIND_BELOW_THRESHOLD_SQL =
      """
      SELECT id, stock, low_stock_threshold
      FROM products
      WHERE stock IS NOT NULL AND low_stock_threshold IS NOT NULL AND stock <= low_stock_threshold
      """;

  private final JdbcTemplate jdbcTemplate;

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public LowStockJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @return every managed product currently at or below its configured low-stock threshold
   */
  public List<LowStockCandidate> findBelowThreshold() {
    LOGGER.debug("findBelowThreshold");
    List<LowStockCandidate> result =
        jdbcTemplate.query(
            FIND_BELOW_THRESHOLD_SQL,
            (rs, rowNum) ->
                new LowStockCandidate(
                    (UUID) rs.getObject("id"), rs.getInt("stock"), rs.getInt("low_stock_threshold")));
    LOGGER.debug("findBelowThreshold -> count={}", result.size());
    return result;
  }
}
