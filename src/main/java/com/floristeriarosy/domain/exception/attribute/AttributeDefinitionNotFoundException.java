package com.floristeriarosy.domain.exception.attribute;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/** No attribute definition exists with the requested id. */
public final class AttributeDefinitionNotFoundException extends NotFoundException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public AttributeDefinitionNotFoundException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AttributeErrorCode.ATTRIBUTE_DEFINITION_NOT_FOUND.name();
  }
}
