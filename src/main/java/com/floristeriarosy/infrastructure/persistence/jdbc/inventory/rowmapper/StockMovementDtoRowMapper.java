package com.floristeriarosy.infrastructure.persistence.jdbc.inventory.rowmapper;

import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/** Maps a {@code stock_movements} row directly to a {@link StockMovementDto}. Not logged (CLAUDE.md, Logging). */
public class StockMovementDtoRowMapper implements RowMapper<StockMovementDto> {

  /**
   * @param rs the current row
   * @param rowNum the row's index, unused
   * @return the row mapped to a stock movement read shape
   * @throws SQLException propagated from a column read
   */
  @Override
  public StockMovementDto mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new StockMovementDto(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("product_id"),
        StockMovementType.valueOf(rs.getString("type")),
        rs.getInt("quantity"),
        rs.getInt("resulting_stock"),
        (UUID) rs.getObject("admin_user_id"),
        rs.getString("note"),
        rs.getTimestamp("created_at").toInstant());
  }
}
