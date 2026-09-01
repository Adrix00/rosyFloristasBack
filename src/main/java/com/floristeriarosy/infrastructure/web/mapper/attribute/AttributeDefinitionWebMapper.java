package com.floristeriarosy.infrastructure.web.mapper.attribute;

import com.floristeriarosy.application.attribute.command.CreateAttributeDefinitionCommand;
import com.floristeriarosy.application.attribute.command.DeleteAttributeDefinitionCommand;
import com.floristeriarosy.application.attribute.command.UpdateAttributeDefinitionCommand;
import com.floristeriarosy.application.attribute.dto.AttributeDefinitionDto;
import com.floristeriarosy.infrastructure.web.request.attribute.CreateAttributeDefinitionRequest;
import com.floristeriarosy.infrastructure.web.request.attribute.UpdateAttributeDefinitionRequest;
import com.floristeriarosy.infrastructure.web.response.attribute.AttributeDefinitionResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Only class in the controller's call graph allowed to touch domain-typed fields
 * ({@code AttributeDataType}): keeps the Controller itself domain-free (HexagonalArchitectureTest).
 * Pure 1:1 field mapping, not logged (see CLAUDE.md, Logging) — every call is already visible in
 * the Controller's own entry/exit log.
 */
@Component
public class AttributeDefinitionWebMapper {

  /**
   * @param request the create request
   * @return the command to hand to {@code CreateAttributeDefinitionUseCase}
   */
  public CreateAttributeDefinitionCommand toCommand(CreateAttributeDefinitionRequest request) {
    return new CreateAttributeDefinitionCommand(
        request.attributeKey(),
        request.label(),
        request.dataType(),
        filterable(request.filterable()),
        position(request.position()));
  }

  /**
   * @param id the attribute definition to update, from the path
   * @param request the new field values
   * @return the command to hand to {@code UpdateAttributeDefinitionUseCase}
   */
  public UpdateAttributeDefinitionCommand toCommand(UUID id, UpdateAttributeDefinitionRequest request) {
    return new UpdateAttributeDefinitionCommand(
        id, request.label(), filterable(request.filterable()), position(request.position()));
  }

  /**
   * @param id the attribute definition to delete, from the path
   * @return the command to hand to {@code DeleteAttributeDefinitionUseCase}
   */
  public DeleteAttributeDefinitionCommand toDeleteCommand(UUID id) {
    return new DeleteAttributeDefinitionCommand(id);
  }

  /**
   * @param dto the attribute definition to expose
   * @return its full API representation
   */
  public AttributeDefinitionResponse toResponse(AttributeDefinitionDto dto) {
    return new AttributeDefinitionResponse(
        dto.id(),
        dto.attributeKey(),
        dto.label(),
        dto.dataType(),
        dto.filterable(),
        dto.position(),
        dto.createdAt(),
        dto.updatedAt());
  }

  /**
   * @param filterable the request's optional filterable field
   * @return {@code filterable}, or {@code true} if absent (matches {@code
   *     product_attribute_definitions.filterable}'s DB default)
   */
  private boolean filterable(Boolean filterable) {
    return filterable == null || filterable;
  }

  /**
   * @param position the request's optional position field
   * @return {@code position}, or {@code 0} if absent
   */
  private int position(Integer position) {
    return position == null ? 0 : position;
  }
}
