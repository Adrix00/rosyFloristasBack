package com.floristeriarosy.infrastructure.persistence.mapper.cart;

import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.entity.cart.CartEntity;
import com.floristeriarosy.infrastructure.persistence.entity.cart.CartItemEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Domain {@code Cart} aggregate &harr; JPA entities conversions (ADR-002: Persistence Mapper). */
@Component
public class CartPersistenceMapper {

  /**
   * @param cart the domain cart to persist
   * @return its JPA entity shape (the {@code carts} row only, never its lines)
   */
  public CartEntity toEntity(Cart cart) {
    return new CartEntity(
        cart.id().value(),
        cart.customerId(),
        cart.sessionToken(),
        cart.expiresAt(),
        cart.createdAt(),
        cart.updatedAt());
  }

  /**
   * @param entity the persisted {@code carts} row
   * @param itemEntities the persisted {@code cart_items} rows belonging to it
   * @return the rebuilt domain cart ({@link Cart#reconstitute})
   */
  public Cart toDomain(CartEntity entity, List<CartItemEntity> itemEntities) {
    Map<ProductId, Integer> items = new LinkedHashMap<>();
    for (CartItemEntity itemEntity : itemEntities) {
      items.put(ProductId.of(itemEntity.getProductId()), itemEntity.getQuantity());
    }
    return Cart.reconstitute(
        CartId.of(entity.getId()),
        entity.getCustomerId(),
        entity.getSessionToken(),
        entity.getExpiresAt(),
        items,
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
