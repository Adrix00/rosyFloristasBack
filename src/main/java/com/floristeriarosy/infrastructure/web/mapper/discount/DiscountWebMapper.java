package com.floristeriarosy.infrastructure.web.mapper.discount;

import com.floristeriarosy.application.discount.command.CreateDiscountCommand;
import com.floristeriarosy.application.discount.command.DeleteDiscountCommand;
import com.floristeriarosy.application.discount.command.EndDiscountCommand;
import com.floristeriarosy.application.discount.command.UpdateDiscountCommand;
import com.floristeriarosy.application.discount.dto.DiscountDto;
import com.floristeriarosy.application.discount.query.GetProductDiscountsQuery;
import com.floristeriarosy.infrastructure.web.request.discount.CreateDiscountRequest;
import com.floristeriarosy.infrastructure.web.request.discount.UpdateDiscountRequest;
import com.floristeriarosy.infrastructure.web.response.discount.DiscountResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Only class in the controllers' call graph allowed to touch domain-typed fields ({@code
 * DiscountState}): keeps the Controllers themselves domain-free (HexagonalArchitectureTest). Pure
 * 1:1 field mapping, not logged (see CLAUDE.md, Logging) — every call is already visible in the
 * Controller's own entry/exit log.
 */
@Component
public class DiscountWebMapper {

  /**
   * @param productId the product to discount, from the path
   * @param request the promotion's fields
   * @return the command to hand to {@code CreateDiscountUseCase}
   */
  public CreateDiscountCommand toCommand(UUID productId, CreateDiscountRequest request) {
    return new CreateDiscountCommand(
        productId, request.salePrice(), request.startsAt(), request.endsAt(), request.quantityLimit());
  }

  /**
   * @param id the discount to update, from the path
   * @param request the requested new field values
   * @return the command to hand to {@code UpdateDiscountUseCase}
   */
  public UpdateDiscountCommand toCommand(UUID id, UpdateDiscountRequest request) {
    return new UpdateDiscountCommand(
        id, request.startsAt(), request.endsAt(), request.quantityLimit(), request.salePrice());
  }

  /**
   * @param id the discount to close, from the path
   * @return the command to hand to {@code EndDiscountUseCase}
   */
  public EndDiscountCommand toEndCommand(UUID id) {
    return new EndDiscountCommand(id);
  }

  /**
   * @param id the discount to delete, from the path
   * @return the command to hand to {@code DeleteDiscountUseCase}
   */
  public DeleteDiscountCommand toDeleteCommand(UUID id) {
    return new DeleteDiscountCommand(id);
  }

  /**
   * @param productId the product whose discount history to list, from the path
   * @return the query to hand to {@code GetProductDiscountsUseCase}
   */
  public GetProductDiscountsQuery toQuery(UUID productId) {
    return new GetProductDiscountsQuery(productId);
  }

  /**
   * @param dto the discount to expose
   * @return its API representation
   */
  public DiscountResponse toResponse(DiscountDto dto) {
    return new DiscountResponse(
        dto.id(),
        dto.productId(),
        dto.originalPrice(),
        dto.salePrice(),
        dto.startsAt(),
        dto.endsAt(),
        dto.quantityLimit(),
        dto.quantitySold(),
        dto.state(),
        dto.createdAt(),
        dto.updatedAt());
  }
}
