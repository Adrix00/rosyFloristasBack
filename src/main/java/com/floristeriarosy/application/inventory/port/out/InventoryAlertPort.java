package com.floristeriarosy.application.inventory.port.out;

import com.floristeriarosy.application.inventory.dto.InventoryAlertCriteria;
import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.domain.model.inventory.InventoryAlert;
import com.floristeriarosy.domain.model.inventory.valueobject.InventoryAlertId;
import java.util.List;
import java.util.Optional;

/** Persists and retrieves inventory alerts (ADR-003; inventory.md, section 8). */
public interface InventoryAlertPort {

  /**
   * Inserts a new {@code OPEN} alert. {@code save} only ever creates — {@link #resolve} and {@link
   * #dismiss} are the only mutations to an already-persisted alert (ADR-002: JPA insert vs. simple
   * update, kept as separate methods rather than one generic upsert).
   *
   * @param alert the new alert to insert
   * @return {@code true} if it was created; {@code false} if {@code ux_inventory_alerts_open}
   *     already had one open for this product and type — silently no-op, not an error
   *     (inventory.md, section 3.8; ADR-013)
   */
  boolean save(InventoryAlert alert);

  /**
   * @param id the alert to load
   * @return the alert, if it exists
   */
  Optional<InventoryAlert> findById(InventoryAlertId id);

  /**
   * @return every alert currently {@code OPEN}
   */
  List<InventoryAlert> findOpen();

  /**
   * @param criteria the admin's type/status/product filters and the requested page
   * @return the matching alerts, paginated, most recent first, with {@code productName} resolved
   */
  PageResult<InventoryAlertDto> findAll(InventoryAlertCriteria criteria);

  /**
   * Persists an already-domain-validated resolution: {@code status = RESOLVED} plus the resolution
   * fields (inventory.md, section 3.8: "Resolver"). The caller is expected to have already called
   * {@link InventoryAlert#resolve} on {@code alert}.
   *
   * @param alert the resolved alert
   * @return the persisted alert
   */
  InventoryAlert resolve(InventoryAlert alert);

  /**
   * Persists an already-domain-validated dismissal: {@code status = DISMISSED} plus the resolution
   * fields (inventory.md, section 3.8: "Descartar"). The caller is expected to have already called
   * {@link InventoryAlert#dismiss} on {@code alert}.
   *
   * @param alert the dismissed alert
   * @return the persisted alert
   */
  InventoryAlert dismiss(InventoryAlert alert);
}
