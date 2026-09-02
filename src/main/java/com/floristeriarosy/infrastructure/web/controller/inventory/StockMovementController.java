package com.floristeriarosy.infrastructure.web.controller.inventory;

import com.floristeriarosy.application.inventory.port.in.GetStockMovementsUseCase;
import com.floristeriarosy.application.inventory.port.in.RegisterAdjustmentUseCase;
import com.floristeriarosy.application.inventory.port.in.RegisterWasteUseCase;
import com.floristeriarosy.infrastructure.web.mapper.inventory.StockMovementWebMapper;
import com.floristeriarosy.infrastructure.web.request.inventory.RegisterAdjustmentRequest;
import com.floristeriarosy.infrastructure.web.request.inventory.RegisterWasteRequest;
import com.floristeriarosy.infrastructure.web.response.inventory.StockMovementPageResponse;
import com.floristeriarosy.infrastructure.web.response.inventory.StockMovementResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code /api/v1/products/{id}/stock-movements} (inventory.md, section 4): a
 * product's movement history, plus the two actions an administrator triggers directly ({@code
 * WASTE}, {@code ADJUSTMENT}). {@code INITIAL} lives under {@code PATCH /products/{id}/inventory}
 * (product.md); {@code PURCHASE} and {@code SALE} have no endpoint here at all (inventory.md,
 * section 4).
 */
@RestController
@RequestMapping("/api/v1/products/{id}/stock-movements")
public class StockMovementController {

  private static final Logger LOGGER = LoggerFactory.getLogger(StockMovementController.class);

  private final GetStockMovementsUseCase getStockMovementsUseCase;
  private final RegisterWasteUseCase registerWasteUseCase;
  private final RegisterAdjustmentUseCase registerAdjustmentUseCase;
  private final StockMovementWebMapper mapper;

  /**
   * @param getStockMovementsUseCase backs {@code GET /products/{id}/stock-movements}
   * @param registerWasteUseCase backs {@code POST /products/{id}/stock-movements/waste}
   * @param registerAdjustmentUseCase backs {@code POST /products/{id}/stock-movements/adjustment}
   * @param mapper translates Request/Response to/from Command/Query/Dto; the only class in this
   *     controller's call graph allowed to touch a domain type
   */
  public StockMovementController(
      GetStockMovementsUseCase getStockMovementsUseCase,
      RegisterWasteUseCase registerWasteUseCase,
      RegisterAdjustmentUseCase registerAdjustmentUseCase,
      StockMovementWebMapper mapper) {
    this.getStockMovementsUseCase = getStockMovementsUseCase;
    this.registerWasteUseCase = registerWasteUseCase;
    this.registerAdjustmentUseCase = registerAdjustmentUseCase;
    this.mapper = mapper;
  }

  /**
   * {@code GET /products/{id}/stock-movements} (ADMIN — unenforced, dev-plan.md): complete
   * movement history, paginated.
   *
   * @param id the product whose history to list
   * @param page requested page, zero-based
   * @param size requested page size, capped
   * @return 200 with the matching movements, paginated
   */
  @GetMapping
  public ResponseEntity<StockMovementPageResponse> getHistory(
      @PathVariable UUID id,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size) {
    LOGGER.debug("GET /products/{}/stock-movements page={} size={}", id, page, size);
    StockMovementPageResponse response =
        mapper.toPageResponse(getStockMovementsUseCase.execute(mapper.toQuery(id, page, size)));
    LOGGER.debug("GET /products/{}/stock-movements -> 200 totalElements={}", id, response.totalElements());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code POST /products/{id}/stock-movements/waste} (ADMIN — unenforced, dev-plan.md): registers
   * an explicit write-off (inventory.md, section 3.5).
   *
   * @param id the product being written off
   * @param request the wasted quantity and required note
   * @return 201 with the recorded movement
   */
  @PostMapping("/waste")
  public ResponseEntity<StockMovementResponse> registerWaste(
      @PathVariable UUID id, @Valid @RequestBody RegisterWasteRequest request) {
    LOGGER.debug("POST /products/{}/stock-movements/waste quantity={}", id, request.quantity());
    StockMovementResponse response = mapper.toResponse(registerWasteUseCase.execute(mapper.toCommand(id, request)));
    LOGGER.debug("POST /products/{}/stock-movements/waste -> 201 id={}", id, response.id());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * {@code POST /products/{id}/stock-movements/adjustment} (ADMIN — unenforced, dev-plan.md):
   * registers a manual correction (inventory.md, section 3.6).
   *
   * @param id the product being corrected
   * @param request the signed delta and required note
   * @return 201 with the recorded movement
   */
  @PostMapping("/adjustment")
  public ResponseEntity<StockMovementResponse> registerAdjustment(
      @PathVariable UUID id, @Valid @RequestBody RegisterAdjustmentRequest request) {
    LOGGER.debug("POST /products/{}/stock-movements/adjustment quantity={}", id, request.quantity());
    StockMovementResponse response =
        mapper.toResponse(registerAdjustmentUseCase.execute(mapper.toCommand(id, request)));
    LOGGER.debug("POST /products/{}/stock-movements/adjustment -> 201 id={}", id, response.id());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
