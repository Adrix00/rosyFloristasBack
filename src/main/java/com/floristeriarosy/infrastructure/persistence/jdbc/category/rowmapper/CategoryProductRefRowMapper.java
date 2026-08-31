package com.floristeriarosy.infrastructure.persistence.jdbc.category.rowmapper;

import com.floristeriarosy.application.category.dto.CategoryProductRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps one row of an impact-preview query to a {@link CategoryProductRef}. Not logged (CLAUDE.md,
 * Logging): runs once per row, the caller already logs the resulting count.
 */
public class CategoryProductRefRowMapper implements RowMapper<CategoryProductRef> {

  /**
   * @param rs the current row, positioned by {@link org.springframework.jdbc.core.JdbcTemplate}
   * @param rowNum the row's index, unused
   * @return the row as a product reference
   * @throws SQLException propagated from a column read
   */
  @Override
  public CategoryProductRef mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new CategoryProductRef(
        (UUID) rs.getObject("id"), rs.getString("name"), rs.getString("status"));
  }
}
