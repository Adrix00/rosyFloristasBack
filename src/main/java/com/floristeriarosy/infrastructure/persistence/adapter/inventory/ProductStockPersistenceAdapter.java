package com.floristeriarosy.infrastructure.persistence.adapter.inventory;

import com.floristeriarosy.application.inventory.port.out.ProductStockPort;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.jdbc.inventory.repository.ProductStockJdbcRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Implements {@link ProductStockPort} (ADR-003) over JDBC (ADR-002), for the reasons in ADR-009. */
@Repository
public class ProductStockPersistenceAdapter implements ProductStockPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductStockPersistenceAdapter.class);

  private final ProductStockJdbcRepository jdbcRepository;

  /**
   * @param jdbcRepository applies the conditional {@code UPDATE} on {@code products.stock}
   */
  public ProductStockPersistenceAdapter(ProductStockJdbcRepository jdbcRepository) {
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @param productId the product to decrement
   * @param quantity the positive amount to subtract
   * @return the resulting stock, if the row was affected
   */
  @Override
  public Optional<Integer> decrementConditional(ProductId productId, int quantity) {
    LOGGER.debug("decrementConditional productId={} quantity={}", productId, quantity);
    Optional<Integer> result = jdbcRepository.decrementConditional(productId.value(), quantity);
    LOGGER.debug("decrementConditional productId={} -> present={}", productId, result.isPresent());
    return result;
  }

  /**
   * @param productId the product to increment
   * @param quantity the positive amount to add
   * @return the resulting stock, if the row was affected
   */
  @Override
  public Optional<Integer> incrementConditional(ProductId productId, int quantity) {
    LOGGER.debug("incrementConditional productId={} quantity={}", productId, quantity);
    Optional<Integer> result = jdbcRepository.incrementConditional(productId.value(), quantity);
    LOGGER.debug("incrementConditional productId={} -> present={}", productId, result.isPresent());
    return result;
  }

  /**
   * @param productId the product to set the starting stock for
   * @param quantity the starting stock
   * @return the resulting stock, equal to {@code quantity}
   */
  @Override
  public int setInitial(ProductId productId, int quantity) {
    LOGGER.debug("setInitial productId={} quantity={}", productId, quantity);
    int result = jdbcRepository.setInitial(productId.value(), quantity);
    LOGGER.debug("setInitial productId={} -> {}", productId, result);
    return result;
  }

  /**
   * @param productId the product to deactivate
   */
  @Override
  public void clear(ProductId productId) {
    LOGGER.debug("clear productId={}", productId);
    jdbcRepository.clear(productId.value());
    LOGGER.debug("clear productId={} -> cleared", productId);
  }
}
