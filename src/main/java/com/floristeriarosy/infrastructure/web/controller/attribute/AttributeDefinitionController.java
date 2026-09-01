package com.floristeriarosy.infrastructure.web.controller.attribute;

import com.floristeriarosy.application.attribute.port.in.CreateAttributeDefinitionUseCase;
import com.floristeriarosy.application.attribute.port.in.DeleteAttributeDefinitionUseCase;
import com.floristeriarosy.application.attribute.port.in.GetAttributeDefinitionsUseCase;
import com.floristeriarosy.application.attribute.port.in.UpdateAttributeDefinitionUseCase;
import com.floristeriarosy.infrastructure.web.mapper.attribute.AttributeDefinitionWebMapper;
import com.floristeriarosy.infrastructure.web.request.attribute.CreateAttributeDefinitionRequest;
import com.floristeriarosy.infrastructure.web.request.attribute.UpdateAttributeDefinitionRequest;
import com.floristeriarosy.infrastructure.web.response.attribute.AttributeDefinitionResponse;
import com.floristeriarosy.shared.util.LogSanitizer;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for {@code /api/v1/product-attributes} (product.md, section 4). */
@RestController
@RequestMapping("/api/v1/product-attributes")
public class AttributeDefinitionController {

  private static final Logger LOGGER = LoggerFactory.getLogger(AttributeDefinitionController.class);

  private final CreateAttributeDefinitionUseCase createAttributeDefinitionUseCase;
  private final UpdateAttributeDefinitionUseCase updateAttributeDefinitionUseCase;
  private final DeleteAttributeDefinitionUseCase deleteAttributeDefinitionUseCase;
  private final GetAttributeDefinitionsUseCase getAttributeDefinitionsUseCase;
  private final AttributeDefinitionWebMapper mapper;

  /**
   * @param createAttributeDefinitionUseCase backs {@code POST /product-attributes}
   * @param updateAttributeDefinitionUseCase backs {@code PUT /product-attributes/{id}}
   * @param deleteAttributeDefinitionUseCase backs {@code DELETE /product-attributes/{id}}
   * @param getAttributeDefinitionsUseCase backs {@code GET /product-attributes}
   * @param mapper translates Request/Response to/from Command/Dto; the only class in this
   *     controller's call graph allowed to touch a domain type
   */
  public AttributeDefinitionController(
      CreateAttributeDefinitionUseCase createAttributeDefinitionUseCase,
      UpdateAttributeDefinitionUseCase updateAttributeDefinitionUseCase,
      DeleteAttributeDefinitionUseCase deleteAttributeDefinitionUseCase,
      GetAttributeDefinitionsUseCase getAttributeDefinitionsUseCase,
      AttributeDefinitionWebMapper mapper) {
    this.createAttributeDefinitionUseCase = createAttributeDefinitionUseCase;
    this.updateAttributeDefinitionUseCase = updateAttributeDefinitionUseCase;
    this.deleteAttributeDefinitionUseCase = deleteAttributeDefinitionUseCase;
    this.getAttributeDefinitionsUseCase = getAttributeDefinitionsUseCase;
    this.mapper = mapper;
  }

  /**
   * {@code GET /product-attributes} (public): the front needs to know what filters to offer.
   *
   * @return 200 with every declared attribute definition
   */
  @GetMapping
  public ResponseEntity<List<AttributeDefinitionResponse>> getAll() {
    LOGGER.debug("GET /product-attributes");
    List<AttributeDefinitionResponse> response =
        getAttributeDefinitionsUseCase.execute().stream().map(mapper::toResponse).toList();
    LOGGER.debug("GET /product-attributes -> 200 count={}", response.size());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code POST /product-attributes} (ADMIN — unenforced, dev-plan.md).
   *
   * @param request key, label, data type, filterable flag and position of the definition to
   *     create
   * @return 201 with the created attribute definition
   */
  @PostMapping
  public ResponseEntity<AttributeDefinitionResponse> create(
      @Valid @RequestBody CreateAttributeDefinitionRequest request) {
    LOGGER.debug("POST /product-attributes attributeKey={}", LogSanitizer.sanitize(request.attributeKey()));
    AttributeDefinitionResponse response =
        mapper.toResponse(createAttributeDefinitionUseCase.execute(mapper.toCommand(request)));
    LOGGER.debug("POST /product-attributes -> 201 id={}", response.id());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * {@code PUT /product-attributes/{id}} (ADMIN — unenforced, dev-plan.md).
   *
   * @param id the attribute definition to update
   * @param request the new label, filterable flag and position
   * @return 200 with the updated attribute definition
   */
  @PutMapping("/{id}")
  public ResponseEntity<AttributeDefinitionResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateAttributeDefinitionRequest request) {
    LOGGER.debug("PUT /product-attributes/{}", id);
    AttributeDefinitionResponse response =
        mapper.toResponse(updateAttributeDefinitionUseCase.execute(mapper.toCommand(id, request)));
    LOGGER.debug("PUT /product-attributes/{} -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code DELETE /product-attributes/{id}} (ADMIN — unenforced, dev-plan.md): permanent removal.
   *
   * @param id the attribute definition to delete
   * @return 204, empty body
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    LOGGER.debug("DELETE /product-attributes/{}", id);
    deleteAttributeDefinitionUseCase.execute(mapper.toDeleteCommand(id));
    LOGGER.debug("DELETE /product-attributes/{} -> 204", id);
    return ResponseEntity.noContent().build();
  }
}
