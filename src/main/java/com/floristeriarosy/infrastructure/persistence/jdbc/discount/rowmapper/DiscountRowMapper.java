package com.floristeriarosy.infrastructure.persistence.jdbc.discount.rowmapper;

import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a {@code product_discounts} row to a domain {@link Discount}. Not logged (CLAUDE.md,
 * Logging): runs once per row, the caller already logs the resulting count. {@code state} is not
 * a column read here — it is derived by {@link Discount#state()} on demand (product-discounts.md,
 * section 6).
 */
public class DiscountRowMapper implements RowMapper<Discount> {

  /**
   * @param rs the current row, positioned by {@link org.springframework.jdbc.core.JdbcTemplate}
   * @param rowNum the row's index, unused
   * @return the row rebuilt as a domain discount
   * @throws SQLException propagated from a column read
   */
  @Override
  public Discount mapRow(ResultSet rs, int rowNum) throws SQLException {
    Integer quantityLimit = (Integer) rs.getObject("quantity_limit");
    return Discount.reconstitute(
        DiscountId.of((UUID) rs.getObject("id")),
        ProductId.of((UUID) rs.getObject("product_id")),
        rs.getBigDecimal("original_price"),
        rs.getBigDecimal("sale_price"),
        rs.getTimestamp("starts_at").toInstant(),
        rs.getTimestamp("ends_at").toInstant(),
        quantityLimit,
        rs.getInt("quantity_sold"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
