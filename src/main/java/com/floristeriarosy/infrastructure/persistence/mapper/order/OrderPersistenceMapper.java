package com.floristeriarosy.infrastructure.persistence.mapper.order;

import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderDelivery;
import com.floristeriarosy.domain.model.order.OrderItem;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderItemId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.entity.order.OrderDeliveryEntity;
import com.floristeriarosy.infrastructure.persistence.entity.order.OrderEntity;
import com.floristeriarosy.infrastructure.persistence.entity.order.OrderItemEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Domain ↔ JPA entity conversions for the order aggregate (ADR-002: Persistence Mapper). */
@Component
public class OrderPersistenceMapper {

  /**
   * @param order the domain order to persist
   * @return its JPA entity shape, with children and back-references wired
   */
  public OrderEntity toEntity(Order order) {
    OrderEntity entity =
        new OrderEntity(
            order.id().value(),
            order.orderNumber().value(),
            order.channel(),
            order.fulfillment(),
            order.status(),
            order.customerId(),
            order.buyerFirstNameEncrypted(),
            order.buyerLastNameEncrypted(),
            order.buyerEmailEncrypted(),
            order.buyerEmailHash(),
            order.buyerPhoneEncrypted(),
            order.buyerPhoneHash(),
            order.subtotal(),
            order.shippingCost(),
            order.placedAt(),
            order.retentionUntil(),
            order.personalDataPurgedAt(),
            order.version(),
            order.createdAt(),
            order.updatedAt());

    List<OrderItemEntity> itemEntities = new ArrayList<>();
    for (OrderItem item : order.items()) {
      OrderItemEntity itemEntity = toEntity(item, entity);
      itemEntity.setOrder(entity);
      itemEntities.add(itemEntity);
    }
    entity.setItems(itemEntities);

    if (order.delivery() != null) {
      OrderDeliveryEntity deliveryEntity = toEntity(order.delivery(), entity);
      entity.setDelivery(deliveryEntity);
    }

    return entity;
  }

  /**
   * @param item the domain order line
   * @param order the owning entity
   * @return the JPA order line entity
   */
  private OrderItemEntity toEntity(OrderItem item, OrderEntity order) {
    return new OrderItemEntity(
        item.id().value(),
        order,
        item.productId().value(),
        item.productName(),
        item.productAttributes(),
        item.unitPrice(),
        item.discountPrice(),
        item.discountId() != null ? item.discountId().value() : null,
        item.quantity(),
        item.createdAt());
  }

  /**
   * @param delivery the domain delivery/pickup slot
   * @param order the owning entity
   * @return the JPA delivery entity
   */
  private OrderDeliveryEntity toEntity(OrderDelivery delivery, OrderEntity order) {
    return new OrderDeliveryEntity(
        order.getId(),
        order,
        delivery.recipientNameEncrypted(),
        delivery.recipientPhoneEncrypted(),
        delivery.streetEncrypted(),
        delivery.numberEncrypted(),
        delivery.detailEncrypted(),
        delivery.postalCode(),
        delivery.latitudeEncrypted(),
        delivery.longitudeEncrypted(),
        delivery.distanceKm(),
        delivery.deliveryDate(),
        delivery.slotFrom(),
        delivery.slotTo(),
        delivery.cardMessageEncrypted(),
        delivery.createdAt(),
        delivery.updatedAt());
  }

  /**
   * Copies the mutable fields of {@code order} onto a managed entity, replacing the children so
   * cascade/orphanRemoval stays consistent.
   *
   * @param existing the entity Hibernate already loaded
   * @param order the domain order carrying the new values
   * @return the same managed entity, now mutated
   */
  public OrderEntity updateEntity(OrderEntity existing, Order order) {
    existing.setStatus(order.status());
    existing.setCustomerId(order.customerId());
    existing.setBuyerFirstNameEncrypted(order.buyerFirstNameEncrypted());
    existing.setBuyerLastNameEncrypted(order.buyerLastNameEncrypted());
    existing.setBuyerEmailEncrypted(order.buyerEmailEncrypted());
    existing.setBuyerEmailHash(order.buyerEmailHash());
    existing.setBuyerPhoneEncrypted(order.buyerPhoneEncrypted());
    existing.setBuyerPhoneHash(order.buyerPhoneHash());
    existing.setSubtotal(order.subtotal());
    existing.setShippingCost(order.shippingCost());
    existing.setPlacedAt(order.placedAt());
    existing.setRetentionUntil(order.retentionUntil());
    existing.setPersonalDataPurgedAt(order.personalDataPurgedAt());

    List<OrderItemEntity> newItems = new ArrayList<>();
    for (OrderItem item : order.items()) {
      newItems.add(toEntity(item, existing));
    }
    existing.setItems(newItems);

    existing.setDelivery(order.delivery() != null ? toEntity(order.delivery(), existing) : null);
    return existing;
  }

  /**
   * @param entity the persisted JPA order aggregate
   * @return the rebuilt domain order
   */
  public Order toDomain(OrderEntity entity) {
    List<OrderItem> items = new ArrayList<>();
    for (OrderItemEntity item : entity.getItems()) {
      items.add(toDomain(item));
    }
    OrderDelivery delivery = entity.getDelivery() != null ? toDomain(entity.getDelivery()) : null;
    return Order.reconstitute(
        OrderId.of(entity.getId()),
        OrderNumber.of(entity.getOrderNumber()),
        entity.getChannel(),
        entity.getFulfillment(),
        entity.getStatus(),
        entity.getCustomerId(),
        entity.getBuyerFirstNameEncrypted(),
        entity.getBuyerLastNameEncrypted(),
        entity.getBuyerEmailEncrypted(),
        entity.getBuyerEmailHash(),
        entity.getBuyerPhoneEncrypted(),
        entity.getBuyerPhoneHash(),
        items,
        delivery,
        entity.getSubtotal(),
        entity.getShippingCost(),
        entity.getPlacedAt(),
        entity.getRetentionUntil(),
        entity.getPersonalDataPurgedAt(),
        entity.getVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  /**
   * @param entity the persisted order line
   * @return the rebuilt domain order line
   */
  private OrderItem toDomain(OrderItemEntity entity) {
    return OrderItem.reconstitute(
        OrderItemId.of(entity.getId()),
        ProductId.of(entity.getProductId()),
        entity.getProductName(),
        entity.getProductAttributes(),
        entity.getUnitPrice(),
        entity.getDiscountPrice(),
        entity.getDiscountId() != null ? DiscountId.of(entity.getDiscountId()) : null,
        entity.getQuantity(),
        entity.getCreatedAt());
  }

  /**
   * @param entity the persisted delivery/pickup slot
   * @return the rebuilt domain delivery/pickup slot
   */
  private OrderDelivery toDomain(OrderDeliveryEntity entity) {
    return OrderDelivery.reconstitute(
        entity.getRecipientNameEncrypted(),
        entity.getRecipientPhoneEncrypted(),
        entity.getStreetEncrypted(),
        entity.getNumberEncrypted(),
        entity.getDetailEncrypted(),
        entity.getPostalCode(),
        entity.getLatitudeEncrypted(),
        entity.getLongitudeEncrypted(),
        entity.getDistanceKm(),
        entity.getDeliveryDate(),
        entity.getSlotFrom(),
        entity.getSlotTo(),
        entity.getCardMessageEncrypted(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
