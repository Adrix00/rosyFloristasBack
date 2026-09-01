package com.floristeriarosy.infrastructure.web.mapper.product;

import com.floristeriarosy.application.product.command.ChangeInventoryModeCommand;
import com.floristeriarosy.application.product.command.ChangeProductStatusCommand;
import com.floristeriarosy.application.product.command.CreateProductCommand;
import com.floristeriarosy.application.product.command.DeleteProductCommand;
import com.floristeriarosy.application.product.command.UpdateProductCategoriesCommand;
import com.floristeriarosy.application.product.command.UpdateProductCommand;
import com.floristeriarosy.application.product.command.UpdateProductExtrasCommand;
import com.floristeriarosy.application.product.command.UpdateProductImagesCommand;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.application.product.dto.ProductDeletionImpact;
import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.dto.ProductImageAssignment;
import com.floristeriarosy.application.product.dto.ProductImageRef;
import com.floristeriarosy.application.product.dto.ProductSuggestionDto;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.query.AutocompleteProductsQuery;
import com.floristeriarosy.application.product.query.GetProductDeletionImpactQuery;
import com.floristeriarosy.application.product.query.GetProductExtrasQuery;
import com.floristeriarosy.application.product.query.GetProductQuery;
import com.floristeriarosy.application.product.query.GetProductsQuery;
import com.floristeriarosy.application.product.query.SearchProductsQuery;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.infrastructure.web.request.product.ChangeInventoryModeRequest;
import com.floristeriarosy.infrastructure.web.request.product.ChangeProductStatusRequest;
import com.floristeriarosy.infrastructure.web.request.product.CreateProductRequest;
import com.floristeriarosy.infrastructure.web.request.product.UpdateProductCategoriesRequest;
import com.floristeriarosy.infrastructure.web.request.product.UpdateProductExtrasRequest;
import com.floristeriarosy.infrastructure.web.request.product.UpdateProductImagesRequest;
import com.floristeriarosy.infrastructure.web.request.product.UpdateProductRequest;
import com.floristeriarosy.infrastructure.web.response.product.ProductCategoryRefResponse;
import com.floristeriarosy.infrastructure.web.response.product.ProductDeletionImpactResponse;
import com.floristeriarosy.infrastructure.web.response.product.ProductImageRefResponse;
import com.floristeriarosy.infrastructure.web.response.product.ProductPageResponse;
import com.floristeriarosy.infrastructure.web.response.product.ProductResponse;
import com.floristeriarosy.infrastructure.web.response.product.ProductSuggestionResponse;
import com.floristeriarosy.infrastructure.web.response.product.ProductSummaryResponse;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Only class in the controller's call graph allowed to touch domain-typed fields ({@code
 * ProductStatus}): keeps the Controller itself domain-free (HexagonalArchitectureTest). Pure 1:1
 * field mapping, not logged (see CLAUDE.md, Logging) — every call is already visible in the
 * Controller's own entry/exit log.
 */
@Component
public class ProductWebMapper {

  /** Upper bound of {@code size} on a paginated listing (product.md, section 4). */
  private static final int MAX_PAGE_SIZE = 100;

  /** Every {@code attr.{key}} query parameter carries this prefix (product.md, section 4). */
  private static final String ATTR_PREFIX = "attr.";

  /**
   * @param request the create request
   * @return the command to hand to {@code CreateProductUseCase}
   */
  public CreateProductCommand toCommand(CreateProductRequest request) {
    return new CreateProductCommand(
        request.name(),
        request.description(),
        request.price(),
        request.categoryIds(),
        isExtra(request.isExtra()),
        attributes(request.attributes()),
        request.imageIds() == null ? List.of() : request.imageIds(),
        request.initialStock());
  }

  /**
   * @param id the product to update, from the path
   * @param request the new field values
   * @return the command to hand to {@code UpdateProductUseCase}
   */
  public UpdateProductCommand toCommand(UUID id, UpdateProductRequest request) {
    return new UpdateProductCommand(
        id,
        request.name(),
        request.description(),
        request.price(),
        isExtra(request.isExtra()),
        attributes(request.attributes()));
  }

  /**
   * @param id the product to change, from the path
   * @param request the new status
   * @return the command to hand to {@code ChangeProductStatusUseCase}
   */
  public ChangeProductStatusCommand toCommand(UUID id, ChangeProductStatusRequest request) {
    return new ChangeProductStatusCommand(id, request.status());
  }

