package com.floristeriarosy.infrastructure.persistence.jdbc.product.rowmapper;

import com.floristeriarosy.application.product.dto.ProductImageRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a {@code product_images} row to a {@link ProductImageRef}. Not logged (CLAUDE.md,
 * Logging).
 */
public class ProductImageRefRowMapper implements RowMapper<ProductImageRef> {

  /**
   * @param rs the current row
   * @param rowNum the row's index, unused
   * @return the row mapped to an image reference; {@code url} is always {@code null} (tracked gap)
   * @throws SQLException propagated from a column read
   */
  @Override
  public ProductImageRef mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new ProductImageRef(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("image_id"),
        null,
        rs.getString("alt_text"),
        rs.getInt("position"));
  }
}
