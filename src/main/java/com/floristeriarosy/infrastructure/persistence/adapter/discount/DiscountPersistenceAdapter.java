package com.floristeriarosy.infrastructure.persistence.adapter.discount;

import com.floristeriarosy.application.discount.port.out.DiscountReadPort;
import com.floristeriarosy.application.discount.port.out.DiscountWritePort;
import com.floristeriarosy.domain.exception.discount.DiscountLimitBelowSoldException;
import com.floristeriarosy.domain.exception.discount.DiscountOverlapException;
import com.floristeriarosy.domain.exception.discount.DiscountPeriodInvalidException;
import com.floristeriarosy.domain.exception.discount.DiscountPriceNotLowerException;
import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.entity.discount.DiscountEntity;
import com.floristeriarosy.infrastructure.persistence.jdbc.discount.repository.DiscountJdbcRepository;
import com.floristeriarosy.infrastructure.persistence.jpa.discount.repository.DiscountJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.discount.DiscountPersistenceMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link DiscountReadPort} and {@link DiscountWritePort} (ADR-003): JPA for creating
 * and editing, JDBC for the history listing and the active-vigency lookup (ADR-002).
 */
@Repository
public class DiscountPersistenceAdapter implements DiscountReadPort, DiscountWritePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(DiscountPersistenceAdapter.class);

  private final DiscountJpaRepository jpaRepository;
  private final DiscountJdbcRepository jdbcRepository;
  private final DiscountPersistenceMapper mapper;

  /**
   * @param jpaRepository writes and simple lookups by id
   * @param jdbcRepository the history listing and the active-vigency lookup
   * @param mapper converts between the domain {@link Discount} and the JPA {@link DiscountEntity}
   */
  public DiscountPersistenceAdapter(
      DiscountJpaRepository jpaRepository, DiscountJdbcRepository jdbcRepository, DiscountPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.jdbcRepository = jdbcRepository;
    this.mapper = mapper;
  }

  /**
   * @param id the discount to load
   * @return the discount, if it exists
   */
  @Override
  public Optional<Discount> findById(DiscountId id) {
    LOGGER.debug("findById id={}", id);
    Optional<Discount> result = jpaRepository.findById(id.value()).map(mapper::toDomain);
    LOGGER.debug("findById id={} -> found={}", id, result.isPresent());
    return result;
  }

  /**
   * @param productId the product whose discount history to list
   * @return every discount ever created for the product, most recent first
   */
  @Override
  public List<Discount> findByProduct(ProductId productId) {
    LOGGER.debug("findByProduct productId={}", productId);
    List<Discount> result = jdbcRepository.findByProduct(productId.value());
    LOGGER.debug("findByProduct productId={} -> count={}", productId, result.size());
    return result;
  }

  /**
   * @param productId the product to check
   * @return the discount whose vigency window contains the current instant, if any
   */
  @Override
  public Optional<Discount> findActiveForProduct(ProductId productId) {
    LOGGER.debug("findActiveForProduct productId={}", productId);
    Optional<Discount> result = jdbcRepository.findActiveForProduct(productId.value());
    LOGGER.debug("findActiveForProduct productId={} -> present={}", productId, result.isPresent());
    return result;
  }

  /**
   * Updates the managed entity in place when {@code discount.id()} already exists, so a
   * concurrent update to {@code quantity_sold} made by {@code DiscountReservationPort} in between
   * is not clobbered by a full detached-entity overwrite. Builds a fresh entity only for a
   * genuinely new discount.
   *
   * @param discount the discount to insert or update
   * @return the saved discount, with timestamps populated by the database
   * @throws DiscountOverlapException {@code ex_product_discounts_no_overlap} was violated
   * @throws DiscountPeriodInvalidException {@code chk_product_discounts_period} was violated
   * @throws DiscountPriceNotLowerException {@code chk_product_discounts_price} was violated
   * @throws DiscountLimitBelowSoldException {@code chk_product_discounts_sold} was violated
   */
  @Override
  public Discount save(Discount discount) {
    LOGGER.debug("save id={} productId={}", discount.id(), discount.productId());
    DiscountEntity entity = jpaRepository.findById(discount.id().value()).orElse(null);
    if (entity != null) {
      entity.applyChanges(discount);
    } else {
      entity = mapper.toEntity(discount);
    }
    try {
      Discount result = mapper.toDomain(jpaRepository.save(entity));
      LOGGER.debug("save id={} -> saved", result.id());
      return result;
    } catch (DataIntegrityViolationException violation) {
      throw translateSave(violation, discount);
    }
  }

  /**
   * @param id the discount to delete
   */
  @Override
  public void delete(DiscountId id) {
    LOGGER.debug("delete id={}", id);
    jpaRepository.deleteById(id.value());
    LOGGER.debug("delete id={} -> deleted", id);
  }

  /**
   * @param id the discount to close now
   * @return the closed discount
   * @throws DiscountPeriodInvalidException {@code chk_product_discounts_period} was violated
   */
  @Override
  public Discount endNow(DiscountId id) {
    LOGGER.debug("endNow id={}", id);
    DiscountEntity entity =
        jpaRepository
            .findById(id.value())
            .orElseThrow(() -> new IllegalStateException("Discount " + id + " not found"));
    entity.endNow(Instant.now());
    try {
      Discount result = mapper.toDomain(jpaRepository.save(entity));
      LOGGER.debug("endNow id={} -> endsAt={}", id, result.endsAt());
      return result;
    } catch (DataIntegrityViolationException violation) {
      String message = String.valueOf(violation.getMostSpecificCause().getMessage());
      LOGGER.debug("endNow id={} -> constraint violation: {}", id, message);
      if (message.contains("chk_product_discounts_period")) {
        throw new DiscountPeriodInvalidException("endsAt must be after startsAt for discount " + id);
      }
      throw violation;
    }
  }

  /**
   * Translates a database constraint violation from {@link #save} into the business exception it
   * represents. The constraint's name never reaches the client (06-validation-conventions.md).
   *
   * @param violation the low-level constraint violation caught around the save
   * @param discount the discount that was being saved
   * @return the business exception to throw instead, or {@code violation} itself if the
   *     constraint is not one this module owns
   */
  private RuntimeException translateSave(DataIntegrityViolationException violation, Discount discount) {
    String message = String.valueOf(violation.getMostSpecificCause().getMessage());
    LOGGER.debug("save id={} -> constraint violation: {}", discount.id(), message);
    if (message.contains("ex_product_discounts_no_overlap")) {
      return new DiscountOverlapException(
          "Discount " + discount.id() + " overlaps another discount of product " + discount.productId());
    }
    if (message.contains("chk_product_discounts_period")) {
      return new DiscountPeriodInvalidException("endsAt must be after startsAt for discount " + discount.id());
    }
    if (message.contains("chk_product_discounts_price")) {
      return new DiscountPriceNotLowerException(
          "salePrice must be lower than originalPrice for discount " + discount.id());
    }
    if (message.contains("chk_product_discounts_sold")) {
      return new DiscountLimitBelowSoldException(
          "quantityLimit cannot drop below quantitySold for discount " + discount.id());
    }
    return violation;
  }
}
