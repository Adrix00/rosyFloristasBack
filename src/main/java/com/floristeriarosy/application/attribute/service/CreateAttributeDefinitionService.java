package com.floristeriarosy.application.attribute.service;

import com.floristeriarosy.application.attribute.command.CreateAttributeDefinitionCommand;
import com.floristeriarosy.application.attribute.dto.AttributeDefinitionDto;
import com.floristeriarosy.application.attribute.mapper.AttributeDefinitionDtoMapper;
import com.floristeriarosy.application.attribute.port.in.CreateAttributeDefinitionUseCase;
import com.floristeriarosy.application.attribute.port.out.AttributeDefinitionPort;
import com.floristeriarosy.domain.exception.attribute.AttributeDefinitionAlreadyExistsException;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import com.floristeriarosy.shared.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link CreateAttributeDefinitionUseCase}: declares a new product attribute key. */
@Service
@Transactional
public class CreateAttributeDefinitionService implements CreateAttributeDefinitionUseCase {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CreateAttributeDefinitionService.class);

  private final AttributeDefinitionPort port;

  /**
   * @param port checks the key is not already taken and persists the new definition
   */
  public CreateAttributeDefinitionService(AttributeDefinitionPort port) {
    this.port = port;
  }

  /**
   * Creates a new attribute definition.
   *
   * @param command key, label, data type, filterable flag and position of the definition to
   *     create
   * @return the created attribute definition
   * @throws AttributeDefinitionAlreadyExistsException {@code command.attributeKey()} is already
   *     declared
   */
  @Override
  public AttributeDefinitionDto execute(CreateAttributeDefinitionCommand command) {
    LOGGER.debug(
        "createAttributeDefinition attributeKey={} label={} dataType={} filterable={} position={}",
        LogSanitizer.sanitize(command.attributeKey()),
        LogSanitizer.sanitize(command.label()),
        command.dataType(),
        command.filterable(),
        command.position());

    if (port.findByKey(command.attributeKey()).isPresent()) {
      throw new AttributeDefinitionAlreadyExistsException(
          "An attribute definition with key '" + command.attributeKey() + "' already exists");
    }
    AttributeDefinition definition =
        AttributeDefinition.create(
            AttributeDefinitionId.newId(),
            command.attributeKey(),
            command.label(),
            command.dataType(),
            command.filterable(),
            command.position());
    AttributeDefinitionDto result = AttributeDefinitionDtoMapper.toDto(port.save(definition));

    LOGGER.debug("createAttributeDefinition -> id={} attributeKey={}", result.id(), result.attributeKey());
    return result;
  }
}
