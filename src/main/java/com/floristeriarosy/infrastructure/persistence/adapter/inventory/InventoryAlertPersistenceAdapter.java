package com.floristeriarosy.infrastructure.persistence.adapter.inventory;

import com.floristeriarosy.application.inventory.dto.InventoryAlertCriteria;
import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.inventory.port.out.InventoryAlertPort;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.domain.model.inventory.InventoryAlert;
import com.floristeriarosy.domain.model.inventory.valueobject.InventoryAlertId;
import com.floristeriarosy.infrastructure.persistence.entity.inventory.InventoryAlertEntity;
import com.floristeriarosy.infrastructure.persistence.jdbc.inventory.repository.InventoryAlertJdbcRepository;
import com.floristeriarosy.infrastructure.persistence.jpa.inventory.repository.InventoryAlertJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.inventory.InventoryAlertPersistenceMapper;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link InventoryAlertPort} (ADR-003): JPA for the insert and the two simple
 * per-id updates, JDBC for the open listing and the filtered, paginated admin listing (ADR-002).
 */
@Repository
public class InventoryAlertPersistenceAdapter implements InventoryAlertPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(InventoryAlertPersistenceAdapter.class);

  private final InventoryAlertJpaRepository jpaRepository;
  private final InventoryAlertJdbcRepository jdbcRepository;
  private final InventoryAlertPersistenceMapper mapper;

  /**
   * @param jpaRepository inserts new alerts and updates resolution fields on an existing one
   * @param jdbcRepository the open listing and the filtered, paginated admin listing
   * @param mapper converts between the domain {@link InventoryAlert} and the JPA {@link
   *     InventoryAlertEntity}
   */
  public InventoryAlertPersistenceAdapter(
      InventoryAlertJpaRepository jpaRepository,
      InventoryAlertJdbcRepository jdbcRepository,
      InventoryAlertPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.jdbcRepository = jdbcRepository;
    this.mapper = mapper;
  }

  /**
   * Uses {@code saveAndFlush}, not {@code save}: the entity's id is client-assigned ({@link
   * com.floristeriarosy.domain.model.inventory.valueobject.InventoryAlertId#newId()}), so
   * Hibernate would otherwise defer the {@code INSERT} to the enclosing transaction's commit —
   * which happens after this method's own {@code catch} has already returned to {@link
   * com.floristeriarosy.application.inventory.service.GenerateInventoryAlertsService}, letting
   * {@code ux_inventory_alerts_open}'s violation escape as an uncaught exception instead of the
   * per-row skip it is meant to be.
   *
   * @param alert the new alert to insert
   * @return {@code true} if it was created; {@code false} if {@code ux_inventory_alerts_open}
   *     already had one open for this product and type
   */
  @Override
  public boolean save(InventoryAlert alert) {
    LOGGER.debug("save type={} productId={}", alert.type(), alert.productId());
    try {
      jpaRepository.saveAndFlush(mapper.toEntity(alert));
      LOGGER.debug("save type={} productId={} -> created", alert.type(), alert.productId());
      return true;
    } catch (DataIntegrityViolationException violation) {
      String message = String.valueOf(violation.getMostSpecificCause().getMessage());
      if (message.contains("ux_inventory_alerts_open")) {
        LOGGER.debug("save type={} productId={} -> already open, skipped", alert.type(), alert.productId());
        return false;
      }
      throw violation;
    }
  }

  /**
   * @param id the alert to load
   * @return the alert, if it exists
   */
  @Override
  public Optional<InventoryAlert> findById(InventoryAlertId id) {
    LOGGER.debug("findById id={}", id);
    Optional<InventoryAlert> result = jpaRepository.findById(id.value()).map(mapper::toDomain);
    LOGGER.debug("findById id={} -> found={}", id, result.isPresent());
    return result;
  }

  /**
   * @return every alert currently {@code OPEN}
   */
  @Override
  public List<InventoryAlert> findOpen() {
    LOGGER.debug("findOpen");
    List<InventoryAlert> result = jdbcRepository.findOpen();
    LOGGER.debug("findOpen -> count={}", result.size());
    return result;
  }

  /**
   * @param criteria the admin's type/status/product filters and the requested page
   * @return the matching alerts, paginated, most recent first, with {@code productName} resolved
   */
  @Override
  public PageResult<InventoryAlertDto> findAll(InventoryAlertCriteria criteria) {
    LOGGER.debug(
        "findAll type={} status={} productId={} page={} size={}",
        criteria.type(),
        criteria.status(),
        criteria.productId(),
        criteria.page(),
        criteria.size());
    PageResult<InventoryAlertDto> result =
        jdbcRepository.findAll(
            criteria.type() == null ? null : criteria.type().name(),
            criteria.status() == null ? null : criteria.status().name(),
            criteria.productId(),
            criteria.page(),
            criteria.size());
    LOGGER.debug("findAll -> totalElements={}", result.totalElements());
    return result;
  }

  /**
   * @param alert the resolved alert
   * @return the persisted alert
   */
  @Override
  public InventoryAlert resolve(InventoryAlert alert) {
    LOGGER.debug("resolve id={}", alert.id());
    InventoryAlert result = persistResolution(alert);
    LOGGER.debug("resolve id={} -> resolved", alert.id());
    return result;
  }

  /**
   * @param alert the dismissed alert
   * @return the persisted alert
   */
  @Override
  public InventoryAlert dismiss(InventoryAlert alert) {
    LOGGER.debug("dismiss id={}", alert.id());
    InventoryAlert result = persistResolution(alert);
    LOGGER.debug("dismiss id={} -> dismissed", alert.id());
    return result;
  }

  /**
   * Loads the managed entity and copies the domain-validated resolution fields onto it, shared by
   * {@link #resolve} and {@link #dismiss} — both are a simple {@code UPDATE} on a row already
   * loaded once by the caller.
   *
   * @param alert the already-domain-validated alert, resolved or dismissed
   * @return the persisted alert
   */
  private InventoryAlert persistResolution(InventoryAlert alert) {
    InventoryAlertEntity entity =
        jpaRepository
            .findById(alert.id().value())
            .orElseThrow(() -> new IllegalStateException("Inventory alert " + alert.id() + " not found"));
    entity.applyResolution(alert);
    return mapper.toDomain(jpaRepository.save(entity));
  }
}
