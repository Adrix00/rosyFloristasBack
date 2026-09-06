package com.floristeriarosy.application.inventory.mapper;

import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.domain.model.inventory.StockMovement;

/** Domain to application DTO (00-project-principles.md #10: Application Mapper). */
public final class StockMovementDtoMapper {

  private StockMovementDtoMapper() {}

  /**
   * @param movement the domain movement to expose
   * @param adminUserName the admin's email, resolved separately by the caller (ADR-005), or
   *     {@code null}
   * @return its read shape, with plain UUID/enum fields a Controller may hold directly
   */
  public static StockMovementDto toDto(StockMovement movement, String adminUserName) {
    return new StockMovementDto(
        movement.id().value(),
        movement.productId().value(),
        movement.type(),
        movement.quantity(),
        movement.resultingStock(),
        movement.adminUserId(),
        adminUserName,
        movement.note(),
        movement.createdAt());
  }
}
