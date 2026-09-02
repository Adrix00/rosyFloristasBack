package com.floristeriarosy.infrastructure.persistence.jdbc.inventory.repository;

import com.floristeriarosy.application.inventory.dto.ReconciliationMismatch;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.infrastructure.persistence.jdbc.inventory.rowmapper.StockMovementDtoRowMapper;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC reads for stock movements (ADR-002): the paginated per-product history, and the
 * reconciliation-mismatch detection query (inventory.md, section 3.8).
 */
@Repository
public class StockMovementJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(StockMovementJdbcRepository.class);

  private static final String FIND_BY_PRODUCT_SQL =
      "SELECT * FROM stock_movements WHERE product_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";

  private static final String COUNT_BY_PRODUCT_SQL = "SELECT COUNT(*) FROM stock_movements WHERE product_id = ?";

  private static final String RECONCILIATION_MISMATCHES_SQL =
      """
      SELECT p.id, p.stock, COALESCE(SUM(m.quantity), 0) AS movements_total
      FROM products p LEFT JOIN stock_movements m ON m.product_id = p.id
      WHERE p.stock IS NOT NULL
      GROUP BY p.id, p.stock
      HAVING p.stock <> COALESCE(SUM(m.quantity), 0)
      """;

  private final JdbcTemplate jdbcTemplate;
  private final StockMovementDtoRowMapper rowMapper = new StockMovementDtoRowMapper();

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public StockMovementJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param productId the product whose history to list
   * @param page requested page, zero-based
   * @param size requested page size
   * @return the matching movements, paginated, most recent first
   */
  public PageResult<StockMovementDto> findByProduct(UUID productId, int page, int size) {
    LOGGER.debug("findByProduct productId={} page={} size={}", productId, page, size);
    List<StockMovementDto> items = jdbcTemplate.query(FIND_BY_PRODUCT_SQL, rowMapper, productId, size, page * size);
    Long total = jdbcTemplate.queryForObject(COUNT_BY_PRODUCT_SQL, Long.class, productId);
    PageResult<StockMovementDto> result = new PageResult<>(items, total == null ? 0 : total, page, size);
    LOGGER.debug("findByProduct productId={} -> totalElements={}", productId, result.totalElements());
    return result;
  }

  /**
   * Runs the {@code RECONCILIATION_MISMATCH} detection query (inventory.md, section 3.8).
   *
   * @return every product currently mismatched
   */
  public List<ReconciliationMismatch> findReconciliationMismatches() {
    LOGGER.debug("findReconciliationMismatches");
    List<ReconciliationMismatch> result =
        jdbcTemplate.query(
            RECONCILIATION_MISMATCHES_SQL,
            (rs, rowNum) ->
                new ReconciliationMismatch(
                    (UUID) rs.getObject("id"), rs.getInt("stock"), rs.getInt("movements_total")));
    LOGGER.debug("findReconciliationMismatches -> count={}", result.size());
    return result;
  }
}
