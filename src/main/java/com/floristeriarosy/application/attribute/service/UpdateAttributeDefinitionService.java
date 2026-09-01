package com.floristeriarosy.application.attribute.service;

import com.floristeriarosy.application.attribute.command.UpdateAttributeDefinitionCommand;
import com.floristeriarosy.application.attribute.dto.AttributeDefinitionDto;
import com.floristeriarosy.application.attribute.mapper.AttributeDefinitionDtoMapper;
import com.floristeriarosy.application.attribute.port.in.UpdateAttributeDefinitionUseCase;
import com.floristeriarosy.application.attribute.port.out.AttributeDefinitionPort;
import com.floristeriarosy.domain.exception.attribute.AttributeDefinitionNotFoundException;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import com.floristeriarosy.shared.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link UpdateAttributeDefinitionUseCase}: renames the label, filterable flag and
 * position of an existing attribute definition.
 */
@Service
@Transactional
public class UpdateAttributeDefinitionService implements UpdateAttributeDefinitionUseCase {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(UpdateAttributeDefinitionService.class);

  private final AttributeDefinitionPort port;

  /**
   * @param port loads the definition being updated and persists the change
   */
  public UpdateAttributeDefinitionService(AttributeDefinitionPort port) {
    this.port = port;
  }

  /**
   * Renames an existing attribute definition. {@code attributeKey} and {@code dataType} cannot
   * change (product.md, section 3.5).
   *
   * @param command id of the definition to update, plus its new label, filterable flag and
   *     position
   * @return the updated attribute definition
   * @throws AttributeDefinitionNotFoundException {@code command.id()} does not exist
   */
  @Override
  public AttributeDefinitionDto execute(UpdateAttributeDefinitionCommand command) {
    LOGGER.debug(
        "updateAttributeDefinition id={} label={} filterable={} position={}",
        command.id(),
        LogSanitizer.sanitize(command.label()),
        command.filterable(),
        command.position());

    AttributeDefinitionId id = AttributeDefinitionId.of(command.id());
    AttributeDefinition definition =
        port.findById(id)
            .orElseThrow(
                () -> new AttributeDefinitionNotFoundException(
                    "Attribute definition " + id + " not found"));

    definition.relabel(command.label(), command.filterable(), command.position());
    AttributeDefinitionDto result = AttributeDefinitionDtoMapper.toDto(port.save(definition));

    LOGGER.debug("updateAttributeDefinition -> id={} label={}", result.id(), LogSanitizer.sanitize(result.label()));
    return result;
  }
}
