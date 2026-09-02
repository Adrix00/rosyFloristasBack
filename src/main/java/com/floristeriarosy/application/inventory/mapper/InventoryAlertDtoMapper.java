package com.floristeriarosy.application.inventory.mapper;

import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.domain.model.inventory.InventoryAlert;

/** Domain to application DTO (00-project-principles.md #10: Application Mapper). */
public final class InventoryAlertDtoMapper {

  private InventoryAlertDtoMapper() {}

  /**
   * @param alert the domain alert to expose
   * @param productName the product's current name, resolved separately (the alert itself only
   *     carries {@code productId})
   * @return its read shape, with plain UUID/enum fields a Controller may hold directly
   */
  public static InventoryAlertDto toDto(InventoryAlert alert, String productName) {
    return new InventoryAlertDto(
        alert.id().value(),
        alert.type(),
        alert.productId().value(),
        productName,
        alert.observedValue(),
        alert.expectedValue(),
        alert.status(),
        alert.resolvedByAdminId(),
        alert.resolvedAt(),
        alert.createdAt());
  }
}
