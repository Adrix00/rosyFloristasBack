package com.floristeriarosy.infrastructure.persistence.adapter.discount;

import com.floristeriarosy.application.discount.port.out.DiscountReservationPort;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.infrastructure.persistence.jdbc.discount.repository.DiscountReservationJdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Implements {@link DiscountReservationPort} (ADR-003) over JDBC (ADR-002), for the reasons in ADR-009. */
@Repository
public class DiscountReservationPersistenceAdapter implements DiscountReservationPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(DiscountReservationPersistenceAdapter.class);

  private final DiscountReservationJdbcRepository jdbcRepository;

  /**
   * @param jdbcRepository writes {@code product_discounts.quantity_sold} with the two conditional
   *     updates
   */
  public DiscountReservationPersistenceAdapter(DiscountReservationJdbcRepository jdbcRepository) {
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @param id the discount being purchased under
   * @param quantity the number of units being purchased
   * @return {@code true} if the reservation succeeded, {@code false} if the discount was expired
   *     or exhausted
   */
  @Override
  public boolean reserve(DiscountId id, int quantity) {
    LOGGER.debug("reserve id={} quantity={}", id, quantity);
    boolean reserved = jdbcRepository.reserve(id.value(), quantity);
    LOGGER.debug("reserve id={} quantity={} -> {}", id, quantity, reserved);
    return reserved;
  }

  /**
   * @param id the discount to release units back to
   * @param quantity the number of units to return
   */
  @Override
  public void release(DiscountId id, int quantity) {
    LOGGER.debug("release id={} quantity={}", id, quantity);
    jdbcRepository.release(id.value(), quantity);
    LOGGER.debug("release id={} quantity={} -> released", id, quantity);
  }
}
