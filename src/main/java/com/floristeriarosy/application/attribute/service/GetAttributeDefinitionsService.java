package com.floristeriarosy.application.attribute.service;

import com.floristeriarosy.application.attribute.dto.AttributeDefinitionDto;
import com.floristeriarosy.application.attribute.mapper.AttributeDefinitionDtoMapper;
import com.floristeriarosy.application.attribute.port.in.GetAttributeDefinitionsUseCase;
import com.floristeriarosy.application.attribute.port.out.AttributeDefinitionPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Implements {@link GetAttributeDefinitionsUseCase}: the public {@code GET /product-attributes} listing. */
@Service
public class GetAttributeDefinitionsService implements GetAttributeDefinitionsUseCase {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(GetAttributeDefinitionsService.class);

  private final AttributeDefinitionPort port;

  /**
   * @param port lists every declared attribute definition
   */
  public GetAttributeDefinitionsService(AttributeDefinitionPort port) {
    this.port = port;
  }

  /**
   * @return every attribute definition, ordered by position then label
   */
  @Override
  public List<AttributeDefinitionDto> execute() {
    LOGGER.debug("getAttributeDefinitions");

    List<AttributeDefinitionDto> result =
        port.findAll().stream().map(AttributeDefinitionDtoMapper::toDto).toList();

    LOGGER.debug("getAttributeDefinitions -> {} definitions", result.size());
    return result;
  }
}
