package com.floristeriarosy.infrastructure.web.controller.discount;

import com.floristeriarosy.application.discount.port.in.CreateDiscountUseCase;
import com.floristeriarosy.application.discount.port.in.GetProductDiscountsUseCase;
import com.floristeriarosy.infrastructure.web.mapper.discount.DiscountWebMapper;
import com.floristeriarosy.infrastructure.web.request.discount.CreateDiscountRequest;
import com.floristeriarosy.infrastructure.web.response.discount.DiscountResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code /api/v1/products/{id}/discounts} (product-discounts.md, section 4): the
 * discount-as-child-of-product operations, creation and full history. Operations addressed by the
 * discount's own id ({@code PUT}, {@code end}, {@code DELETE}) are {@link DiscountController} —
 * same split this codebase already uses between {@code ProductController} and {@code
 * ProductSearchController} for a single aggregate's endpoints grouped by concern; here the split
 * follows the URL's resource root instead, since {@code /products/{id}/discounts} and {@code
 * /discounts/{id}} are different resource roots.
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/discounts")
public class ProductDiscountController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductDiscountController.class);

  private final CreateDiscountUseCase createDiscountUseCase;
  private final GetProductDiscountsUseCase getProductDiscountsUseCase;
  private final DiscountWebMapper mapper;

  /**
   * @param createDiscountUseCase backs {@code POST /products/{id}/discounts}
   * @param getProductDiscountsUseCase backs {@code GET /products/{id}/discounts}
   * @param mapper translates Request/Response to/from Command/Query/Dto; the only class in this
   *     controller's call graph allowed to touch a domain type
   */
  public ProductDiscountController(
      CreateDiscountUseCase createDiscountUseCase,
      GetProductDiscountsUseCase getProductDiscountsUseCase,
      DiscountWebMapper mapper) {
    this.createDiscountUseCase = createDiscountUseCase;
    this.getProductDiscountsUseCase = getProductDiscountsUseCase;
    this.mapper = mapper;
  }

  /**
   * {@code POST /products/{id}/discounts} (ADMIN — unenforced, dev-plan.md).
   *
   * @param productId the product to discount
   * @param request the promotion's fields
   * @return 201 with the created discount
   */
  @PostMapping
  public ResponseEntity<DiscountResponse> create(
      @PathVariable UUID productId, @Valid @RequestBody CreateDiscountRequest request) {
    LOGGER.debug("POST /products/{}/discounts salePrice={}", productId, request.salePrice());
    DiscountResponse response =
        mapper.toResponse(createDiscountUseCase.execute(mapper.toCommand(productId, request)));
    LOGGER.debug("POST /products/{}/discounts -> 201 id={}", productId, response.id());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * {@code GET /products/{id}/discounts} (ADMIN — unenforced, dev-plan.md): complete discount
   * history, past and present.
   *
   * @param productId the product whose discount history to list
   * @return 200 with every discount ever created for the product
   */
  @GetMapping
  public ResponseEntity<List<DiscountResponse>> getAll(@PathVariable UUID productId) {
    LOGGER.debug("GET /products/{}/discounts", productId);
    List<DiscountResponse> response =
        getProductDiscountsUseCase.execute(mapper.toQuery(productId)).stream().map(mapper::toResponse).toList();
    LOGGER.debug("GET /products/{}/discounts -> 200 count={}", productId, response.size());
    return ResponseEntity.ok(response);
  }
}