  /**
   * @param id the product to update, from the path
   * @param request the complete new category set
   * @return the command to hand to {@code UpdateProductCategoriesUseCase}
   */
  public UpdateProductCategoriesCommand toCommand(UUID id, UpdateProductCategoriesRequest request) {
    return new UpdateProductCategoriesCommand(id, request.categoryIds());
  }

  /**
   * @param id the product to update, from the path
   * @param request the complete new gallery
   * @return the command to hand to {@code UpdateProductImagesUseCase}
   */
  public UpdateProductImagesCommand toCommand(UUID id, UpdateProductImagesRequest request) {
    return new UpdateProductImagesCommand(
        id, request.images().stream().map(item -> new ProductImageAssignment(item.imageId(), item.altText())).toList());
  }

  /**
   * @param id the product to update, from the path
   * @param request the complete new suggestion set
   * @return the command to hand to {@code UpdateProductExtrasUseCase}
   */
  public UpdateProductExtrasCommand toCommand(UUID id, UpdateProductExtrasRequest request) {
    return new UpdateProductExtrasCommand(id, request.extraProductIds());
  }

  /**
   * @param id the product to change, from the path
   * @param request the new inventory mode
   * @return the command to hand to {@code ChangeInventoryModeUseCase}
   */
  public ChangeInventoryModeCommand toCommand(UUID id, ChangeInventoryModeRequest request) {
    return new ChangeInventoryModeCommand(
        id, Boolean.TRUE.equals(request.managed()), request.stock(), request.lowStockThreshold(), request.note());
  }

  /**
   * @param id the product to delete, from the path
   * @return the command to hand to {@code DeleteProductUseCase}
   */
  public DeleteProductCommand toDeleteCommand(UUID id) {
    return new DeleteProductCommand(id);
  }

  /**
   * @param idOrSlug the raw path segment
   * @return the query to hand to {@code GetProductUseCase}
   */
  public GetProductQuery toQuery(String idOrSlug) {
    return new GetProductQuery(idOrSlug);
  }

  /**
   * @param id the product to look up, from the path
   * @return the query to hand to {@code GetProductExtrasUseCase}
   */
  public GetProductExtrasQuery toExtrasQuery(UUID id) {
    return new GetProductExtrasQuery(id);
  }

  /**
   * @param id the product to preview, from the path
   * @return the query to hand to {@code GetProductDeletionImpactUseCase}
   */
  public GetProductDeletionImpactQuery toImpactQuery(UUID id) {
    return new GetProductDeletionImpactQuery(id);
  }

  /**
   * @param dto the product to expose
   * @return its full API representation
   */
  public ProductResponse toResponse(ProductDto dto) {
    return new ProductResponse(
        dto.id(),
        dto.name(),
        dto.slug(),
        dto.description(),
        dto.price(),
        dto.effectivePrice(),
        dto.onSale(),
        dto.status(),
        dto.isExtra(),
        dto.attributes(),
        dto.categories().stream().map(this::toCategoryRefResponse).toList(),
        dto.images().stream().map(this::toImageRefResponse).toList(),
        dto.stock(),
        dto.inventoryManaged(),
        dto.createdAt(),
        dto.updatedAt());
  }

  /**
   * @param dto the product to expose
   * @return its summary representation, for listings
   */
  public ProductSummaryResponse toSummaryResponse(ProductSummaryDto dto) {
    return new ProductSummaryResponse(
        dto.id(), dto.name(), dto.slug(), dto.price(), dto.effectivePrice(), dto.onSale(), dto.mainImageUrl());
  }

  /**
   * @param impact the computed impact preview
   * @return its API representation
   */
  public ProductDeletionImpactResponse toImpactResponse(ProductDeletionImpact impact) {
    return new ProductDeletionImpactResponse(
        impact.deletable(),
        impact.blockedBy(),
        impact.orderCount(),
        impact.stockMovementCount(),
        impact.purchaseCount());
  }

