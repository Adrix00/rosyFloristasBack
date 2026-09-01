package com.floristeriarosy.infrastructure.persistence.mapper.discount;

import com.floristeriarosy.domain.model.discount.Discount;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.entity.discount.DiscountEntity;
import org.springframework.stereotype.Component;

/** Domain ↔ JPA entity conversions (ADR-002: Persistence Mapper). */
@Component
public class DiscountPersistenceMapper {

  /**
   * @param discount the domain discount to persist
   * @return its JPA entity shape
   */
  public DiscountEntity toEntity(Discount discount) {
    return new DiscountEntity(
        discount.id().value(),
        discount.productId().value(),
        discount.originalPrice(),
        discount.salePrice(),
        discount.startsAt(),
        discount.endsAt(),
        discount.quantityLimit(),
        discount.quantitySold(),
        discount.createdAt(),
        discount.updatedAt());
  }

  /**
   * @param entity the persisted JPA entity
   * @return the rebuilt domain discount ({@link Discount#reconstitute})
   */
  public Discount toDomain(DiscountEntity entity) {
    return Discount.reconstitute(
        DiscountId.of(entity.getId()),
        ProductId.of(entity.getProductId()),
        entity.getOriginalPrice(),
        entity.getSalePrice(),
        entity.getStartsAt(),
        entity.getEndsAt(),
        entity.getQuantityLimit(),
        entity.getQuantitySold(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
