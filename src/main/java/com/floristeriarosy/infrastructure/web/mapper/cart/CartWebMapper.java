package com.floristeriarosy.infrastructure.web.mapper.cart;

import com.floristeriarosy.application.cart.command.AddCartItemCommand;
import com.floristeriarosy.application.cart.command.ClearCartCommand;
import com.floristeriarosy.application.cart.command.RemoveCartItemCommand;
import com.floristeriarosy.application.cart.command.UpdateCartItemCommand;
import com.floristeriarosy.application.cart.command.ValidateCartCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.dto.CartItemDto;
import com.floristeriarosy.application.cart.dto.CartValidationDto;
import com.floristeriarosy.application.cart.dto.InsufficientStockCartItemDto;
import com.floristeriarosy.application.cart.dto.RemovedCartItemDto;
import com.floristeriarosy.application.cart.query.GetCartQuery;
import com.floristeriarosy.infrastructure.web.request.cart.AddCartItemRequest;
import com.floristeriarosy.infrastructure.web.request.cart.UpdateCartItemRequest;
import com.floristeriarosy.infrastructure.web.response.cart.CartItemResponse;
import com.floristeriarosy.infrastructure.web.response.cart.CartResponse;
import com.floristeriarosy.infrastructure.web.response.cart.CartValidationResponse;
import com.floristeriarosy.infrastructure.web.response.cart.InsufficientStockItemResponse;
import com.floristeriarosy.infrastructure.web.response.cart.RemovedCartItemResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Only class in the controller's call graph allowed to touch application-layer DTOs: keeps the
 * Controller itself free of anything beyond Request/Response (CLAUDE.md, Logging — pure 1:1 field
 * mapping, not logged; every call is already visible in the Controller's own entry/exit log).
 */
@Component
public class CartWebMapper {

  /**
   * @param customerId the authenticated customer, or {@code null} for a guest
   * @param sessionToken the guest cart cookie value
   * @param request the product and quantity to add
   * @return the command to hand to {@code AddCartItemUseCase}
   */
  public AddCartItemCommand toCommand(UUID customerId, String sessionToken, AddCartItemRequest request) {
    return new AddCartItemCommand(customerId, sessionToken, request.productId(), request.quantity());
  }

  /**
   * @param customerId the authenticated customer, or {@code null} for a guest
   * @param sessionToken the guest cart cookie value
   * @param productId the product to update, from the path
   * @param request the exact quantity to set
   * @return the command to hand to {@code UpdateCartItemUseCase}
   */
  public UpdateCartItemCommand toCommand(
      UUID customerId, String sessionToken, UUID productId, UpdateCartItemRequest request) {
    return new UpdateCartItemCommand(customerId, sessionToken, productId, request.quantity());
  }

  /**
   * @param customerId the authenticated customer, or {@code null} for a guest
   * @param sessionToken the guest cart cookie value
   * @param productId the product to remove, from the path
   * @return the command to hand to {@code RemoveCartItemUseCase}
   */
  public RemoveCartItemCommand toRemoveCommand(UUID customerId, String sessionToken, UUID productId) {
    return new RemoveCartItemCommand(customerId, sessionToken, productId);
  }

  /**
   * @param customerId the authenticated customer, or {@code null} for a guest
   * @param sessionToken the guest cart cookie value
   * @return the command to hand to {@code ClearCartUseCase}
   */
  public ClearCartCommand toClearCommand(UUID customerId, String sessionToken) {
    return new ClearCartCommand(customerId, sessionToken);
  }

  /**
   * @param customerId the authenticated customer, or {@code null} for a guest
   * @param sessionToken the guest cart cookie value
   * @return the command to hand to {@code ValidateCartUseCase}
   */
  public ValidateCartCommand toValidateCommand(UUID customerId, String sessionToken) {
    return new ValidateCartCommand(customerId, sessionToken);
  }

  /**
   * @param customerId the authenticated customer, or {@code null} for a guest
   * @param sessionToken the guest cart cookie value
   * @return the query to hand to {@code GetCartUseCase}
   */
  public GetCartQuery toQuery(UUID customerId, String sessionToken) {
    return new GetCartQuery(customerId, sessionToken);
  }

  /**
   * @param dto the cart to expose
   * @return its full API representation
   */
  public CartResponse toResponse(CartDto dto) {
    return new CartResponse(
        dto.id(), dto.items().stream().map(this::toItemResponse).toList(), dto.subtotal(), dto.itemCount());
  }

  /**
   * @param dto the validation result to expose
   * @return its full API representation
   */
  public CartValidationResponse toValidationResponse(CartValidationDto dto) {
    return new CartValidationResponse(
        dto.valid(),
        dto.removedItems().stream().map(this::toRemovedResponse).toList(),
        dto.insufficientStockItems().stream().map(this::toInsufficientStockResponse).toList());
  }

  /**
   * @param dto one priced cart line
   * @return its API representation
   */
  private CartItemResponse toItemResponse(CartItemDto dto) {
    return new CartItemResponse(
        dto.productId(),
        dto.productName(),
        dto.productSlug(),
        dto.mainImageUrl(),
        dto.unitPrice(),
        dto.effectivePrice(),
        dto.onSale(),
        dto.quantity(),
        dto.lineTotal(),
        dto.availableQuantity());
  }

  /**
   * @param dto one removed line
   * @return its API representation
   */
  private RemovedCartItemResponse toRemovedResponse(RemovedCartItemDto dto) {
    return new RemovedCartItemResponse(dto.productId(), dto.productName());
  }

  /**
   * @param dto one insufficient-stock line
   * @return its API representation
   */
  private InsufficientStockItemResponse toInsufficientStockResponse(InsufficientStockCartItemDto dto) {
    return new InsufficientStockItemResponse(
        dto.productId(), dto.productName(), dto.requestedQuantity(), dto.availableQuantity());
  }
}