  /**
   * @param q free text, or {@code null}
   * @param category a category's id or slug, or {@code null}
   * @param minPrice minimum effective price, or {@code null}
   * @param maxPrice maximum effective price, or {@code null}
   * @param onSale whether to only return products with a currently active discount
   * @param allParams every request query parameter, used to extract {@code attr.{key}} entries
   * @param page requested page, clamped to {@code >= 0}
   * @param size requested page size, clamped to {@code [1, MAX_PAGE_SIZE]}
   * @return the query to hand to {@code SearchProductsUseCase}
   */
  public SearchProductsQuery toSearchQuery(
      String q,
      String category,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      boolean onSale,
      Map<String, String> allParams,
      int page,
      int size) {
    return new SearchProductsQuery(
        q, category, minPrice, maxPrice, onSale, attributeFilters(allParams), clampPage(page), clampSize(size));
  }

  /**
   * @param q the text typed so far
   * @return the query to hand to {@code AutocompleteProductsUseCase}
   */
  public AutocompleteProductsQuery toAutocompleteQuery(String q) {
    return new AutocompleteProductsQuery(q);
  }

  /**
   * @param status raw {@code status} query parameter, or {@code null}; an unparseable value is
   *     treated as absent rather than rejected — this is an optional admin filter, not a
   *     validated request body field
   * @param withoutCategory whether to only return products with no category at all
   * @param isExtra only products with this {@code is_extra} flag, or {@code null} for both
   * @param page requested page, clamped to {@code >= 0}
   * @param size requested page size, clamped to {@code [1, MAX_PAGE_SIZE]}
   * @return the query to hand to {@code GetProductsUseCase}
   */
  public GetProductsQuery toGetProductsQuery(
      String status, boolean withoutCategory, Boolean isExtra, int page, int size) {
    return new GetProductsQuery(parseStatus(status), withoutCategory, isExtra, clampPage(page), clampSize(size));
  }

  /**
   * @param dto the suggestion to expose
   * @return its API representation
   */
  public ProductSuggestionResponse toSuggestionResponse(ProductSuggestionDto dto) {
    return new ProductSuggestionResponse(dto.name(), dto.slug());
  }

  /**
   * @param result the paginated result to expose
   * @return its API representation
   */
  public ProductPageResponse toPageResponse(PageResult<ProductSummaryDto> result) {
    return new ProductPageResponse(
        result.items().stream().map(this::toSummaryResponse).toList(),
        result.totalElements(),
        result.page(),
        result.size());
  }

  /**
   * @param allParams every request query parameter
   * @return the entries prefixed with {@code attr.}, keyed without the prefix
   */
  private Map<String, String> attributeFilters(Map<String, String> allParams) {
    Map<String, String> filters = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : allParams.entrySet()) {
      if (entry.getKey().startsWith(ATTR_PREFIX)) {
        filters.put(entry.getKey().substring(ATTR_PREFIX.length()), entry.getValue());
      }
    }
    return filters;
  }

  /**
   * @param page the raw requested page
   * @return {@code page}, never negative
   */
  private int clampPage(int page) {
    return Math.max(page, 0);
  }

  /**
   * @param size the raw requested page size
   * @return {@code size}, clamped to {@code [1, MAX_PAGE_SIZE]}
   */
  private int clampSize(int size) {
    return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
  }

  /**
   * @param status the raw {@code status} query parameter
   * @return {@code status} parsed, or {@code null} if absent or unparseable
   */
  private ProductStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return ProductStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unparseable) {
      return null;
    }
  }

  /**
   * @param ref one category a product belongs to
   * @return its API representation
   */
  private ProductCategoryRefResponse toCategoryRefResponse(ProductCategoryRef ref) {
    return new ProductCategoryRefResponse(ref.id(), ref.name(), ref.slug());
  }

  /**
   * @param ref one image in a product's gallery
   * @return its API representation
   */
  private ProductImageRefResponse toImageRefResponse(ProductImageRef ref) {
    return new ProductImageRefResponse(ref.id(), ref.url(), ref.altText(), ref.position());
  }

  /**
   * @param isExtra the request's optional {@code isExtra} field
   * @return {@code isExtra}, or {@code false} if absent
   */
  private boolean isExtra(Boolean isExtra) {
    return Boolean.TRUE.equals(isExtra);
  }

  /**
   * @param attributes the request's optional attributes field
   * @return {@code attributes}, or an empty map if absent
   */
  private Map<String, Object> attributes(Map<String, Object> attributes) {
    return attributes == null ? Collections.emptyMap() : attributes;
  }
}
