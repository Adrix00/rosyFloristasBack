package com.floristeriarosy.application.product.validation;

import com.floristeriarosy.application.attribute.port.out.AttributeDefinitionPort;
import com.floristeriarosy.domain.exception.product.ProductAttributeTypeMismatchException;
import com.floristeriarosy.domain.exception.product.ProductAttributeUndeclaredException;
import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Validates a product's {@code attributes} JSONB against the declared definitions (product.md,
 * section 3.5). Lives in application, not domain: it needs {@link AttributeDefinitionPort}, a
 * port domain cannot depend on. Shared by {@code CreateProductService} and {@code
 * UpdateProductService} rather than duplicated in both.
 */
@Component
public class ProductAttributeValidator {

  private final AttributeDefinitionPort attributeDefinitionPort;

  /**
   * @param attributeDefinitionPort looks up the declared definition for each attribute key
   */
  public ProductAttributeValidator(AttributeDefinitionPort attributeDefinitionPort) {
    this.attributeDefinitionPort = attributeDefinitionPort;
  }

  /**
   * @param attributes the attribute values to validate
   * @throws ProductAttributeUndeclaredException a key is not declared in {@code
   *     product_attribute_definitions}
   * @throws ProductAttributeTypeMismatchException a value does not respect its key's declared
   *     {@code data_type}
   */
  public void validate(Map<String, Object> attributes) {
    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      AttributeDefinition definition =
          attributeDefinitionPort
              .findByKey(entry.getKey())
              .orElseThrow(
                  () ->
                      new ProductAttributeUndeclaredException(
                          "Attribute key '" + entry.getKey() + "' is not declared"));
      requireMatchingType(definition, entry.getValue());
    }
  }

  /**
   * @param definition the declared definition for the key being checked
   * @param value the value supplied for that key
   * @throws ProductAttributeTypeMismatchException {@code value}'s Java type does not match {@code
   *     definition.dataType()}
   */
  private void requireMatchingType(AttributeDefinition definition, Object value) {
    boolean matches =
        switch (definition.dataType()) {
          case TEXT -> value instanceof String;
          case NUMBER -> value instanceof Number;
          case BOOLEAN -> value instanceof Boolean;
        };
    if (!matches) {
      throw new ProductAttributeTypeMismatchException(
          "Attribute '"
              + definition.attributeKey()
              + "' expects "
              + typeName(definition.dataType())
              + " but got "
              + (value == null ? "null" : value.getClass().getSimpleName()));
    }
  }

  /**
   * @param dataType the declared data type
   * @return a human-readable name for it, for error messages
   */
  private String typeName(AttributeDataType dataType) {
    return switch (dataType) {
      case TEXT -> "TEXT";
      case NUMBER -> "NUMBER";
      case BOOLEAN -> "BOOLEAN";
    };
  }
}
