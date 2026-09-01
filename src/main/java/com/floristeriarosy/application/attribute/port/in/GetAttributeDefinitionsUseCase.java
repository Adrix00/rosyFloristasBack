package com.floristeriarosy.application.attribute.port.in;

import com.floristeriarosy.application.attribute.dto.AttributeDefinitionDto;
import java.util.List;

/** Lists every declared attribute definition (product.md, section 7): {@code GET /product-attributes}. */
public interface GetAttributeDefinitionsUseCase {

  /**
   * @return every attribute definition, ordered by position then label
   */
  List<AttributeDefinitionDto> execute();
}
