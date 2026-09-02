package com.floristeriarosy.infrastructure.web.controller.inventory;

import com.floristeriarosy.application.inventory.port.in.DismissInventoryAlertUseCase;
import com.floristeriarosy.application.inventory.port.in.GetInventoryAlertsUseCase;
import com.floristeriarosy.application.inventory.port.in.ResolveInventoryAlertUseCase;
import com.floristeriarosy.infrastructure.web.mapper.inventory.InventoryAlertWebMapper;
import com.floristeriarosy.infrastructure.web.request.inventory.DismissAlertRequest;
import com.floristeriarosy.infrastructure.web.request.inventory.ResolveAlertRequest;
import com.floristeriarosy.infrastructure.web.response.inventory.InventoryAlertPageResponse;
import com.floristeriarosy.infrastructure.web.response.inventory.InventoryAlertResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code /api/v1/inventory/alerts} (inventory.md, section 4): the filtered, paginated
 * alert history, plus the two terminal actions an administrator takes on an open alert.
 */
@RestController
@RequestMapping("/api/v1/inventory/alerts")
public class InventoryAlertController {

  private static final Logger LOGGER = LoggerFactory.getLogger(InventoryAlertController.class);

  private final GetInventoryAlertsUseCase getInventoryAlertsUseCase;
  private final ResolveInventoryAlertUseCase resolveInventoryAlertUseCase;
  private final DismissInventoryAlertUseCase dismissInventoryAlertUseCase;
  private final InventoryAlertWebMapper mapper;

  /**
   * @param getInventoryAlertsUseCase backs {@code GET /inventory/alerts}
   * @param resolveInventoryAlertUseCase backs {@code PATCH /inventory/alerts/{id}/resolve}
   * @param dismissInventoryAlertUseCase backs {@code PATCH /inventory/alerts/{id}/dismiss}
   * @param mapper translates Request/Response to/from Command/Query/Dto; the only class in this
   *     controller's call graph allowed to touch a domain type
   */
  public InventoryAlertController(
      GetInventoryAlertsUseCase getInventoryAlertsUseCase,
      ResolveInventoryAlertUseCase resolveInventoryAlertUseCase,
      DismissInventoryAlertUseCase dismissInventoryAlertUseCase,
      InventoryAlertWebMapper mapper) {
    this.getInventoryAlertsUseCase = getInventoryAlertsUseCase;
    this.resolveInventoryAlertUseCase = resolveInventoryAlertUseCase;
    this.dismissInventoryAlertUseCase = dismissInventoryAlertUseCase;
    this.mapper = mapper;
  }

  /**
   * {@code GET /inventory/alerts} (ADMIN — unenforced, dev-plan.md): filtered, paginated history.
   *
   * @param type only alerts of this type, or {@code null} for every type
   * @param status only alerts with this status, or {@code null} for every status
   * @param productId only alerts for this product, or {@code null} for every product
   * @param page requested page, zero-based
   * @param size requested page size, capped
   * @return 200 with the matching alerts, paginated
   */
  @GetMapping
  public ResponseEntity<InventoryAlertPageResponse> getAll(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID productId,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size) {
    LOGGER.debug(
        "GET /inventory/alerts type={} status={} productId={} page={} size={}",
        type == null ? null : Encode.forJava(type),
        status == null ? null : Encode.forJava(status),
        productId,
        page,
        size);
    InventoryAlertPageResponse response =
        mapper.toPageResponse(getInventoryAlertsUseCase.execute(mapper.toQuery(type, status, productId, page, size)));
    LOGGER.debug("GET /inventory/alerts -> 200 totalElements={}", response.totalElements());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code PATCH /inventory/alerts/{id}/resolve} (ADMIN — unenforced, dev-plan.md): closes the
   * alert as fixed.
   *
   * @param id the alert to resolve
   * @param request the optional closing note
   * @return 200 with the resolved alert
   */
  @PatchMapping("/{id}/resolve")
  public ResponseEntity<InventoryAlertResponse> resolve(
      @PathVariable UUID id, @Valid @RequestBody ResolveAlertRequest request) {
    LOGGER.debug("PATCH /inventory/alerts/{}/resolve", id);
    InventoryAlertResponse response =
        mapper.toResponse(resolveInventoryAlertUseCase.execute(mapper.toResolveCommand(id, request)));
    LOGGER.debug("PATCH /inventory/alerts/{}/resolve -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code PATCH /inventory/alerts/{id}/dismiss} (ADMIN — unenforced, dev-plan.md): closes the
   * alert as acknowledged, no action needed.
   *
   * @param id the alert to dismiss
   * @param request the optional closing note
   * @return 200 with the dismissed alert
   */
  @PatchMapping("/{id}/dismiss")
  public ResponseEntity<InventoryAlertResponse> dismiss(
      @PathVariable UUID id, @Valid @RequestBody DismissAlertRequest request) {
    LOGGER.debug("PATCH /inventory/alerts/{}/dismiss", id);
    InventoryAlertResponse response =
        mapper.toResponse(dismissInventoryAlertUseCase.execute(mapper.toDismissCommand(id, request)));
    LOGGER.debug("PATCH /inventory/alerts/{}/dismiss -> 200", id);
    return ResponseEntity.ok(response);
  }
}
