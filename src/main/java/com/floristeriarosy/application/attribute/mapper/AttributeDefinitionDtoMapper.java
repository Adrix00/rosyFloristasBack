package com.floristeriarosy.application.attribute.mapper;

import com.floristeriarosy.application.attribute.dto.AttributeDefinitionDto;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;

/** Domain to application DTO (00-project-principles.md #10: Application Mapper). */
public final class AttributeDefinitionDtoMapper {

  private AttributeDefinitionDtoMapper() {}

  /**
   * @param definition the domain attribute definition to expose
   * @return its read shape, with plain UUID/enum fields a Controller may hold directly
   */
  public static AttributeDefinitionDto toDto(AttributeDefinition definition) {
    return new AttributeDefinitionDto(
        definition.id().value(),
        definition.attributeKey(),
        definition.label(),
        definition.dataType(),
        definition.filterable(),
        definition.position(),
        definition.createdAt(),
        definition.updatedAt());
  }
}
