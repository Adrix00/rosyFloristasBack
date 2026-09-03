package com.floristeriarosy.infrastructure.web.mapper.category;

import com.floristeriarosy.application.category.command.ChangeCategoryStatusCommand;
import com.floristeriarosy.application.category.command.CreateCategoryCommand;
import com.floristeriarosy.application.category.command.DeleteCategoryCommand;
import com.floristeriarosy.application.category.command.ReorderCategoriesCommand;
import com.floristeriarosy.application.category.command.UpdateCategoryCommand;
import com.floristeriarosy.application.category.dto.CategoryDto;
import com.floristeriarosy.application.category.dto.CategoryImpact;
import com.floristeriarosy.application.category.dto.CategoryProductRef;
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
 * CategoryStatus): keeps the Controller itself domain-free (HexagonalArchitectureTest). Pure 1:1
 * field mapping, not logged (see CLAUDE.md, Logging) — every call is already visible in the
 * Controller's own entry/exit log.
 *
 * <p>{@code imageUrl} is always {@code null} until the image module builds the CDN URL (tracked
 * gap, dev-plan.md).
 */
@Component
public class CategoryWebMapper {

  /**
   * @param request the create request
   * @return the command to hand to {@code CreateCategoryUseCase}
   */
  public CreateCategoryCommand toCommand(CreateCategoryRequest request) {
    return new CreateCategoryCommand(
        request.name(), request.description(), request.imageId(), position(request.position()));
  }

  /**
   * @param id the category to update, from the path
   * @param request the new field values
   * @return the command to hand to {@code UpdateCategoryUseCase}
   */
  public UpdateCategoryCommand toCommand(UUID id, UpdateCategoryRequest request) {
    return new UpdateCategoryCommand(
        id, request.name(), request.description(), request.imageId(), position(request.position()));
  }

  /**
   * @param id the category to change, from the path
   * @param request the new status
   * @return the command to hand to {@code ChangeCategoryStatusUseCase}
   */
  public ChangeCategoryStatusCommand toCommand(UUID id, ChangeCategoryStatusRequest request) {
    return new ChangeCategoryStatusCommand(id, request.status());
  }

  /**
   * @param request every category id, in its new order
   * @return the command to hand to {@code ReorderCategoriesUseCase}
   */
  public ReorderCategoriesCommand toCommand(ReorderCategoriesRequest request) {
    return new ReorderCategoriesCommand(request.categoryIds());
  }

  /**
   * @param id the category to delete, from the path
   * @return the command to hand to {@code DeleteCategoryUseCase}
   */
  public DeleteCategoryCommand toDeleteCommand(UUID id) {
    return new DeleteCategoryCommand(id);
  }

  /**
   * @param idOrSlug the raw path segment
   * @return the query to hand to {@code GetCategoryUseCase}
   */
  public GetCategoryQuery toQuery(String idOrSlug) {
    return new GetCategoryQuery(idOrSlug);
  }

  /**
   * @param id the category to preview, from the path
   * @return the query to hand to {@code GetCategoryImpactUseCase}
   */
  public GetCategoryImpactQuery toImpactQuery(UUID id) {
    return new GetCategoryImpactQuery(id);
  }

  /**
   * @param dto the category to expose
   * @return its full API representation
   */
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

  /**
   * @param dto the category to expose
   * @return its summary representation, for listings
   */
  public CategorySummaryResponse toSummaryResponse(CategoryDto dto) {
    return new CategorySummaryResponse(dto.id(), dto.name(), dto.slug(), null, dto.position());
  }

  /**
   * @param impact the computed impact preview
   * @return its API representation
   */
  public CategoryImpactResponse toImpactResponse(CategoryImpact impact) {
    return new CategoryImpactResponse(
        impact.totalProducts(),
        impact.productsLosingVisibility().stream().map(this::toProductRefResponse).toList(),
        impact.productsLeftWithoutCategory().stream().map(this::toProductRefResponse).toList());
  }

  /**
   * @param ref one product referenced by an impact preview
   * @return its API representation
   */
  private CategoryProductRefResponse toProductRefResponse(CategoryProductRef ref) {
    return new CategoryProductRefResponse(ref.id(), ref.name(), ref.status());
  }

  /**
   * @param position the request's optional position field
   * @return {@code position}, or {@code 0} if absent (category.md, section 5)
   */
  private int position(Integer position) {
    return position == null ? 0 : position;
  }
}
