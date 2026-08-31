package com.floristeriarosy.application.attribute.port.in;

import com.floristeriarosy.application.attribute.command.UpdateAttributeDefinitionCommand;
import com.floristeriarosy.application.attribute.dto.AttributeDefinitionDto;

/** Renames the label, filterable flag and position of an attribute definition (product.md, section 7). */
public interface UpdateAttributeDefinitionUseCase {

  /**
   * @param command id of the definition to update, plus its new label, filterable flag and
   *     position
   * @return the updated attribute definition
   */
  AttributeDefinitionDto execute(UpdateAttributeDefinitionCommand command);
}
