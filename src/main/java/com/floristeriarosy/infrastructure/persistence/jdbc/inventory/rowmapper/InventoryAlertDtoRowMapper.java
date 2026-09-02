package com.floristeriarosy.infrastructure.persistence.jdbc.inventory.rowmapper;

import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.domain.model.inventory.InventoryAlertStatus;
import com.floristeriarosy.domain.model.inventory.InventoryAlertType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps an {@code inventory_alerts} row, joined with its product's {@code name} aliased as {@code
 * product_name}, to an {@link InventoryAlertDto}. Not logged (CLAUDE.md, Logging).
 */
public class InventoryAlertDtoRowMapper implements RowMapper<InventoryAlertDto> {

  /**
   * @param rs the current row
   * @param rowNum the row's index, unused
   * @return the row mapped to an inventory alert read shape
   * @throws SQLException propagated from a column read
   */
  @Override
  public InventoryAlertDto mapRow(ResultSet rs, int rowNum) throws SQLException {
    Timestamp resolvedAt = rs.getTimestamp("resolved_at");
    return new InventoryAlertDto(
        (UUID) rs.getObject("id"),
        InventoryAlertType.valueOf(rs.getString("type")),
        (UUID) rs.getObject("product_id"),
        rs.getString("product_name"),
        rs.getInt("observed_value"),
        rs.getInt("expected_value"),
        InventoryAlertStatus.valueOf(rs.getString("status")),
        (UUID) rs.getObject("resolved_by_admin_id"),
        resolvedAt == null ? null : resolvedAt.toInstant(),
        rs.getTimestamp("created_at").toInstant());
  }
}
