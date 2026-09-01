package com.floristeriarosy.infrastructure.persistence.jdbc.discount.repository;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC writes for {@code product_discounts.quantity_sold} (ADR-002; product-discounts.md, section
 * 3.5/3.6): the two conditional {@code UPDATE}s that reserve and release promotional units,
 * neither of which reads first — the {@code WHERE} predicate is the concurrency control.
 */
@Repository
public class DiscountReservationJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(DiscountReservationJdbcRepository.class);

  private static final String RESERVE_SQL =
      """
      UPDATE product_discounts
      SET quantity_sold = quantity_sold + ?
      WHERE id = ?
        AND tstzrange(starts_at, ends_at, '[)') @> now()
        AND (quantity_limit IS NULL OR quantity_sold + ? <= quantity_limit)
      """;

  private static final String RELEASE_SQL =
      "UPDATE product_discounts SET quantity_sold = quantity_sold - ? WHERE id = ? AND quantity_sold >= ?";

  private final JdbcTemplate jdbcTemplate;

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public DiscountReservationJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param discountId the discount being purchased under
   * @param quantity the number of units being purchased
   * @return {@code true} if the reservation succeeded, {@code false} if the discount was expired
   *     or exhausted (product-discounts.md, section 3.5)
   */
  public boolean reserve(UUID discountId, int quantity) {
    LOGGER.debug("reserve discountId={} quantity={}", discountId, quantity);
    int updatedRows = jdbcTemplate.update(RESERVE_SQL, quantity, discountId, quantity);
    boolean reserved = updatedRows > 0;
    LOGGER.debug("reserve discountId={} quantity={} -> {}", discountId, quantity, reserved);
    return reserved;
  }

  /**
   * @param discountId the discount to release units back to
   * @param quantity the number of units to return
   */
  public void release(UUID discountId, int quantity) {
    LOGGER.debug("release discountId={} quantity={}", discountId, quantity);
    int updatedRows = jdbcTemplate.update(RELEASE_SQL, quantity, discountId, quantity);
    LOGGER.debug("release discountId={} quantity={} -> {} rows updated", discountId, quantity, updatedRows);
  }
}
