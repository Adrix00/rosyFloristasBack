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
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

  private final CreateCategoryUseCase createCategoryUseCase;
  private final UpdateCategoryUseCase updateCategoryUseCase;
  private final ChangeCategoryStatusUseCase changeCategoryStatusUseCase;
  private final ReorderCategoriesUseCase reorderCategoriesUseCase;
  private final DeleteCategoryUseCase deleteCategoryUseCase;
  private final GetCategoryUseCase getCategoryUseCase;
  private final GetCategoriesUseCase getCategoriesUseCase;
  private final GetCategoryImpactUseCase getCategoryImpactUseCase;
  private final CategoryWebMapper mapper;

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

  @PostMapping
  public ResponseEntity<CategoryResponse> create(
      @Valid @RequestBody CreateCategoryRequest request) {
    CategoryResponse response =
        mapper.toResponse(createCategoryUseCase.execute(mapper.toCommand(request)));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  public ResponseEntity<List<CategorySummaryResponse>> getActive() {
    List<CategorySummaryResponse> response =
        getCategoriesUseCase.execute(mapper.toQuery(false)).stream()
            .map(mapper::toSummaryResponse)
            .toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping("/all")
  public ResponseEntity<List<CategorySummaryResponse>> getAll() {
    List<CategorySummaryResponse> response =
        getCategoriesUseCase.execute(mapper.toQuery(true)).stream()
            .map(mapper::toSummaryResponse)
            .toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{idOrSlug}")
  public ResponseEntity<CategoryResponse> getOne(@PathVariable String idOrSlug) {
    CategoryResponse response =
        mapper.toResponse(getCategoryUseCase.execute(mapper.toQuery(idOrSlug)));
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{id}/impact")
  public ResponseEntity<CategoryImpactResponse> getImpact(@PathVariable UUID id) {
    CategoryImpactResponse response =
        mapper.toImpactResponse(getCategoryImpactUseCase.execute(mapper.toImpactQuery(id)));
    return ResponseEntity.ok(response);
  }

  @PutMapping("/{id}")
  public ResponseEntity<CategoryResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
    CategoryResponse response =
        mapper.toResponse(updateCategoryUseCase.execute(mapper.toCommand(id, request)));
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<CategoryResponse> changeStatus(
      @PathVariable UUID id, @Valid @RequestBody ChangeCategoryStatusRequest request) {
    CategoryResponse response =
        mapper.toResponse(changeCategoryStatusUseCase.execute(mapper.toCommand(id, request)));
    return ResponseEntity.ok(response);
  }

  @PutMapping("/positions")
  public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderCategoriesRequest request) {
    reorderCategoriesUseCase.execute(mapper.toCommand(request));
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    deleteCategoryUseCase.execute(mapper.toDeleteCommand(id));
    return ResponseEntity.noContent().build();
  }
}
