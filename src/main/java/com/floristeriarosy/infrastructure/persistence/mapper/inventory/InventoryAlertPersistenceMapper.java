package com.floristeriarosy.infrastructure.persistence.mapper.inventory;

import com.floristeriarosy.domain.model.inventory.InventoryAlert;
import com.floristeriarosy.domain.model.inventory.InventoryAlertStatus;
import com.floristeriarosy.domain.model.inventory.InventoryAlertType;
import com.floristeriarosy.domain.model.inventory.valueobject.InventoryAlertId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.entity.inventory.InventoryAlertEntity;
import org.springframework.stereotype.Component;

/** Domain ↔ JPA entity conversions (ADR-002: Persistence Mapper). */
@Component
public class InventoryAlertPersistenceMapper {

  /**
   * @param alert the domain alert to persist
   * @return its JPA entity shape
   */
  public InventoryAlertEntity toEntity(InventoryAlert alert) {
    return new InventoryAlertEntity(
        alert.id().value(),
        alert.type().name(),
        alert.productId().value(),
        alert.observedValue(),
        alert.expectedValue(),
        alert.status().name(),
        alert.resolvedByAdminId(),
        alert.resolvedAt(),
        alert.note(),
        alert.createdAt());
  }

  /**
   * @param entity the persisted JPA entity
   * @return the rebuilt domain alert ({@link InventoryAlert#reconstitute})
   */
  public InventoryAlert toDomain(InventoryAlertEntity entity) {
    return InventoryAlert.reconstitute(
        InventoryAlertId.of(entity.getId()),
        InventoryAlertType.valueOf(entity.getType()),
        ProductId.of(entity.getProductId()),
        entity.getObservedValue(),
        entity.getExpectedValue(),
        InventoryAlertStatus.valueOf(entity.getStatus()),
        entity.getResolvedByAdminId(),
        entity.getResolvedAt(),
        entity.getNote(),
        entity.getCreatedAt());
  }
}
