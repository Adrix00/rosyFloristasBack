package com.floristeriarosy.application.attribute.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.attribute.dto.AttributeDefinitionDto;
import com.floristeriarosy.application.attribute.port.out.AttributeDefinitionPort;
import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetAttributeDefinitionsServiceTest {

  @Mock private AttributeDefinitionPort port;

  private GetAttributeDefinitionsService service;

  @Test
  void listsEveryAttributeDefinition() {
    service = new GetAttributeDefinitionsService(port);
    when(port.findAll())
        .thenReturn(
            List.of(
                AttributeDefinition.create(
                    AttributeDefinitionId.newId(), "color", "Color", AttributeDataType.TEXT, true, 0)));

    List<AttributeDefinitionDto> result = service.execute();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).attributeKey()).isEqualTo("color");
  }
}
