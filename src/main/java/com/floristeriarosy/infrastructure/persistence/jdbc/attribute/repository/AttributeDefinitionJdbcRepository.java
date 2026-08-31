package com.floristeriarosy.infrastructure.persistence.jdbc.attribute.repository;

import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.infrastructure.persistence.jdbc.attribute.rowmapper.AttributeDefinitionRowMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC reads for attribute definitions (ADR-002): the ordered listing. */
@Repository
public class AttributeDefinitionJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(AttributeDefinitionJdbcRepository.class);

  private static final String SELECT_ALL =
      "SELECT * FROM product_attribute_definitions ORDER BY position, label";

  private final JdbcTemplate jdbcTemplate;
  private final AttributeDefinitionRowMapper rowMapper = new AttributeDefinitionRowMapper();

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public AttributeDefinitionJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @return every attribute definition, ordered by position then label
   */
  public List<AttributeDefinition> findAll() {
    LOGGER.debug("findAll");
    List<AttributeDefinition> result = jdbcTemplate.query(SELECT_ALL, rowMapper);
    LOGGER.debug("findAll -> count={}", result.size());
    return result;
  }
}
