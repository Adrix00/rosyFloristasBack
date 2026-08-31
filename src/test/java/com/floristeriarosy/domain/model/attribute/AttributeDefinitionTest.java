package com.floristeriarosy.domain.model.attribute;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import org.junit.jupiter.api.Test;

class AttributeDefinitionTest {

  @Test
  void relabelChangesLabelFilterableAndPosition() {
    AttributeDefinition definition =
        AttributeDefinition.create(
            AttributeDefinitionId.newId(), "color", "Color", AttributeDataType.TEXT, true, 0);

    definition.relabel("Colour", false, 3);

    assertThat(definition.label()).isEqualTo("Colour");
    assertThat(definition.filterable()).isFalse();
    assertThat(definition.position()).isEqualTo(3);
  }

  @Test
  void relabelNeverChangesKeyOrDataType() {
    AttributeDefinition definition =
        AttributeDefinition.create(
            AttributeDefinitionId.newId(), "color", "Color", AttributeDataType.TEXT, true, 0);

    definition.relabel("Colour", true, 1);

    assertThat(definition.attributeKey()).isEqualTo("color");
    assertThat(definition.dataType()).isEqualTo(AttributeDataType.TEXT);
  }
}
