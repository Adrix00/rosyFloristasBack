package com.floristeriarosy.application.discount.mapper;

import com.floristeriarosy.application.discount.dto.DiscountDto;
import com.floristeriarosy.domain.model.discount.Discount;

/** Domain to application DTO (00-project-principles.md #10: Application Mapper). */
public final class DiscountDtoMapper {

  private DiscountDtoMapper() {}

  /**
   * @param discount the domain discount to expose
   * @return its read shape, with plain UUID/enum fields a Controller may hold directly
   */
  public static DiscountDto toDto(Discount discount) {
    return new DiscountDto(
        discount.id().value(),
        discount.productId().value(),
        discount.originalPrice(),
        discount.salePrice(),
        discount.startsAt(),
        discount.endsAt(),
        discount.quantityLimit(),
        discount.quantitySold(),
        discount.state(),
        discount.createdAt(),
        discount.updatedAt());
  }
}
