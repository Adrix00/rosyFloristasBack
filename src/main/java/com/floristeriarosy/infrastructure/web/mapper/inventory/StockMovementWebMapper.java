package com.floristeriarosy.infrastructure.web.mapper.inventory;

import com.floristeriarosy.application.inventory.command.RegisterAdjustmentCommand;
import com.floristeriarosy.application.inventory.command.RegisterWasteCommand;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.inventory.query.GetStockMovementsQuery;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.infrastructure.web.request.inventory.RegisterAdjustmentRequest;
import com.floristeriarosy.infrastructure.web.request.inventory.RegisterWasteRequest;
import com.floristeriarosy.infrastructure.web.response.inventory.StockMovementPageResponse;
import com.floristeriarosy.infrastructure.web.response.inventory.StockMovementResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Only class in {@link com.floristeriarosy.infrastructure.web.controller.inventory.StockMovementController}'s
 * call graph allowed to touch domain-typed fields ({@code StockMovementType}): keeps the Controller
 * itself domain-free (HexagonalArchitectureTest). Pure 1:1 field mapping, not logged (see
 * CLAUDE.md, Logging) — every call is already visible in the Controller's own entry/exit log.
 */
@Component
public class StockMovementWebMapper {

  /** Upper bound of {@code size} on a paginated listing, matching product.md's own admin listings. */
  private static final int MAX_PAGE_SIZE = 100;

  /**
   * @param productId the product whose history to list, from the path
   * @param page requested page, clamped to {@code >= 0}
   * @param size requested page size, clamped to {@code [1, MAX_PAGE_SIZE]}
   * @return the query to hand to {@code GetStockMovementsUseCase}
   */
  public GetStockMovementsQuery toQuery(UUID productId, int page, int size) {
    return new GetStockMovementsQuery(productId, clampPage(page), clampSize(size));
  }

  /**
   * @param productId the product being written off, from the path
   * @param request the wasted quantity and required note
   * @return the command to hand to {@code RegisterWasteUseCase}
   */
  public RegisterWasteCommand toCommand(UUID productId, RegisterWasteRequest request) {
    return new RegisterWasteCommand(productId, request.quantity(), request.note());
  }

  /**
   * @param productId the product being corrected, from the path
   * @param request the signed delta and required note
   * @return the command to hand to {@code RegisterAdjustmentUseCase}
   */
  public RegisterAdjustmentCommand toCommand(UUID productId, RegisterAdjustmentRequest request) {
    return new RegisterAdjustmentCommand(productId, request.quantity(), request.note());
  }

  /**
   * @param dto the movement to expose
   * @return its API representation; {@code adminUserName} is always {@code null} (known gap)
   */
  public StockMovementResponse toResponse(StockMovementDto dto) {
    return new StockMovementResponse(
        dto.id(), dto.productId(), dto.type(), dto.quantity(), dto.resultingStock(), null, dto.note(), dto.createdAt());
  }

  /**
   * @param result the paginated result to expose
   * @return its API representation
   */
  public StockMovementPageResponse toPageResponse(PageResult<StockMovementDto> result) {
    return new StockMovementPageResponse(
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
}
