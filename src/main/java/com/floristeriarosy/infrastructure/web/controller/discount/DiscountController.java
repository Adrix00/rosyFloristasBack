package com.floristeriarosy.infrastructure.web.controller.discount;

import com.floristeriarosy.application.discount.port.in.DeleteDiscountUseCase;
import com.floristeriarosy.application.discount.port.in.EndDiscountUseCase;
import com.floristeriarosy.application.discount.port.in.UpdateDiscountUseCase;
import com.floristeriarosy.infrastructure.web.mapper.discount.DiscountWebMapper;
import com.floristeriarosy.infrastructure.web.request.discount.UpdateDiscountRequest;
import com.floristeriarosy.infrastructure.web.response.discount.DiscountResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code /api/v1/discounts/{id}} (product-discounts.md, section 4): operations
 * addressed by the discount's own id. {@code POST /discounts/{id}/end} is an action endpoint, not
 * a {@code PATCH .../status}: there is no status field to change, only a date being brought
 * forward to now (product-discounts.md, section 4).
 */
@RestController
@RequestMapping("/api/v1/discounts")
public class DiscountController {

  private static final Logger LOGGER = LoggerFactory.getLogger(DiscountController.class);

  private final UpdateDiscountUseCase updateDiscountUseCase;
  private final EndDiscountUseCase endDiscountUseCase;
  private final DeleteDiscountUseCase deleteDiscountUseCase;
  private final DiscountWebMapper mapper;

  /**
   * @param updateDiscountUseCase backs {@code PUT /discounts/{id}}
   * @param endDiscountUseCase backs {@code POST /discounts/{id}/end}
   * @param deleteDiscountUseCase backs {@code DELETE /discounts/{id}}
   * @param mapper translates Request/Response to/from Command/Dto; the only class in this
   *     controller's call graph allowed to touch a domain type
   */
  public DiscountController(
      UpdateDiscountUseCase updateDiscountUseCase,
      EndDiscountUseCase endDiscountUseCase,
      DeleteDiscountUseCase deleteDiscountUseCase,
      DiscountWebMapper mapper) {
    this.updateDiscountUseCase = updateDiscountUseCase;
    this.endDiscountUseCase = endDiscountUseCase;
    this.deleteDiscountUseCase = deleteDiscountUseCase;
    this.mapper = mapper;
  }

  /**
   * {@code PUT /discounts/{id}} (ADMIN — unenforced, dev-plan.md): partial edit, per the
   * editability rules of product-discounts.md, section 3.3.
   *
   * @param id the discount to update
   * @param request the requested new field values
   * @return 200 with the updated discount
   */
  @PutMapping("/{id}")
  public ResponseEntity<DiscountResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateDiscountRequest request) {
    LOGGER.debug("PUT /discounts/{}", id);
    DiscountResponse response = mapper.toResponse(updateDiscountUseCase.execute(mapper.toCommand(id, request)));
    LOGGER.debug("PUT /discounts/{} -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code POST /discounts/{id}/end} (ADMIN — unenforced, dev-plan.md): closes the discount now.
   *
   * @param id the discount to close
   * @return 200 with the closed discount
   */
  @PostMapping("/{id}/end")
  public ResponseEntity<DiscountResponse> end(@PathVariable UUID id) {
    LOGGER.debug("POST /discounts/{}/end", id);
    DiscountResponse response = mapper.toResponse(endDiscountUseCase.execute(mapper.toEndCommand(id)));
    LOGGER.debug("POST /discounts/{}/end -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code DELETE /discounts/{id}} (ADMIN — unenforced, dev-plan.md): permanent removal, only if
   * the discount has not started yet.
   *
   * @param id the discount to delete
   * @return 204, empty body
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    LOGGER.debug("DELETE /discounts/{}", id);
    deleteDiscountUseCase.execute(mapper.toDeleteCommand(id));
    LOGGER.debug("DELETE /discounts/{} -> 204", id);
    return ResponseEntity.noContent().build();
  }
}
