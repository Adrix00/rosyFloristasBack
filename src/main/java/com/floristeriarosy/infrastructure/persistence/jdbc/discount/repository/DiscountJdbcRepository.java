package com.floristeriarosy.infrastructure.persistence.jdbc.discount.repository;

import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.infrastructure.persistence.jdbc.discount.rowmapper.DiscountRowMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC reads for a product's discount history (ADR-002; product-discounts.md, section 8): the
 * listing with its state derived at read time, and the currently-active window.
 */
@Repository
public class DiscountJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(DiscountJdbcRepository.class);

  private static final String FIND_BY_PRODUCT_SQL =
      "SELECT * FROM product_discounts WHERE product_id = ? ORDER BY starts_at DESC";

  private static final String FIND_ACTIVE_FOR_PRODUCT_SQL =
      """
      SELECT * FROM product_discounts
      WHERE product_id = ? AND tstzrange(starts_at, ends_at, '[)') @> now()
      """;

  private final JdbcTemplate jdbcTemplate;
  private final DiscountRowMapper rowMapper = new DiscountRowMapper();

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public DiscountJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param productId the product whose discount history to list
   * @return every discount ever created for the product, most recent {@code startsAt} first
   */
  public List<Discount> findByProduct(UUID productId) {
    LOGGER.debug("findByProduct productId={}", productId);
    List<Discount> result = jdbcTemplate.query(FIND_BY_PRODUCT_SQL, rowMapper, productId);
    LOGGER.debug("findByProduct productId={} -> count={}", productId, result.size());
    return result;
  }

  /**
   * @param productId the product to check
   * @return the discount whose vigency window contains the current instant, if any
   *     (product-discounts.md, section 3.1)
   */
  public Optional<Discount> findActiveForProduct(UUID productId) {
    LOGGER.debug("findActiveForProduct productId={}", productId);
    List<Discount> rows = jdbcTemplate.query(FIND_ACTIVE_FOR_PRODUCT_SQL, rowMapper, productId);
    Optional<Discount> result = rows.stream().findFirst();
    LOGGER.debug("findActiveForProduct productId={} -> present={}", productId, result.isPresent());
    return result;
  }
}
