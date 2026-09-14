package com.floristeriarosy.infrastructure.persistence.jdbc.order.rowmapper;

import com.floristeriarosy.application.order.port.out.ShippingRate;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/** Maps one {@code shipping_rates} row to {@link ShippingRate}. */
public class ShippingRateRowMapper implements RowMapper<ShippingRate> {

  /**
   * @param rs the current row
   * @param rowNum the row number, unused
   * @return the mapped rate
   * @throws SQLException a column could not be read
   */
  @Override
  public ShippingRate mapRow(ResultSet rs, int rowNum) throws SQLException {
    BigDecimal freeFromAmount = rs.getBigDecimal("free_from_amount");
    return new ShippingRate(rs.getBigDecimal("price"), rs.wasNull() ? null : freeFromAmount);
  }
}
