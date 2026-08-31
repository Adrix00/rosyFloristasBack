package com.floristeriarosy.application.attribute.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.attribute.command.DeleteAttributeDefinitionCommand;
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
class DeleteAttributeDefinitionServiceTest {

  @Mock private AttributeDefinitionPort port;

  private DeleteAttributeDefinitionService service;

  @Test
  void deletesAnExistingDefinition() {
    service = new DeleteAttributeDefinitionService(port);
    AttributeDefinitionId id = AttributeDefinitionId.newId();
    when(port.findById(id))
        .thenReturn(
            Optional.of(
                AttributeDefinition.create(id, "color", "Color", AttributeDataType.TEXT, true, 0)));

    service.execute(new DeleteAttributeDefinitionCommand(id.value()));

    verify(port).delete(id);
  }

  @Test
  void rejectsWhenIdDoesNotExist() {
    service = new DeleteAttributeDefinitionService(port);
    UUID id = UUID.randomUUID();
    when(port.findById(AttributeDefinitionId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new DeleteAttributeDefinitionCommand(id)))
        .isInstanceOf(AttributeDefinitionNotFoundException.class);
    verify(port, never()).delete(AttributeDefinitionId.of(id));
  }
}
