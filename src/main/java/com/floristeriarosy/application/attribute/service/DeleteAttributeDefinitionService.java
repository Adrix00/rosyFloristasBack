package com.floristeriarosy.application.attribute.service;

import com.floristeriarosy.application.attribute.command.DeleteAttributeDefinitionCommand;
import com.floristeriarosy.application.attribute.port.in.DeleteAttributeDefinitionUseCase;
import com.floristeriarosy.application.attribute.port.out.AttributeDefinitionPort;
import com.floristeriarosy.domain.exception.attribute.AttributeDefinitionNotFoundException;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link DeleteAttributeDefinitionUseCase}: permanently removes an attribute definition. */
@Service
@Transactional
public class DeleteAttributeDefinitionService implements DeleteAttributeDefinitionUseCase {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeleteAttributeDefinitionService.class);

  private final AttributeDefinitionPort port;

  /**
   * @param port checks the definition exists and performs the delete
   */
  public DeleteAttributeDefinitionService(AttributeDefinitionPort port) {
    this.port = port;
  }

  /**
   * Deletes the attribute definition. Products that already used the key keep it in their JSONB,
   * orphaned (product.md, section 3.5).
   *
   * @param command id of the definition to delete
   * @throws AttributeDefinitionNotFoundException {@code command.id()} does not exist
   */
  @Override
  public void execute(DeleteAttributeDefinitionCommand command) {
    LOGGER.debug("deleteAttributeDefinition id={}", command.id());

    AttributeDefinitionId id = AttributeDefinitionId.of(command.id());
    if (port.findById(id).isEmpty()) {
      throw new AttributeDefinitionNotFoundException("Attribute definition " + id + " not found");
    }
    port.delete(id);

    LOGGER.debug("deleteAttributeDefinition -> id={} deleted", id);
  }
}
