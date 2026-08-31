package com.floristeriarosy.infrastructure.persistence.mapper.attribute;

import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import com.floristeriarosy.infrastructure.persistence.entity.attribute.AttributeDefinitionEntity;
import org.springframework.stereotype.Component;

/** Domain ↔ JPA entity conversions (ADR-002: Persistence Mapper). */
@Component
public class AttributeDefinitionPersistenceMapper {

  /**
   * @param definition the domain attribute definition to persist
   * @return its JPA entity shape
   */
  public AttributeDefinitionEntity toEntity(AttributeDefinition definition) {
    return new AttributeDefinitionEntity(
        definition.id().value(),
        definition.attributeKey(),
        definition.label(),
        definition.dataType(),
        definition.filterable(),
        definition.position(),
        definition.createdAt(),
        definition.updatedAt());
  }

  /**
   * @param entity the persisted JPA entity
   * @return the rebuilt domain attribute definition ({@link AttributeDefinition#reconstitute})
   */
  public AttributeDefinition toDomain(AttributeDefinitionEntity entity) {
    return AttributeDefinition.reconstitute(
        AttributeDefinitionId.of(entity.getId()),
        entity.getAttributeKey(),
        entity.getLabel(),
        entity.getDataType(),
        entity.isFilterable(),
        entity.getPosition(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
