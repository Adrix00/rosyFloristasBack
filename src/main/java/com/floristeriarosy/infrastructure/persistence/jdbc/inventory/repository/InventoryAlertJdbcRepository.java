package com.floristeriarosy.infrastructure.persistence.jdbc.inventory.repository;

import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.domain.model.inventory.InventoryAlert;
import com.floristeriarosy.infrastructure.persistence.jdbc.inventory.rowmapper.InventoryAlertDtoRowMapper;
import com.floristeriarosy.infrastructure.persistence.jdbc.inventory.rowmapper.InventoryAlertRowMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC reads for inventory alerts (ADR-002): every {@code OPEN} alert, and the admin's filtered,
 * paginated listing with the product's name resolved via {@code JOIN} (no N+1 lookups).
 */
@Repository
public class InventoryAlertJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(InventoryAlertJdbcRepository.class);

  private static final String FIND_OPEN_SQL = "SELECT * FROM inventory_alerts WHERE status = 'OPEN'";

  private final JdbcTemplate jdbcTemplate;
  private final InventoryAlertRowMapper rowMapper = new InventoryAlertRowMapper();
  private final InventoryAlertDtoRowMapper dtoRowMapper = new InventoryAlertDtoRowMapper();

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public InventoryAlertJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @return every alert currently {@code OPEN}
   */
  public List<InventoryAlert> findOpen() {
    LOGGER.debug("findOpen");
    List<InventoryAlert> result = jdbcTemplate.query(FIND_OPEN_SQL, rowMapper);
    LOGGER.debug("findOpen -> count={}", result.size());
    return result;
  }

  /**
   * @param type only alerts of this type, or {@code null} for every type
   * @param status only alerts with this status, or {@code null} for every status
   * @param productId only alerts for this product, or {@code null} for every product
   * @param page requested page, zero-based
   * @param size requested page size
   * @return the matching alerts, paginated, most recent first, with {@code productName} resolved
   */
  public PageResult<InventoryAlertDto> findAll(String type, String status, UUID productId, int page, int size) {
    LOGGER.debug(
        "findAll type={} status={} productId={} page={} size={}", type, status, productId, page, size);

    StringBuilder where = new StringBuilder(" WHERE 1 = 1");
    List<Object> params = new ArrayList<>();
    if (type != null) {
      where.append(" AND ia.type = ?");
      params.add(type);
    }
    if (status != null) {
      where.append(" AND ia.status = ?");
      params.add(status);
    }
    if (productId != null) {
      where.append(" AND ia.product_id = ?");
      params.add(productId);
    }
    String fromWhere = "FROM inventory_alerts ia JOIN products p ON p.id = ia.product_id" + where;
    String dataSql =
        "SELECT ia.*, p.name AS product_name " + fromWhere + " ORDER BY ia.created_at DESC LIMIT ? OFFSET ?";
    String countSql = "SELECT COUNT(*) " + fromWhere;

    List<Object> dataParams = new ArrayList<>(params);
    dataParams.add(size);
    dataParams.add(page * size);

    List<InventoryAlertDto> items = jdbcTemplate.query(dataSql, dtoRowMapper, dataParams.toArray());
    Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
    PageResult<InventoryAlertDto> result = new PageResult<>(items, total == null ? 0 : total, page, size);

    LOGGER.debug("findAll -> totalElements={}", result.totalElements());
    return result;
  }
}
