package com.floristeriarosy.infrastructure.persistence.adapter.inventory;

import com.floristeriarosy.application.inventory.dto.ReconciliationMismatch;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.inventory.port.out.StockMovementReadPort;
import com.floristeriarosy.application.inventory.port.out.StockMovementWritePort;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.domain.exception.inventory.InventoryAlreadyInitializedException;
import com.floristeriarosy.domain.model.inventory.StockMovement;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.entity.inventory.StockMovementEntity;
import com.floristeriarosy.infrastructure.persistence.jdbc.inventory.repository.StockMovementJdbcRepository;
import com.floristeriarosy.infrastructure.persistence.jpa.inventory.repository.StockMovementJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.inventory.StockMovementPersistenceMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link StockMovementWritePort} and {@link StockMovementReadPort} (ADR-003): JPA for
 * the insert, JDBC for the paginated history and the reconciliation query (ADR-002).
 */
@Repository
public class StockMovementPersistenceAdapter implements StockMovementWritePort, StockMovementReadPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(StockMovementPersistenceAdapter.class);

  private final StockMovementJpaRepository jpaRepository;
  private final StockMovementJdbcRepository jdbcRepository;
  private final StockMovementPersistenceMapper mapper;

  /**
   * @param jpaRepository inserts the movement row
   * @param jdbcRepository the paginated history listing and the reconciliation query
   * @param mapper converts between the domain {@link StockMovement} and the JPA {@link
   *     StockMovementEntity}
   */
  public StockMovementPersistenceAdapter(
      StockMovementJpaRepository jpaRepository,
      StockMovementJdbcRepository jdbcRepository,
      StockMovementPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.jdbcRepository = jdbcRepository;
    this.mapper = mapper;
  }

  /**
   * Uses {@code saveAndFlush}, not {@code save}: the entity's id is client-assigned ({@link
   * com.floristeriarosy.domain.model.inventory.valueobject.StockMovementId#newId()}), so Hibernate
   * has no need to hit the database immediately and would otherwise defer the {@code INSERT} to
   * the enclosing transaction's commit — which happens after this method (and its {@code catch})
   * has already returned to {@link
   * com.floristeriarosy.application.inventory.service.RegisterStockMovementService}, letting
   * {@code ux_stock_movements_initial}'s violation escape untranslated. Flushing here keeps the
   * violation, and its translation, inside this method's own stack frame.
   *
   * @param movement the movement to insert
   * @return the saved movement, with {@code createdAt} populated by the database
   * @throws InventoryAlreadyInitializedException {@code ux_stock_movements_initial} was violated
   */
  @Override
  public StockMovement save(StockMovement movement) {
    LOGGER.debug("save productId={} type={}", movement.productId(), movement.type());
    try {
      StockMovement result = mapper.toDomain(jpaRepository.saveAndFlush(mapper.toEntity(movement)));
      LOGGER.debug("save -> id={}", result.id());
      return result;
    } catch (DataIntegrityViolationException violation) {
      throw translateSave(violation, movement);
    }
  }

  /**
   * @param productId the product whose history to list
   * @param page requested page, zero-based
   * @param size requested page size
   * @return the matching movements, paginated, most recent first
   */
  @Override
  public PageResult<StockMovementDto> findByProduct(ProductId productId, int page, int size) {
    LOGGER.debug("findByProduct productId={} page={} size={}", productId, page, size);
    PageResult<StockMovementDto> result = jdbcRepository.findByProduct(productId.value(), page, size);
    LOGGER.debug("findByProduct productId={} -> totalElements={}", productId, result.totalElements());
    return result;
  }

  /**
   * @return every product currently mismatched
   */
  @Override
  public List<ReconciliationMismatch> findReconciliationMismatches() {
    LOGGER.debug("findReconciliationMismatches");
    List<ReconciliationMismatch> result = jdbcRepository.findReconciliationMismatches();
    LOGGER.debug("findReconciliationMismatches -> count={}", result.size());
    return result;
  }

  /**
   * Translates a database constraint violation from {@link #save} into the business exception it
   * represents, the same pattern {@code DiscountPersistenceAdapter#translateSave} uses. The
   * constraint's name never reaches the client.
   *
   * @param violation the low-level constraint violation caught around the save
   * @param movement the movement that was being saved
   * @return the business exception to throw instead, or {@code violation} itself if the
   *     constraint is not one this module owns
   */
  private RuntimeException translateSave(DataIntegrityViolationException violation, StockMovement movement) {
    String message = String.valueOf(violation.getMostSpecificCause().getMessage());
    LOGGER.debug("save productId={} -> constraint violation: {}", movement.productId(), message);
    if (message.contains("ux_stock_movements_initial")) {
      return new InventoryAlreadyInitializedException(
          "Product " + movement.productId() + " already has an INITIAL stock movement");
    }
    return violation;
  }
}
