package com.floristeriarosy.infrastructure.persistence.adapter.order;

import com.floristeriarosy.application.order.dto.ShippingRate;
import com.floristeriarosy.application.order.port.out.ShippingRatePort;
import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import com.floristeriarosy.domain.model.order.ShippingBand;
import com.floristeriarosy.infrastructure.persistence.entity.order.ShippingRateEntity;
import com.floristeriarosy.infrastructure.persistence.jdbc.order.repository.ShippingRateJdbcRepository;
import com.floristeriarosy.infrastructure.persistence.jpa.order.repository.ShippingRateJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.order.ShippingBandPersistenceMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link ShippingRatePort} (ADR-002, ADR-003): JDBC for the distance lookup and the
 * radius aggregate, JPA for the writes and the plain listings.
 */
@Repository
public class ShippingRatePersistenceAdapter implements ShippingRatePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShippingRatePersistenceAdapter.class);
  private static final String NO_OVERLAP_CONSTRAINT = "ex_shipping_rates_no_overlap";

  private final ShippingRateJdbcRepository jdbcRepository;
  private final ShippingRateJpaRepository jpaRepository;
  private final ShippingBandPersistenceMapper mapper;

  /**
   * @param jdbcRepository runs the range query and the radius aggregate
   * @param jpaRepository lists, locks and writes the bands
   * @param mapper converts between {@link ShippingBand} and its entity
   */
  public ShippingRatePersistenceAdapter(
      ShippingRateJdbcRepository jdbcRepository,
      ShippingRateJpaRepository jpaRepository,
      ShippingBandPersistenceMapper mapper) {
    this.jdbcRepository = jdbcRepository;
    this.jpaRepository = jpaRepository;
    this.mapper = mapper;
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

  /**
   * @return the end of the furthest active band, or empty if none is active
   */
  @Override
  public Optional<BigDecimal> findMaxDeliveryDistanceKm() {
    return jdbcRepository.findMaxDeliveryDistance();
  }

  /**
   * @return every band, ordered by where it starts
   */
  @Override
  public List<ShippingBand> findAll() {
    LOGGER.debug("findAll");
    List<ShippingBand> result = jpaRepository.findAllOrdered().stream().map(mapper::toDomain).toList();
    LOGGER.debug("findAll -> count={}", result.size());
    return result;
  }

  /**
   * Takes the tariff mutex first, then reads the bands in a new statement, so the list includes
   * everything the previous writer committed (see {@link ShippingRateJdbcRepository#lockTariff()}).
   *
   * @return every band, ordered by where it starts, read under the tariff mutex
   */
  @Override
  public List<ShippingBand> findAllUnderTariffLock() {
    LOGGER.debug("findAllUnderTariffLock");
    jdbcRepository.lockTariff();
    List<ShippingBand> result = jpaRepository.findAllOrdered().stream().map(mapper::toDomain).toList();
    LOGGER.debug("findAllUnderTariffLock -> count={}", result.size());
    return result;
  }

  /**
   * Updates the managed row in place when {@code band.id()} exists, and flushes inside the {@code
   * try}: under a service's transaction a plain save would defer the {@code INSERT}/{@code UPDATE}
   * to the commit, after this {@code catch} has returned.
   *
   * @param band the band to insert or update
   * @return the saved band
   * @throws ShippingRatesInvalidException the exclusion constraint refused overlapping active bands
   */
  @Override
  public ShippingBand save(ShippingBand band) {
    LOGGER.debug("save id={}", band.id());
    ShippingRateEntity entity = jpaRepository.findById(band.id().value()).orElse(null);
    if (entity != null) {
      entity.applyChanges(band.minKm(), band.maxKm(), band.price(), band.freeFromAmount(), band.active());
    } else {
      entity = mapper.toEntity(band);
    }
    try {
      ShippingBand result = mapper.toDomain(jpaRepository.saveAndFlush(entity));
      LOGGER.debug("save id={} -> saved", result.id());
      return result;
    } catch (DataIntegrityViolationException violation) {
      throw new ShippingRatesInvalidException(describe(violation));
    }
  }

  /**
   * @param violation the database refusal
   * @return "overlap" only when the violated constraint is the exclusion constraint on active bands;
   *     any other constraint gets a neutral message rather than a misleading one
   */
  private String describe(DataIntegrityViolationException violation) {
    String cause = String.valueOf(violation.getMostSpecificCause().getMessage());
    return cause.contains(NO_OVERLAP_CONSTRAINT)
        ? "The shipping bands overlap"
        : "The shipping bands break a database constraint";
  }
}
