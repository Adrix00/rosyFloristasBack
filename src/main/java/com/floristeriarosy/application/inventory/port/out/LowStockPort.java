package com.floristeriarosy.application.inventory.port.out;

import com.floristeriarosy.application.inventory.dto.LowStockCandidate;
import java.util.List;

/** Runs the {@code LOW_STOCK} detection query (ADR-003; inventory.md, section 3.8, section 8). */
public interface LowStockPort {

  /**
   * @return every managed product currently at or below its configured low-stock threshold
   */
  List<LowStockCandidate> findBelowThreshold();
}
