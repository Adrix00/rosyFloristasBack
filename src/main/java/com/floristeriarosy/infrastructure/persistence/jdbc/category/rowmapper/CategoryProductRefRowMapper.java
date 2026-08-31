package com.floristeriarosy.infrastructure.persistence.jdbc.category.rowmapper;

import com.floristeriarosy.application.category.dto.CategoryProductRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

public class CategoryProductRefRowMapper implements RowMapper<CategoryProductRef> {

  @Override
  public CategoryProductRef mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new CategoryProductRef(
        (UUID) rs.getObject("id"), rs.getString("name"), rs.getString("status"));
  }
}
