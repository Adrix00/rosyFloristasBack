package com.floristeriarosy.infrastructure.web.mapper.inventory;

import com.floristeriarosy.application.inventory.command.DismissInventoryAlertCommand;
import com.floristeriarosy.application.inventory.command.ResolveInventoryAlertCommand;
import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.inventory.query.GetInventoryAlertsQuery;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.domain.model.inventory.InventoryAlertStatus;
import com.floristeriarosy.domain.model.inventory.InventoryAlertType;
import com.floristeriarosy.infrastructure.web.request.inventory.DismissAlertRequest;
import com.floristeriarosy.infrastructure.web.request.inventory.ResolveAlertRequest;
import com.floristeriarosy.infrastructure.web.response.inventory.InventoryAlertPageResponse;
import com.floristeriarosy.infrastructure.web.response.inventory.InventoryAlertResponse;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Only class in {@link com.floristeriarosy.infrastructure.web.controller.inventory.InventoryAlertController}'s
 * call graph allowed to touch domain-typed fields ({@code InventoryAlertType}, {@code
 * InventoryAlertStatus}): keeps the Controller itself domain-free (HexagonalArchitectureTest). Pure
 * 1:1 field mapping, not logged (see CLAUDE.md, Logging) — every call is already visible in the
 * Controller's own entry/exit log.
 */
@Component
public class InventoryAlertWebMapper {

  /** Upper bound of {@code size} on a paginated listing, matching product.md's own admin listings. */
  private static final int MAX_PAGE_SIZE = 100;

  /**
   * @param type raw {@code type} query parameter, or {@code null}; an unparseable value is treated
   *     as absent rather than rejected — this is an optional admin filter, not a validated request
   *     body field
   * @param status raw {@code status} query parameter, or {@code null}; same treatment as {@code type}
   * @param productId only alerts for this product, or {@code null} for every product
   * @param page requested page, clamped to {@code >= 0}
   * @param size requested page size, clamped to {@code [1, MAX_PAGE_SIZE]}
   * @return the query to hand to {@code GetInventoryAlertsUseCase}
   */
  public GetInventoryAlertsQuery toQuery(String type, String status, UUID productId, int page, int size) {
    return new GetInventoryAlertsQuery(
        parseType(type), parseStatus(status), productId, clampPage(page), clampSize(size));
  }

  /**
   * @param id the alert to resolve, from the path
   * @param request the optional closing note
   * @return the command to hand to {@code ResolveInventoryAlertUseCase}
   */
  public ResolveInventoryAlertCommand toResolveCommand(UUID id, ResolveAlertRequest request) {
    return new ResolveInventoryAlertCommand(id, request.note());
  }

  /**
   * @param id the alert to dismiss, from the path
   * @param request the optional closing note
   * @return the command to hand to {@code DismissInventoryAlertUseCase}
   */
  public DismissInventoryAlertCommand toDismissCommand(UUID id, DismissAlertRequest request) {
    return new DismissInventoryAlertCommand(id, request.note());
  }

  /**
   * @param dto the alert to expose
   * @return its API representation; {@code resolvedByAdminName} is always {@code null} (known gap)
   */
  public InventoryAlertResponse toResponse(InventoryAlertDto dto) {
    return new InventoryAlertResponse(
        dto.id(),
        dto.type(),
        dto.productId(),
        dto.productName(),
        dto.observedValue(),
        dto.expectedValue(),
        dto.status(),
        null,
        dto.resolvedAt(),
        dto.createdAt());
  }

  /**
   * @param result the paginated result to expose
   * @return its API representation
   */
  public InventoryAlertPageResponse toPageResponse(PageResult<InventoryAlertDto> result) {
    return new InventoryAlertPageResponse(
        result.items().stream().map(this::toResponse).toList(), result.totalElements(), result.page(), result.size());
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
   * @param type the raw {@code type} query parameter
   * @return {@code type} parsed, or {@code null} if absent or unparseable
   */
  private InventoryAlertType parseType(String type) {
    if (type == null || type.isBlank()) {
      return null;
    }
    try {
      return InventoryAlertType.valueOf(type.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unparseable) {
      return null;
    }
  }

  /**
   * @param status the raw {@code status} query parameter
   * @return {@code status} parsed, or {@code null} if absent or unparseable
   */
  private InventoryAlertStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return InventoryAlertStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unparseable) {
      return null;
    }
  }
}
