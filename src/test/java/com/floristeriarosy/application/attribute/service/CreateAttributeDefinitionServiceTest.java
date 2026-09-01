package com.floristeriarosy.application.attribute.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.attribute.command.CreateAttributeDefinitionCommand;
import com.floristeriarosy.application.attribute.dto.AttributeDefinitionDto;
import com.floristeriarosy.application.attribute.port.out.AttributeDefinitionPort;
import com.floristeriarosy.domain.exception.attribute.AttributeDefinitionAlreadyExistsException;
import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateAttributeDefinitionServiceTest {

  @Mock private AttributeDefinitionPort port;

  private CreateAttributeDefinitionService service;

  @Test
  void createsAttributeDefinitionWhenKeyIsFree() {
    service = new CreateAttributeDefinitionService(port);
    when(port.findByKey("color")).thenReturn(Optional.empty());
    when(port.save(any(AttributeDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AttributeDefinitionDto dto =
        service.execute(
            new CreateAttributeDefinitionCommand("color", "Color", AttributeDataType.TEXT, true, 0));

    assertThat(dto.attributeKey()).isEqualTo("color");
    assertThat(dto.label()).isEqualTo("Color");
  }

  @Test
  void rejectsWhenKeyAlreadyExists() {
    service = new CreateAttributeDefinitionService(port);
    when(port.findByKey("color"))
        .thenReturn(
            Optional.of(
                AttributeDefinition.create(
                    AttributeDefinitionId.newId(), "color", "Color", AttributeDataType.TEXT, true, 0)));

    assertThatThrownBy(
            () ->
                service.execute(
                    new CreateAttributeDefinitionCommand(
                        "color", "Color", AttributeDataType.TEXT, true, 0)))
        .isInstanceOf(AttributeDefinitionAlreadyExistsException.class);
  }
}
