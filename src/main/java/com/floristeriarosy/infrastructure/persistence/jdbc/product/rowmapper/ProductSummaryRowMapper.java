package com.floristeriarosy.infrastructure.persistence.jdbc.product.rowmapper;

import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a {@code products} row, with its optional active-discount {@code sale_price} aliased as
 * {@code active_sale_price}, to a {@link ProductSummaryDto}. Not logged (CLAUDE.md, Logging).
 */
public class ProductSummaryRowMapper implements RowMapper<ProductSummaryDto> {

  /**
   * @param rs the current row
   * @param rowNum the row's index, unused
   * @return the row mapped to a product summary; {@code mainImageUrl} is always {@code null}
   *     (tracked gap)
   * @throws SQLException propagated from a column read
   */
  @Override
  public ProductSummaryDto mapRow(ResultSet rs, int rowNum) throws SQLException {
    BigDecimal price = rs.getBigDecimal("price");
    BigDecimal activeSalePrice = rs.getBigDecimal("active_sale_price");
    BigDecimal effectivePrice = activeSalePrice != null ? activeSalePrice : price;
    return new ProductSummaryDto(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("slug"),
        price,
        effectivePrice,
        activeSalePrice != null,
        null);
  }
}
