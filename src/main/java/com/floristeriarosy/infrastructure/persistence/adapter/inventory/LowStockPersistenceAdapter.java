package com.floristeriarosy.infrastructure.persistence.adapter.inventory;

import com.floristeriarosy.application.inventory.dto.LowStockCandidate;
import com.floristeriarosy.application.inventory.port.out.LowStockPort;
import com.floristeriarosy.infrastructure.persistence.jdbc.inventory.repository.LowStockJdbcRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Implements {@link LowStockPort} (ADR-003) over JDBC (ADR-002). */
@Repository
public class LowStockPersistenceAdapter implements LowStockPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(LowStockPersistenceAdapter.class);

  private final LowStockJdbcRepository jdbcRepository;

  /**
   * @param jdbcRepository runs the {@code LOW_STOCK} detection query
   */
  public LowStockPersistenceAdapter(LowStockJdbcRepository jdbcRepository) {
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @return every managed product currently at or below its configured low-stock threshold
   */
  @Override
  public List<LowStockCandidate> findBelowThreshold() {
    LOGGER.debug("findBelowThreshold");
    List<LowStockCandidate> result = jdbcRepository.findBelowThreshold();
    LOGGER.debug("findBelowThreshold -> count={}", result.size());
    return result;
  }
}
