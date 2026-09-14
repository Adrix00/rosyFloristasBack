package com.floristeriarosy.infrastructure.persistence.jdbc.order.repository;

import com.floristeriarosy.application.order.dto.ShippingRate;
import com.floristeriarosy.infrastructure.persistence.jdbc.order.rowmapper.ShippingRateRowMapper;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Raw JDBC read of {@code shipping_rates} (ADR-002: simple filtered read). */
@Repository
public class ShippingRateJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShippingRateJdbcRepository.class);

  private static final String FIND_FOR_DISTANCE =
      """
      SELECT price, free_from_amount FROM shipping_rates
      WHERE active AND ? >= min_km AND ? < max_km
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ShippingRateRowMapper rowMapper = new ShippingRateRowMapper();

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public ShippingRateJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param distanceKm the distance to match against the active bands
   * @return the matching row, at most one (bands never overlap, {@code
   *     ex_shipping_rates_no_overlap})
   */
  public List<ShippingRate> findForDistance(BigDecimal distanceKm) {
    LOGGER.debug("findForDistance distanceKm={}", distanceKm);
    List<ShippingRate> result = jdbcTemplate.query(FIND_FOR_DISTANCE, rowMapper, distanceKm, distanceKm);
    LOGGER.debug("findForDistance distanceKm={} -> count={}", distanceKm, result.size());
    return result;
  }
}
