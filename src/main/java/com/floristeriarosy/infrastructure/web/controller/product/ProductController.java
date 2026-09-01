package com.floristeriarosy.infrastructure.web.controller.product;

import com.floristeriarosy.application.product.port.in.ChangeInventoryModeUseCase;
import com.floristeriarosy.application.product.port.in.ChangeProductStatusUseCase;
import com.floristeriarosy.application.product.port.in.CreateProductUseCase;
import com.floristeriarosy.application.product.port.in.DeleteProductUseCase;
import com.floristeriarosy.application.product.port.in.GetProductDeletionImpactUseCase;
import com.floristeriarosy.application.product.port.in.GetProductExtrasUseCase;
import com.floristeriarosy.application.product.port.in.GetProductUseCase;
import com.floristeriarosy.application.product.port.in.UpdateProductCategoriesUseCase;
import com.floristeriarosy.application.product.port.in.UpdateProductExtrasUseCase;
import com.floristeriarosy.application.product.port.in.UpdateProductImagesUseCase;
import com.floristeriarosy.application.product.port.in.UpdateProductUseCase;
import com.floristeriarosy.infrastructure.web.mapper.product.ProductWebMapper;
import com.floristeriarosy.infrastructure.web.request.product.ChangeInventoryModeRequest;
import com.floristeriarosy.infrastructure.web.request.product.ChangeProductStatusRequest;
import com.floristeriarosy.infrastructure.web.request.product.CreateProductRequest;
import com.floristeriarosy.infrastructure.web.request.product.UpdateProductCategoriesRequest;
import com.floristeriarosy.infrastructure.web.request.product.UpdateProductExtrasRequest;
import com.floristeriarosy.infrastructure.web.request.product.UpdateProductImagesRequest;
import com.floristeriarosy.infrastructure.web.request.product.UpdateProductRequest;
import com.floristeriarosy.infrastructure.web.response.product.ProductDeletionImpactResponse;
import com.floristeriarosy.infrastructure.web.response.product.ProductResponse;
import com.floristeriarosy.infrastructure.web.response.product.ProductSummaryResponse;
import com.floristeriarosy.shared.util.LogSanitizer;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.owasp.encoder.Encode;
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

