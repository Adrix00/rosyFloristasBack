package com.floristeriarosy.infrastructure.persistence.jdbc.attribute.rowmapper;

import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a {@code product_attribute_definitions} row to a domain {@link AttributeDefinition}. Not
 * logged (CLAUDE.md, Logging): runs once per row, the caller already logs the resulting count.
 */
public class AttributeDefinitionRowMapper implements RowMapper<AttributeDefinition> {

  /**
   * @param rs the current row, positioned by {@link org.springframework.jdbc.core.JdbcTemplate}
   * @param rowNum the row's index, unused
   * @return the row rebuilt as a domain attribute definition
   * @throws SQLException propagated from a column read
   */
  @Override
  public AttributeDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
    return AttributeDefinition.reconstitute(
        AttributeDefinitionId.of((UUID) rs.getObject("id")),
        rs.getString("attribute_key"),
        rs.getString("label"),
        AttributeDataType.valueOf(rs.getString("data_type")),
        rs.getBoolean("filterable"),
        rs.getInt("position"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
