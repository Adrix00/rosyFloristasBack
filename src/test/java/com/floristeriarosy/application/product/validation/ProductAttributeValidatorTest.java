package com.floristeriarosy.application.product.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.attribute.port.out.AttributeDefinitionPort;
import com.floristeriarosy.domain.exception.product.ProductAttributeTypeMismatchException;
import com.floristeriarosy.domain.exception.product.ProductAttributeUndeclaredException;
import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import com.floristeriarosy.domain.model.attribute.AttributeDefinition;
import com.floristeriarosy.domain.model.attribute.valueobject.AttributeDefinitionId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductAttributeValidatorTest {

  @Mock private AttributeDefinitionPort attributeDefinitionPort;

  private ProductAttributeValidator validator;

  private AttributeDefinition definition(String key, AttributeDataType dataType) {
    return AttributeDefinition.create(AttributeDefinitionId.newId(), key, "Label", dataType, true, 0);
  }

  @Test
  void acceptsATextValueForATextAttribute() {
    validator = new ProductAttributeValidator(attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("color"))
        .thenReturn(Optional.of(definition("color", AttributeDataType.TEXT)));

    assertThatCode(() -> validator.validate(Map.of("color", "rojo"))).doesNotThrowAnyException();
  }

  @Test
  void acceptsANumberValueForANumberAttribute() {
    validator = new ProductAttributeValidator(attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("altura"))
        .thenReturn(Optional.of(definition("altura", AttributeDataType.NUMBER)));

    assertThatCode(() -> validator.validate(Map.of("altura", 30))).doesNotThrowAnyException();
  }

  @Test
  void acceptsABooleanValueForABooleanAttribute() {
    validator = new ProductAttributeValidator(attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("riego"))
        .thenReturn(Optional.of(definition("riego", AttributeDataType.BOOLEAN)));

    assertThatCode(() -> validator.validate(Map.of("riego", true))).doesNotThrowAnyException();
  }

  @Test
  void emptyAttributesAreValid() {
    validator = new ProductAttributeValidator(attributeDefinitionPort);

    assertThatCode(() -> validator.validate(Map.of())).doesNotThrowAnyException();
  }

  @Test
  void rejectsAnUndeclaredKey() {
    validator = new ProductAttributeValidator(attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("color")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> validator.validate(Map.of("color", "rojo")))
        .isInstanceOf(ProductAttributeUndeclaredException.class);
  }

  @Test
  void rejectsANumberValueForATextAttribute() {
    validator = new ProductAttributeValidator(attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("color"))
        .thenReturn(Optional.of(definition("color", AttributeDataType.TEXT)));

    assertThatThrownBy(() -> validator.validate(Map.of("color", 42)))
        .isInstanceOf(ProductAttributeTypeMismatchException.class);
  }

  @Test
  void rejectsATextValueForANumberAttribute() {
    validator = new ProductAttributeValidator(attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("altura"))
        .thenReturn(Optional.of(definition("altura", AttributeDataType.NUMBER)));

    assertThatThrownBy(() -> validator.validate(Map.of("altura", "alto")))
        .isInstanceOf(ProductAttributeTypeMismatchException.class);
  }

  @Test
  void rejectsATextValueForABooleanAttribute() {
    validator = new ProductAttributeValidator(attributeDefinitionPort);
    when(attributeDefinitionPort.findByKey("riego"))
        .thenReturn(Optional.of(definition("riego", AttributeDataType.BOOLEAN)));

    assertThatThrownBy(() -> validator.validate(Map.of("riego", "si")))
        .isInstanceOf(ProductAttributeTypeMismatchException.class);
  }
}
