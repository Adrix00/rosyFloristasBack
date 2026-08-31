package com.floristeriarosy.infrastructure.persistence.jdbc.category.rowmapper;

import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.domain.model.category.CategoryStatus;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.category.valueobject.CategorySlug;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

public class CategoryRowMapper implements RowMapper<Category> {

  @Override
  public Category mapRow(ResultSet rs, int rowNum) throws SQLException {
    UUID imageId = (UUID) rs.getObject("image_id");
    return Category.reconstitute(
        CategoryId.of((UUID) rs.getObject("id")),
        rs.getString("name"),
        CategorySlug.of(rs.getString("slug")),
        rs.getString("description"),
        CategoryStatus.valueOf(rs.getString("status")),
        imageId,
        rs.getInt("position"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
