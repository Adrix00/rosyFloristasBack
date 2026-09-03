package com.floristeriarosy.infrastructure.web.controller.attribute;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.attribute.command.CreateAttributeDefinitionCommand;
import com.floristeriarosy.application.attribute.dto.AttributeDefinitionDto;
import com.floristeriarosy.application.attribute.port.in.CreateAttributeDefinitionUseCase;
import com.floristeriarosy.application.attribute.port.in.DeleteAttributeDefinitionUseCase;
import com.floristeriarosy.application.attribute.port.in.GetAttributeDefinitionsUseCase;
import com.floristeriarosy.application.attribute.port.in.UpdateAttributeDefinitionUseCase;
import com.floristeriarosy.domain.exception.attribute.AttributeDefinitionAlreadyExistsException;
import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.attribute.AttributeDefinitionWebMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AttributeDefinitionController.class)
@Import({AttributeDefinitionWebMapper.class, SecurityConfig.class})
class AttributeDefinitionControllerTest {

  @Autowired private MockMvc mockMvc;

  // SecurityConfig's real filter chain now needs RateLimitFilter, which needs these two.
  @MockitoBean private PiiCryptoPort piiCryptoPort;
  @MockitoBean private AccessTokenPort accessTokenPort;

  @MockitoBean private CreateAttributeDefinitionUseCase createAttributeDefinitionUseCase;
  @MockitoBean private UpdateAttributeDefinitionUseCase updateAttributeDefinitionUseCase;
  @MockitoBean private DeleteAttributeDefinitionUseCase deleteAttributeDefinitionUseCase;
  @MockitoBean private GetAttributeDefinitionsUseCase getAttributeDefinitionsUseCase;

  @Test
  void createReturns201() throws Exception {
    AttributeDefinitionDto dto =
        new AttributeDefinitionDto(
            UUID.randomUUID(), "color", "Color", AttributeDataType.TEXT, true, 0, Instant.now(), Instant.now());
    when(createAttributeDefinitionUseCase.execute(any(CreateAttributeDefinitionCommand.class)))
        .thenReturn(dto);

    mockMvc
        .perform(
            post("/api/v1/product-attributes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attributeKey\":\"color\",\"label\":\"Color\",\"dataType\":\"TEXT\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.attributeKey").value("color"));
  }

  @Test
  void createWithBlankKeyReturns422WithAttributeValidationCode() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/product-attributes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attributeKey\":\"\",\"label\":\"Color\",\"dataType\":\"TEXT\"}"))
        .andExpect(status().is(422))
        .andExpect(
            content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
        .andExpect(jsonPath("$.code").value("ATTRIBUTE_VALIDATION_FAILED"));
  }

  @Test
  void createWithDuplicateKeyReturns409WithAttributeAlreadyExistsCode() throws Exception {
    when(createAttributeDefinitionUseCase.execute(any(CreateAttributeDefinitionCommand.class)))
        .thenThrow(new AttributeDefinitionAlreadyExistsException("already exists"));

    mockMvc
        .perform(
            post("/api/v1/product-attributes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attributeKey\":\"color\",\"label\":\"Color\",\"dataType\":\"TEXT\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ATTRIBUTE_DEFINITION_ALREADY_EXISTS"));
  }
}
