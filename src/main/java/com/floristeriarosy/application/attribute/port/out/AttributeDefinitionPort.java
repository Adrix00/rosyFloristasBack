package com.floristeriarosy.application.attribute.port.out;

import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import java.util.List;
import java.util.Optional;

/**
 * Persists and retrieves attribute definitions (ADR-003; product.md, section 8). One port, not
 * split into Read/Write/Existence: the aggregate is a small reference table with no independent
 * existence-only query the product module needs.
 */
public interface AttributeDefinitionPort {

  /**
   * @return every attribute definition, ordered by position then label
   */
  List<AttributeDefinition> findAll();

  /**
   * @param id the attribute definition to load
   * @return the attribute definition, if it exists
   */
  Optional<AttributeDefinition> findById(AttributeDefinitionId id);

  /**
   * @param attributeKey the declared key to look up
   * @return the attribute definition, if it exists
   */
  Optional<AttributeDefinition> findByKey(String attributeKey);

  /**
   * @param definition the attribute definition to insert or update
   * @return the saved attribute definition, with timestamps populated by the database
   */
  AttributeDefinition save(AttributeDefinition definition);

  /**
   * @param id the attribute definition to delete
   */
  void delete(AttributeDefinitionId id);
}
