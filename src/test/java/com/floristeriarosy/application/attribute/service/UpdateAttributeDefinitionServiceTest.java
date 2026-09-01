package com.floristeriarosy.application.attribute.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.attribute.command.UpdateAttributeDefinitionCommand;
import com.floristeriarosy.application.attribute.dto.AttributeDefinitionDto;
import com.floristeriarosy.application.attribute.port.out.AttributeDefinitionPort;
import com.floristeriarosy.domain.exception.attribute.AttributeDefinitionNotFoundException;
import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateAttributeDefinitionServiceTest {

  @Mock private AttributeDefinitionPort port;

  private UpdateAttributeDefinitionService service;

  @Test
  void relabelsAnExistingDefinition() {
    service = new UpdateAttributeDefinitionService(port);
    AttributeDefinitionId id = AttributeDefinitionId.newId();
    AttributeDefinition definition =
        AttributeDefinition.create(id, "color", "Color", AttributeDataType.TEXT, true, 0);
    when(port.findById(id)).thenReturn(Optional.of(definition));
    when(port.save(any(AttributeDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AttributeDefinitionDto dto =
        service.execute(new UpdateAttributeDefinitionCommand(id.value(), "Colour", false, 2));

    assertThat(dto.label()).isEqualTo("Colour");
    assertThat(dto.filterable()).isFalse();
    assertThat(dto.position()).isEqualTo(2);
    assertThat(dto.attributeKey()).isEqualTo("color");
  }

  @Test
  void rejectsWhenIdDoesNotExist() {
    service = new UpdateAttributeDefinitionService(port);
    UUID id = UUID.randomUUID();
    when(port.findById(AttributeDefinitionId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new UpdateAttributeDefinitionCommand(id, "Colour", true, 0)))
        .isInstanceOf(AttributeDefinitionNotFoundException.class);
  }
}