/**
 * REST API for {@code /api/v1/products} (product.md, section 4) — core CRUD, associations and
 * inventory mode. Search, autocomplete and the admin listing are {@code
 * infrastructure.web.controller.product.ProductSearchController}, a later slice.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductController.class);

  private final CreateProductUseCase createProductUseCase;
  private final UpdateProductUseCase updateProductUseCase;
  private final ChangeProductStatusUseCase changeProductStatusUseCase;
  private final UpdateProductCategoriesUseCase updateProductCategoriesUseCase;
  private final UpdateProductImagesUseCase updateProductImagesUseCase;
  private final UpdateProductExtrasUseCase updateProductExtrasUseCase;
  private final ChangeInventoryModeUseCase changeInventoryModeUseCase;
  private final DeleteProductUseCase deleteProductUseCase;
  private final GetProductUseCase getProductUseCase;
  private final GetProductExtrasUseCase getProductExtrasUseCase;
  private final GetProductDeletionImpactUseCase getProductDeletionImpactUseCase;
  private final ProductWebMapper mapper;

  /**
   * @param createProductUseCase backs {@code POST /products}
   * @param updateProductUseCase backs {@code PUT /products/{id}}
   * @param changeProductStatusUseCase backs {@code PATCH /products/{id}/status}
   * @param updateProductCategoriesUseCase backs {@code PUT /products/{id}/categories}
   * @param updateProductImagesUseCase backs {@code PUT /products/{id}/images}
   * @param updateProductExtrasUseCase backs {@code PUT /products/{id}/extras}
   * @param changeInventoryModeUseCase backs {@code PATCH /products/{id}/inventory}
   * @param deleteProductUseCase backs {@code DELETE /products/{id}}
   * @param getProductUseCase backs {@code GET /products/{idOrSlug}}
   * @param getProductExtrasUseCase backs {@code GET /products/{id}/extras}
   * @param getProductDeletionImpactUseCase backs {@code GET /products/{id}/deletion-impact}
   * @param mapper translates Request/Response to/from Command/Query/Dto; the only class in this
   *     controller's call graph allowed to touch a domain type
   */
  public ProductController(
      CreateProductUseCase createProductUseCase,
      UpdateProductUseCase updateProductUseCase,
      ChangeProductStatusUseCase changeProductStatusUseCase,
      UpdateProductCategoriesUseCase updateProductCategoriesUseCase,
      UpdateProductImagesUseCase updateProductImagesUseCase,
      UpdateProductExtrasUseCase updateProductExtrasUseCase,
      ChangeInventoryModeUseCase changeInventoryModeUseCase,
      DeleteProductUseCase deleteProductUseCase,
      GetProductUseCase getProductUseCase,
      GetProductExtrasUseCase getProductExtrasUseCase,
      GetProductDeletionImpactUseCase getProductDeletionImpactUseCase,
      ProductWebMapper mapper) {
    this.createProductUseCase = createProductUseCase;
    this.updateProductUseCase = updateProductUseCase;
    this.changeProductStatusUseCase = changeProductStatusUseCase;
    this.updateProductCategoriesUseCase = updateProductCategoriesUseCase;
    this.updateProductImagesUseCase = updateProductImagesUseCase;
    this.updateProductExtrasUseCase = updateProductExtrasUseCase;
    this.changeInventoryModeUseCase = changeInventoryModeUseCase;
    this.deleteProductUseCase = deleteProductUseCase;
    this.getProductUseCase = getProductUseCase;
    this.getProductExtrasUseCase = getProductExtrasUseCase;
    this.getProductDeletionImpactUseCase = getProductDeletionImpactUseCase;
    this.mapper = mapper;
  }

  /**
   * {@code POST /products} (ADMIN — unenforced, dev-plan.md).
   *
   * @param request the product's fields, categories, gallery and optional initial stock
   * @return 201 with the created product
   */
  @PostMapping
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
    LOGGER.debug("POST /products name={}", LogSanitizer.sanitize(request.name()));
    ProductResponse response = mapper.toResponse(createProductUseCase.execute(mapper.toCommand(request)));
    LOGGER.debug("POST /products -> 201 id={}", response.id());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * {@code GET /products/{idOrSlug}} (public): 404, not 403, on a non-visible match (product.md,
   * section 4).
   *
   * @param idOrSlug a UUID or a slug
   * @return 200 with the matching product
   */
  @GetMapping("/{idOrSlug}")
  public ResponseEntity<ProductResponse> getOne(@PathVariable String idOrSlug) {
    // CodeQL's log-injection sanitizer recognition doesn't trace through LogSanitizer as a
    // helper method call, only a literal encode call on the tainted expression at the log site.
    LOGGER.debug("GET /products/{}", Encode.forJava(idOrSlug));
    ProductResponse response = mapper.toResponse(getProductUseCase.execute(mapper.toQuery(idOrSlug)));
    LOGGER.debug("GET /products/{} -> 200", Encode.forJava(idOrSlug));
    return ResponseEntity.ok(response);
  }

  /**
   * {@code GET /products/{id}/extras} (public): suggested extras, already filtered by visibility.
   *
   * @param id the product to look up
   * @return 200 with the visible suggested extras
   */
  @GetMapping("/{id}/extras")
  public ResponseEntity<List<ProductSummaryResponse>> getExtras(@PathVariable UUID id) {
    LOGGER.debug("GET /products/{}/extras", id);
    List<ProductSummaryResponse> response =
        getProductExtrasUseCase.execute(mapper.toExtrasQuery(id)).stream().map(mapper::toSummaryResponse).toList();
    LOGGER.debug("GET /products/{}/extras -> 200 count={}", id, response.size());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code GET /products/{id}/deletion-impact} (ADMIN — unenforced, dev-plan.md): read-only
   * preview.
   *
   * @param id the product to preview
   * @return 200 with the impact preview
   */
  @GetMapping("/{id}/deletion-impact")
  public ResponseEntity<ProductDeletionImpactResponse> getDeletionImpact(@PathVariable UUID id) {
    LOGGER.debug("GET /products/{}/deletion-impact", id);
    ProductDeletionImpactResponse response =
        mapper.toImpactResponse(getProductDeletionImpactUseCase.execute(mapper.toImpactQuery(id)));
    LOGGER.debug("GET /products/{}/deletion-impact -> 200 deletable={}", id, response.deletable());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code PUT /products/{id}} (ADMIN — unenforced, dev-plan.md): full replace of the product's
   * own fields.
   *
   * @param id the product to update
   * @param request the new field values
   * @return 200 with the updated product
   */
  @PutMapping("/{id}")
  public ResponseEntity<ProductResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
    LOGGER.debug("PUT /products/{} name={}", id, LogSanitizer.sanitize(request.name()));
    ProductResponse response = mapper.toResponse(updateProductUseCase.execute(mapper.toCommand(id, request)));
    LOGGER.debug("PUT /products/{} -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code PATCH /products/{id}/status} (ADMIN — unenforced, dev-plan.md).
   *
   * @param id the product to change
   * @param request the new status
   * @return 200 with the updated product
   */
  @PatchMapping("/{id}/status")
  public ResponseEntity<ProductResponse> changeStatus(
      @PathVariable UUID id, @Valid @RequestBody ChangeProductStatusRequest request) {
    LOGGER.debug("PATCH /products/{}/status status={}", id, request.status());
    ProductResponse response = mapper.toResponse(changeProductStatusUseCase.execute(mapper.toCommand(id, request)));
    LOGGER.debug("PATCH /products/{}/status -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code PUT /products/{id}/categories} (ADMIN — unenforced, dev-plan.md): full replace.
   *
   * @param id the product to update
   * @param request the complete new category set
   * @return 200 with the updated product
   */
  @PutMapping("/{id}/categories")
  public ResponseEntity<ProductResponse> updateCategories(
      @PathVariable UUID id, @Valid @RequestBody UpdateProductCategoriesRequest request) {
    LOGGER.debug("PUT /products/{}/categories categoryIds={}", id, request.categoryIds());
    ProductResponse response =
        mapper.toResponse(updateProductCategoriesUseCase.execute(mapper.toCommand(id, request)));
    LOGGER.debug("PUT /products/{}/categories -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code PUT /products/{id}/images} (ADMIN — unenforced, dev-plan.md): full replace, with order.
   *
   * @param id the product to update
   * @param request the complete new gallery
   * @return 200 with the updated product
   */
  @PutMapping("/{id}/images")
  public ResponseEntity<ProductResponse> updateImages(
      @PathVariable UUID id, @Valid @RequestBody UpdateProductImagesRequest request) {
    LOGGER.debug("PUT /products/{}/images count={}", id, request.images().size());
    ProductResponse response = mapper.toResponse(updateProductImagesUseCase.execute(mapper.toCommand(id, request)));
    LOGGER.debug("PUT /products/{}/images -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code PUT /products/{id}/extras} (ADMIN — unenforced, dev-plan.md): full replace, with order.
   *
   * @param id the product to update
   * @param request the complete new suggestion set
   * @return 200 with the updated product
   */
  @PutMapping("/{id}/extras")
  public ResponseEntity<ProductResponse> updateExtras(
      @PathVariable UUID id, @Valid @RequestBody UpdateProductExtrasRequest request) {
    LOGGER.debug("PUT /products/{}/extras extraProductIds={}", id, request.extraProductIds());
    ProductResponse response = mapper.toResponse(updateProductExtrasUseCase.execute(mapper.toCommand(id, request)));
    LOGGER.debug("PUT /products/{}/extras -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code PATCH /products/{id}/inventory} (ADMIN — unenforced, dev-plan.md).
   *
   * @param id the product to change
   * @param request the new inventory mode
   * @return 200 with the updated product
   */
  @PatchMapping("/{id}/inventory")
  public ResponseEntity<ProductResponse> changeInventoryMode(
      @PathVariable UUID id, @Valid @RequestBody ChangeInventoryModeRequest request) {
    LOGGER.debug("PATCH /products/{}/inventory managed={} stock={}", id, request.managed(), request.stock());
    ProductResponse response = mapper.toResponse(changeInventoryModeUseCase.execute(mapper.toCommand(id, request)));
    LOGGER.debug("PATCH /products/{}/inventory -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code DELETE /products/{id}} (ADMIN — unenforced, dev-plan.md): permanent removal.
   *
   * @param id the product to delete
   * @return 204, empty body
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    LOGGER.debug("DELETE /products/{}", id);
    deleteProductUseCase.execute(mapper.toDeleteCommand(id));
    LOGGER.debug("DELETE /products/{} -> 204", id);
    return ResponseEntity.noContent().build();
  }
}
