package com.floristeriarosy.infrastructure.web.mapper.category;

import com.floristeriarosy.application.category.command.ChangeCategoryStatusCommand;
import com.floristeriarosy.application.category.command.CreateCategoryCommand;
import com.floristeriarosy.application.category.command.DeleteCategoryCommand;
import com.floristeriarosy.application.category.command.ReorderCategoriesCommand;
import com.floristeriarosy.application.category.command.UpdateCategoryCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.dto.CategoryImpact;
import com.floristeriarosy.application.category.dto.CategoryProductRef;
import com.floristeriarosy.application.category.query.GetCategoriesQuery;
import com.floristeriarosy.application.category.query.GetCategoryImpactQuery;
import com.floristeriarosy.application.category.query.GetCategoryQuery;
import com.floristeriarosy.infrastructure.web.request.category.ChangeCategoryStatusRequest;
import com.floristeriarosy.infrastructure.web.request.category.CreateCategoryRequest;
import com.floristeriarosy.infrastructure.web.request.category.ReorderCategoriesRequest;
import com.floristeriarosy.infrastructure.web.request.category.UpdateCategoryRequest;
import com.floristeriarosy.infrastructure.web.response.category.CategoryImpactResponse;
import com.floristeriarosy.infrastructure.web.response.category.CategoryProductRefResponse;
import com.floristeriarosy.infrastructure.web.response.category.CategoryResponse;
import com.floristeriarosy.infrastructure.web.response.category.CategorySummaryResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Only class in the controller's call graph allowed to touch domain-typed fields (Category,
 * CategoryStatus): keeps the Controller itself domain-free (HexagonalArchitectureTest).
 *
 * <p>{@code imageUrl} is always {@code null} until the image module builds the CDN URL (tracked
 * gap, dev-plan.md).
 */
@Component
public class CategoryWebMapper {

  public CreateCategoryCommand toCommand(CreateCategoryRequest request) {
    return new CreateCategoryCommand(
        request.name(), request.description(), request.imageId(), position(request.position()));
  }

  public UpdateCategoryCommand toCommand(UUID id, UpdateCategoryRequest request) {
    return new UpdateCategoryCommand(
        id, request.name(), request.description(), request.imageId(), position(request.position()));
  }

  public ChangeCategoryStatusCommand toCommand(UUID id, ChangeCategoryStatusRequest request) {
    return new ChangeCategoryStatusCommand(id, request.status());
  }

  public ReorderCategoriesCommand toCommand(ReorderCategoriesRequest request) {
    return new ReorderCategoriesCommand(request.categoryIds());
  }

  public DeleteCategoryCommand toDeleteCommand(UUID id) {
    return new DeleteCategoryCommand(id);
  }

  public GetCategoryQuery toQuery(String idOrSlug) {
    return new GetCategoryQuery(idOrSlug);
  }

  public GetCategoriesQuery toQuery(boolean includeInactive) {
    return new GetCategoriesQuery(includeInactive);
  }

  public GetCategoryImpactQuery toImpactQuery(UUID id) {
    return new GetCategoryImpactQuery(id);
  }

  public CategoryResponse toResponse(CategoryDto dto) {
    return new CategoryResponse(
        dto.id(),
        dto.name(),
        dto.slug(),
        dto.description(),
        dto.status(),
        null,
        dto.position(),
        dto.createdAt(),
        dto.updatedAt());
  }

  public CategorySummaryResponse toSummaryResponse(CategoryDto dto) {
    return new CategorySummaryResponse(dto.id(), dto.name(), dto.slug(), null, dto.position());
  }

  public CategoryImpactResponse toImpactResponse(CategoryImpact impact) {
    return new CategoryImpactResponse(
        impact.totalProducts(),
        impact.productsLosingVisibility().stream().map(this::toProductRefResponse).toList(),
        impact.productsLeftWithoutCategory().stream().map(this::toProductRefResponse).toList());
  }

  private CategoryProductRefResponse toProductRefResponse(CategoryProductRef ref) {
    return new CategoryProductRefResponse(ref.id(), ref.name(), ref.status());
  }

  private int position(Integer position) {
    return position == null ? 0 : position;
  }
}
