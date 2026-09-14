package com.floristeriarosy.infrastructure.persistence.adapter.order;

import com.floristeriarosy.application.order.port.out.ShippingRate;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.infrastructure.persistence.jdbc.order.repository.ShippingRateJdbcRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Implements {@link ShippingRatePort} over JDBC (ADR-002). */
@Repository
public class ShippingRatePersistenceAdapter implements ShippingRatePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShippingRatePersistenceAdapter.class);

  private final ShippingRateJdbcRepository jdbcRepository;

  /**
   * @param jdbcRepository runs the range query
   */
  public ShippingRatePersistenceAdapter(ShippingRateJdbcRepository jdbcRepository) {
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @param distanceKm the delivery address's distance from the store
   * @return the applicable rate, or empty if none matches
   */
  @Override
  public Optional<ShippingRate> findRateForDistance(BigDecimal distanceKm) {
    LOGGER.debug("findRateForDistance distanceKm={}", distanceKm);
    Optional<ShippingRate> result = jdbcRepository.findForDistance(distanceKm).stream().findFirst();
    LOGGER.debug("findRateForDistance distanceKm={} -> found={}", distanceKm, result.isPresent());
    return result;
  }
}
