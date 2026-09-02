package com.floristeriarosy.infrastructure.persistence.jdbc.inventory.rowmapper;

import com.floristeriarosy.domain.model.inventory.InventoryAlert;
import com.floristeriarosy.domain.model.inventory.InventoryAlertStatus;
import com.floristeriarosy.domain.model.inventory.InventoryAlertType;
import com.floristeriarosy.domain.model.inventory.valueobject.InventoryAlertId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/** Maps an {@code inventory_alerts} row to a domain {@link InventoryAlert}. Not logged (CLAUDE.md, Logging). */
public class InventoryAlertRowMapper implements RowMapper<InventoryAlert> {

  /**
   * @param rs the current row
   * @param rowNum the row's index, unused
   * @return the row rebuilt as a domain alert
   * @throws SQLException propagated from a column read
   */
  @Override
  public InventoryAlert mapRow(ResultSet rs, int rowNum) throws SQLException {
    Timestamp resolvedAt = rs.getTimestamp("resolved_at");
    return InventoryAlert.reconstitute(
        InventoryAlertId.of((UUID) rs.getObject("id")),
        InventoryAlertType.valueOf(rs.getString("type")),
        ProductId.of((UUID) rs.getObject("product_id")),
        rs.getInt("observed_value"),
        rs.getInt("expected_value"),
        InventoryAlertStatus.valueOf(rs.getString("status")),
        (UUID) rs.getObject("resolved_by_admin_id"),
        resolvedAt == null ? null : resolvedAt.toInstant(),
        rs.getString("note"),
        rs.getTimestamp("created_at").toInstant());
  }
}
