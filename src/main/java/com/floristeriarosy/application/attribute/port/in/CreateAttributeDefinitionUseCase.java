package com.floristeriarosy.application.attribute.port.in;

import com.floristeriarosy.application.attribute.command.CreateAttributeDefinitionCommand;
import com.floristeriarosy.application.attribute.dto.AttributeDefinitionDto;

/** Declares a new product attribute key (product.md, section 7). */
public interface CreateAttributeDefinitionUseCase {

  /**
   * @param command key, label, data type, filterable flag and position of the definition to
   *     create
   * @return the created attribute definition
   */
  AttributeDefinitionDto execute(CreateAttributeDefinitionCommand command);
}
