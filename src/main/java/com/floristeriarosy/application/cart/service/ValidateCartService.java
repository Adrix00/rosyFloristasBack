package com.floristeriarosy.application.cart.service;

import com.floristeriarosy.application.cart.command.ValidateCartCommand;
import com.floristeriarosy.application.cart.dto.CartCatalogEntryDto;
import com.floristeriarosy.application.cart.dto.CartValidationDto;
import com.floristeriarosy.application.cart.dto.InsufficientStockCartItemDto;
import com.floristeriarosy.application.cart.dto.RemovedCartItemDto;
import com.floristeriarosy.application.cart.port.in.ValidateCartUseCase;
import com.floristeriarosy.application.cart.port.out.CartItemWritePort;
import com.floristeriarosy.application.cart.port.out.CartPricingPort;
import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.application.cart.port.out.CartWritePort;
import com.floristeriarosy.application.cart.support.CartFinder;
import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.product.ProductStatus;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link ValidateCartUseCase} (cart.md, rule 3.4): removes lines whose product is no
 * longer {@code ACTIVE}, and reports — without adjusting — lines whose quantity exceeds real
 * stock.
 */
@Service
@Transactional
public class ValidateCartService implements ValidateCartUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ValidateCartService.class);

  private final CartReadPort cartReadPort;
  private final CartWritePort cartWritePort;
  private final CartItemWritePort cartItemWritePort;
  private final CartPricingPort cartPricingPort;

  /**
   * @param cartReadPort resolves the caller's existing cart, if any
   * @param cartWritePort renews the cart's expiry when lines are removed
   * @param cartItemWritePort deletes the removed lines
   * @param cartPricingPort resolves each line's product status and real stock in one query
   */
  public ValidateCartService(
      CartReadPort cartReadPort,
      CartWritePort cartWritePort,
      CartItemWritePort cartItemWritePort,
      CartPricingPort cartPricingPort) {
    this.cartReadPort = cartReadPort;
    this.cartWritePort = cartWritePort;
    this.cartItemWritePort = cartItemWritePort;
    this.cartPricingPort = cartPricingPort;
  }

  /**
   * @param command the caller's identity
   * @return whether the cart was already valid, plus what was removed or is blocking checkout
   */
  @Override
  public CartValidationDto execute(ValidateCartCommand command) {
    LOGGER.debug("validateCart hasCustomer={}", command.customerId() != null);

    Optional<Cart> existing = CartFinder.find(cartReadPort, command.customerId(), command.sessionToken());
    if (existing.isEmpty() || existing.get().items().isEmpty()) {
      LOGGER.debug("validateCart -> valid=true, nothing to check");
      return new CartValidationDto(true, List.of(), List.of());
    }

    Cart cart = existing.get();
    Map<UUID, CartCatalogEntryDto> catalog =
        cartPricingPort.catalogEntriesFor(cart.items().keySet()).stream()
            .collect(Collectors.toMap(CartCatalogEntryDto::productId, entry -> entry));

    List<RemovedCartItemDto> removed = new ArrayList<>();
    List<InsufficientStockCartItemDto> insufficientStock = new ArrayList<>();
    Set<ProductId> toRemove = new HashSet<>();

    for (Map.Entry<ProductId, Integer> line : cart.items().entrySet()) {
      CartCatalogEntryDto entry = catalog.get(line.getKey().value());
      if (entry == null) {
        continue; // defensive: ON DELETE CASCADE guarantees the product row always exists
      }
      if (!ProductStatus.ACTIVE.name().equals(entry.status())) {
        removed.add(new RemovedCartItemDto(entry.productId(), entry.productName()));
        toRemove.add(line.getKey());
        continue;
      }
      Integer availableQuantity = entry.availableQuantity();
      if (availableQuantity != null && line.getValue() > availableQuantity) {
        insufficientStock.add(
            new InsufficientStockCartItemDto(
                entry.productId(), entry.productName(), line.getValue(), availableQuantity));
      }
    }

    if (!toRemove.isEmpty()) {
      cart.removeItems(toRemove);
      cartItemWritePort.deleteAll(cart.id(), toRemove);
      cart.renewExpiry();
      cartWritePort.touch(cart.id(), cart.expiresAt());
    }

    boolean valid = removed.isEmpty() && insufficientStock.isEmpty();
    CartValidationDto result = new CartValidationDto(valid, removed, insufficientStock);
    LOGGER.debug(
        "validateCart -> valid={} removedCount={} insufficientStockCount={}",
        valid,
        removed.size(),
        insufficientStock.size());
    return result;
  }
}
