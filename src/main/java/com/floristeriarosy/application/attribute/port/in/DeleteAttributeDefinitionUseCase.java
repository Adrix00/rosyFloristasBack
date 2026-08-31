package com.floristeriarosy.application.attribute.port.in;

import com.floristeriarosy.application.attribute.command.DeleteAttributeDefinitionCommand;

/**
 * Deletes an attribute definition (product.md, section 7). Products that already used the key
 * keep it in their JSONB, orphaned: it stops being filtered and validated, but is not deleted
 * (section 3.5).
 */
public interface DeleteAttributeDefinitionUseCase {

  /**
   * @param command id of the definition to delete
   */
  void execute(DeleteAttributeDefinitionCommand command);
}
