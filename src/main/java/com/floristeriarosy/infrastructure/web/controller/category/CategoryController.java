package com.floristeriarosy.infrastructure.web.controller.category;

import com.floristeriarosy.application.category.port.in.ChangeCategoryStatusUseCase;
import com.floristeriarosy.application.category.port.in.CreateCategoryUseCase;
import com.floristeriarosy.application.category.port.in.DeleteCategoryUseCase;
import com.floristeriarosy.application.category.port.in.GetCategoriesUseCase;
import com.floristeriarosy.application.category.port.in.GetCategoryImpactUseCase;
import com.floristeriarosy.application.category.port.in.GetCategoryUseCase;
import com.floristeriarosy.application.category.port.in.ReorderCategoriesUseCase;
import com.floristeriarosy.application.category.port.in.UpdateCategoryUseCase;
import com.floristeriarosy.infrastructure.web.mapper.category.CategoryWebMapper;
import com.floristeriarosy.infrastructure.web.request.category.ChangeCategoryStatusRequest;
import com.floristeriarosy.infrastructure.web.request.category.CreateCategoryRequest;
import com.floristeriarosy.infrastructure.web.request.category.ReorderCategoriesRequest;
import com.floristeriarosy.infrastructure.web.request.category.UpdateCategoryRequest;
import com.floristeriarosy.infrastructure.web.response.category.CategoryImpactResponse;
import com.floristeriarosy.infrastructure.web.response.category.CategoryResponse;
import com.floristeriarosy.infrastructure.web.response.category.CategorySummaryResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for {@code /api/v1/categories} (category.md, section 4). */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

  private static final Logger LOGGER = LoggerFactory.getLogger(CategoryController.class);

  private final CreateCategoryUseCase createCategoryUseCase;
  private final UpdateCategoryUseCase updateCategoryUseCase;
  private final ChangeCategoryStatusUseCase changeCategoryStatusUseCase;
  private final ReorderCategoriesUseCase reorderCategoriesUseCase;
  private final DeleteCategoryUseCase deleteCategoryUseCase;
  private final GetCategoryUseCase getCategoryUseCase;
  private final GetCategoriesUseCase getCategoriesUseCase;
  private final GetCategoryImpactUseCase getCategoryImpactUseCase;
  private final CategoryWebMapper mapper;

  /**
   * @param createCategoryUseCase backs {@code POST /categories}
   * @param updateCategoryUseCase backs {@code PUT /categories/{id}}
   * @param changeCategoryStatusUseCase backs {@code PATCH /categories/{id}/status}
   * @param reorderCategoriesUseCase backs {@code PUT /categories/positions}
   * @param deleteCategoryUseCase backs {@code DELETE /categories/{id}}
   * @param getCategoryUseCase backs {@code GET /categories/{idOrSlug}}
   * @param getCategoriesUseCase backs {@code GET /categories} and {@code GET /categories/all}
   * @param getCategoryImpactUseCase backs {@code GET /categories/{id}/impact}
   * @param mapper translates Request/Response to/from Command/Query/Dto; the only class in this
   *     controller's call graph allowed to touch a domain type
   */
  public CategoryController(
      CreateCategoryUseCase createCategoryUseCase,
      UpdateCategoryUseCase updateCategoryUseCase,
      ChangeCategoryStatusUseCase changeCategoryStatusUseCase,
      ReorderCategoriesUseCase reorderCategoriesUseCase,
      DeleteCategoryUseCase deleteCategoryUseCase,
      GetCategoryUseCase getCategoryUseCase,
      GetCategoriesUseCase getCategoriesUseCase,
      GetCategoryImpactUseCase getCategoryImpactUseCase,
      CategoryWebMapper mapper) {
    this.createCategoryUseCase = createCategoryUseCase;
    this.updateCategoryUseCase = updateCategoryUseCase;
    this.changeCategoryStatusUseCase = changeCategoryStatusUseCase;
    this.reorderCategoriesUseCase = reorderCategoriesUseCase;
    this.deleteCategoryUseCase = deleteCategoryUseCase;
    this.getCategoryUseCase = getCategoryUseCase;
    this.getCategoriesUseCase = getCategoriesUseCase;
    this.getCategoryImpactUseCase = getCategoryImpactUseCase;
    this.mapper = mapper;
  }

  /**
   * {@code POST /categories} (ADMIN — unenforced, dev-plan.md).
   *
   * @param request name, description, imageId and position of the category to create
   * @return 201 with the created category
   */
  @PostMapping
  public ResponseEntity<CategoryResponse> create(
      @Valid @RequestBody CreateCategoryRequest request) {
    LOGGER.debug("POST /categories name={}", LogSanitizer.sanitize(request.name()));
    CategoryResponse response =
        mapper.toResponse(createCategoryUseCase.execute(mapper.toCommand(request)));
    LOGGER.debug("POST /categories -> 201 id={}", response.id());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * {@code GET /categories} (public): {@code ACTIVE} categories only.
   *
   * @return 200 with the public category listing
   */
  @GetMapping
  public ResponseEntity<List<CategorySummaryResponse>> getActive() {
    LOGGER.debug("GET /categories");
    List<CategorySummaryResponse> response =
        getCategoriesUseCase.execute(mapper.toQuery(false)).stream()
            .map(mapper::toSummaryResponse)
            .toList();
    LOGGER.debug("GET /categories -> 200 count={}", response.size());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code GET /categories/all} (ADMIN — unenforced, dev-plan.md): every status.
   *
   * @return 200 with the full category listing
   */
  @GetMapping("/all")
  public ResponseEntity<List<CategorySummaryResponse>> getAll() {
    LOGGER.debug("GET /categories/all");
    List<CategorySummaryResponse> response =
        getCategoriesUseCase.execute(mapper.toQuery(true)).stream()
            .map(mapper::toSummaryResponse)
            .toList();
    LOGGER.debug("GET /categories/all -> 200 count={}", response.size());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code GET /categories/{idOrSlug}} (public): 404, not 403, on a non-{@code ACTIVE} match
   * (category.md, section 4).
   *
   * @param idOrSlug a UUID or a slug
   * @return 200 with the matching category
   */
  @GetMapping("/{idOrSlug}")
  public ResponseEntity<CategoryResponse> getOne(@PathVariable String idOrSlug) {
    LOGGER.debug("GET /categories/{}", LogSanitizer.sanitize(idOrSlug));
    CategoryResponse response =
        mapper.toResponse(getCategoryUseCase.execute(mapper.toQuery(idOrSlug)));
    LOGGER.debug("GET /categories/{} -> 200", LogSanitizer.sanitize(idOrSlug));
    return ResponseEntity.ok(response);
  }

  /**
   * {@code GET /categories/{id}/impact} (ADMIN — unenforced, dev-plan.md): read-only preview.
   *
   * @param id the category to preview
   * @return 200 with the impact preview
   */
  @GetMapping("/{id}/impact")
  public ResponseEntity<CategoryImpactResponse> getImpact(@PathVariable UUID id) {
    LOGGER.debug("GET /categories/{}/impact", id);
    CategoryImpactResponse response =
        mapper.toImpactResponse(getCategoryImpactUseCase.execute(mapper.toImpactQuery(id)));
    LOGGER.debug("GET /categories/{}/impact -> 200 totalProducts={}", id, response.totalProducts());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code PUT /categories/{id}} (ADMIN — unenforced, dev-plan.md): full replace.
   *
   * @param id the category to update
   * @param request the new field values
   * @return 200 with the updated category
   */
  @PutMapping("/{id}")
  public ResponseEntity<CategoryResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
    LOGGER.debug("PUT /categories/{} name={}", id, LogSanitizer.sanitize(request.name()));
    CategoryResponse response =
        mapper.toResponse(updateCategoryUseCase.execute(mapper.toCommand(id, request)));
    LOGGER.debug("PUT /categories/{} -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code PATCH /categories/{id}/status} (ADMIN — unenforced, dev-plan.md).
   *
   * @param id the category to change
   * @param request the new status
   * @return 200 with the updated category
   */
  @PatchMapping("/{id}/status")
  public ResponseEntity<CategoryResponse> changeStatus(
      @PathVariable UUID id, @Valid @RequestBody ChangeCategoryStatusRequest request) {
    LOGGER.debug("PATCH /categories/{}/status status={}", id, request.status());
    CategoryResponse response =
        mapper.toResponse(changeCategoryStatusUseCase.execute(mapper.toCommand(id, request)));
    LOGGER.debug("PATCH /categories/{}/status -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code PUT /categories/positions} (ADMIN — unenforced, dev-plan.md): full-catalog reorder.
   *
   * @param request every category id, in its new order
   * @return 200, empty body
   */
  @PutMapping("/positions")
  public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderCategoriesRequest request) {
    LOGGER.debug("PUT /categories/positions categoryIds={}", request.categoryIds());
    reorderCategoriesUseCase.execute(mapper.toCommand(request));
    LOGGER.debug("PUT /categories/positions -> 200");
    return ResponseEntity.ok().build();
  }

  /**
   * {@code DELETE /categories/{id}} (ADMIN — unenforced, dev-plan.md): permanent removal.
   *
   * @param id the category to delete
   * @return 204, empty body
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    LOGGER.debug("DELETE /categories/{}", id);
    deleteCategoryUseCase.execute(mapper.toDeleteCommand(id));
    LOGGER.debug("DELETE /categories/{} -> 204", id);
    return ResponseEntity.noContent().build();
  }
}
