package com.floristeriarosy.infrastructure.persistence.jdbc.product.rowmapper;

import com.floristeriarosy.application.product.dto.ProductSuggestionDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a {@code products} row's {@code name} and {@code slug} to a {@link ProductSuggestionDto}.
 * Not logged (CLAUDE.md, Logging).
 */
public class ProductSuggestionRowMapper implements RowMapper<ProductSuggestionDto> {

  /**
   * @param rs the current row
   * @param rowNum the row's index, unused
   * @return the row mapped to a product suggestion
   * @throws SQLException propagated from a column read
   */
  @Override
  public ProductSuggestionDto mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new ProductSuggestionDto(rs.getString("name"), rs.getString("slug"));
  }
}
