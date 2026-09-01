package com.floristeriarosy.infrastructure.persistence.jdbc.product.rowmapper;

import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a {@code categories} row, joined through {@code product_categories}, to a {@link
 * ProductCategoryRef}. Not logged (CLAUDE.md, Logging).
 */
public class ProductCategoryRefRowMapper implements RowMapper<ProductCategoryRef> {

  /**
   * @param rs the current row
   * @param rowNum the row's index, unused
   * @return the row mapped to a category reference
   * @throws SQLException propagated from a column read
   */
  @Override
  public ProductCategoryRef mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new ProductCategoryRef((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("slug"));
  }
}
