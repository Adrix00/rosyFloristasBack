package com.floristeriarosy.infrastructure.persistence.mapper.inventory;

import com.floristeriarosy.domain.model.inventory.StockMovement;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import com.floristeriarosy.domain.model.inventory.valueobject.StockMovementId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.entity.inventory.StockMovementEntity;
import org.springframework.stereotype.Component;

/** Domain ↔ JPA entity conversions (ADR-002: Persistence Mapper). */
@Component
public class StockMovementPersistenceMapper {

  /**
   * @param movement the domain movement to persist
   * @return its JPA entity shape
   */
  public StockMovementEntity toEntity(StockMovement movement) {
    return new StockMovementEntity(
        movement.id().value(),
        movement.productId().value(),
        movement.type().name(),
        movement.quantity(),
        movement.resultingStock(),
        movement.adminUserId(),
        movement.note(),
        movement.createdAt());
  }

  /**
   * @param entity the persisted JPA entity
   * @return the rebuilt domain movement ({@link StockMovement#reconstitute})
   */
  public StockMovement toDomain(StockMovementEntity entity) {
    return StockMovement.reconstitute(
        StockMovementId.of(entity.getId()),
        ProductId.of(entity.getProductId()),
        StockMovementType.valueOf(entity.getType()),
        entity.getQuantity(),
        entity.getResultingStock(),
        entity.getAdminUserId(),
        entity.getNote(),
        entity.getCreatedAt());
  }
}
