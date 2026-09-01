package com.floristeriarosy.infrastructure.web.controller.product;

import com.floristeriarosy.application.product.port.in.AutocompleteProductsUseCase;
import com.floristeriarosy.application.product.port.in.GetProductsUseCase;
import com.floristeriarosy.application.product.port.in.SearchProductsUseCase;
import com.floristeriarosy.infrastructure.web.mapper.product.ProductWebMapper;
import com.floristeriarosy.infrastructure.web.response.product.ProductPageResponse;
import com.floristeriarosy.infrastructure.web.response.product.ProductSuggestionResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the public search, autocomplete and admin listing under {@code
 * /api/v1/products} (product.md, section 4; ADR-006). Split from {@link ProductController} —
 * that one covers core CRUD, associations and inventory mode.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductSearchController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductSearchController.class);

  private final SearchProductsUseCase searchProductsUseCase;
  private final AutocompleteProductsUseCase autocompleteProductsUseCase;
  private final GetProductsUseCase getProductsUseCase;
  private final ProductWebMapper mapper;

  /**
   * @param searchProductsUseCase backs {@code GET /products}
   * @param autocompleteProductsUseCase backs {@code GET /products/suggestions}
   * @param getProductsUseCase backs {@code GET /products/all}
   * @param mapper translates Request/Response to/from Command/Query/Dto; the only class in this
   *     controller's call graph allowed to touch a domain type
   */
  public ProductSearchController(
      SearchProductsUseCase searchProductsUseCase,
      AutocompleteProductsUseCase autocompleteProductsUseCase,
      GetProductsUseCase getProductsUseCase,
      ProductWebMapper mapper) {
    this.searchProductsUseCase = searchProductsUseCase;
    this.autocompleteProductsUseCase = autocompleteProductsUseCase;
    this.getProductsUseCase = getProductsUseCase;
    this.mapper = mapper;
  }

  /**
   * {@code GET /products} (public): paginated, only visible products (product.md, section 3.3).
   * Every filter is optional and combinable; {@code attr.{key}} entries are read from {@code
   * allParams} since their key is dynamic.
   *
   * @param q free text, matched full-text (ADR-006), or {@code null}
   * @param category a category's id or slug, or {@code null}
   * @param minPrice minimum effective price, or {@code null}
   * @param maxPrice maximum effective price, or {@code null}
   * @param onSale whether to only return products with a currently active discount
   * @param page requested page, zero-based
   * @param size requested page size, capped
   * @param allParams every request query parameter, including {@code attr.{key}} entries
   * @return 200 with the matching visible products, paginated
   */
  @GetMapping
  public ResponseEntity<ProductPageResponse> search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) BigDecimal minPrice,
      @RequestParam(required = false) BigDecimal maxPrice,
      @RequestParam(required = false, defaultValue = "false") boolean onSale,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam Map<String, String> allParams) {
    LOGGER.debug(
        "GET /products q={} category={} page={} size={}",
        q == null ? null : Encode.forJava(q),
        category == null ? null : Encode.forJava(category),
        page,
        size);
    ProductPageResponse response =
        mapper.toPageResponse(
            searchProductsUseCase.execute(
                mapper.toSearchQuery(q, category, minPrice, maxPrice, onSale, allParams, page, size)));
    LOGGER.debug("GET /products -> 200 totalElements={}", response.totalElements());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code GET /products/suggestions} (public): trigram autocomplete, prefixes and typos
   * (ADR-006).
   *
   * @param q the text typed so far
   * @return 200 with the matching visible product names and slugs
   */
  @GetMapping("/suggestions")
  public ResponseEntity<List<ProductSuggestionResponse>> suggestions(@RequestParam String q) {
    LOGGER.debug("GET /products/suggestions q={}", Encode.forJava(q));
    List<ProductSuggestionResponse> response =
        autocompleteProductsUseCase.execute(mapper.toAutocompleteQuery(q)).stream()
            .map(mapper::toSuggestionResponse)
            .toList();
    LOGGER.debug("GET /products/suggestions -> 200 count={}", response.size());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code GET /products/all} (ADMIN — unenforced, dev-plan.md): every status, including {@code
   * INACTIVE} and {@code DISCONTINUED}, no visibility check.
   *
   * @param status only products with this status, or {@code null} for every status
   * @param withoutCategory whether to only return products with no category at all
   * @param isExtra only products with this {@code is_extra} flag, or {@code null} for both
   * @param page requested page, zero-based
   * @param size requested page size, capped
   * @return 200 with the matching products, paginated
   */
  @GetMapping("/all")
  public ResponseEntity<ProductPageResponse> getAll(
      @RequestParam(required = false) String status,
      @RequestParam(required = false, defaultValue = "false") boolean withoutCategory,
      @RequestParam(required = false) Boolean isExtra,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size) {
    LOGGER.debug(
        "GET /products/all status={} withoutCategory={} isExtra={} page={} size={}",
        status == null ? null : Encode.forJava(status),
        withoutCategory,
        isExtra,
        page,
        size);
    ProductPageResponse response =
        mapper.toPageResponse(
            getProductsUseCase.execute(mapper.toGetProductsQuery(status, withoutCategory, isExtra, page, size)));
    LOGGER.debug("GET /products/all -> 200 totalElements={}", response.totalElements());
    return ResponseEntity.ok(response);
  }
}
