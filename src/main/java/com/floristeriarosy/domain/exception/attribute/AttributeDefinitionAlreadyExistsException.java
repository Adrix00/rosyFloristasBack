package com.floristeriarosy.domain.exception.attribute;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/** The attribute key is already declared by another attribute definition. */
public final class AttributeDefinitionAlreadyExistsException extends ConflictException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public AttributeDefinitionAlreadyExistsException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AttributeErrorCode.ATTRIBUTE_DEFINITION_ALREADY_EXISTS.name();
  }
}
