package com.floristeriarosy.domain.model.order;

import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.order.valueobject.OrderItemId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.shared.util.LogSanitizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One sold line of an order — a snapshot at the moment of sale (order.md, rule 3.5). Never re-reads
 * {@code products} after creation: {@code productName}, {@code productAttributes} and both prices
 * are copied once and stay fixed even if the product later changes, is deactivated or is
 * discontinued (ADR-007).
 */
public final class OrderItem {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderItem.class);

  private final OrderItemId id;
  private final ProductId productId;
  private final String productName;
  private final Map<String, Object> productAttributes;
  private final BigDecimal unitPrice;
  private final BigDecimal discountPrice;
  private final DiscountId discountId;
  private final int quantity;
  private final Instant createdAt;

  /**
   * Private constructor. Use {@link #snapshot} to create a new order line, or {@link #reconstitute}
   * to rebuild one from persisted state.
   *
   * @param id application-generated identifier
   * @param productId the sold product
   * @param productName the product's name at the moment of sale
   * @param productAttributes the product's attribute values at the moment of sale
   * @param unitPrice the base price at the moment of sale
   * @param discountPrice the discounted price at the moment of sale, or {@code null}
   * @param discountId the applied promotion's reference, or {@code null}
   * @param quantity units sold
   * @param createdAt when the row was created, or {@code null} before the first save
   */
  private OrderItem(
      OrderItemId id,
      ProductId productId,
      String productName,
      Map<String, Object> productAttributes,
      BigDecimal unitPrice,
      BigDecimal discountPrice,
      DiscountId discountId,
      int quantity,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.productId = Objects.requireNonNull(productId, "productId");
    this.productName = Objects.requireNonNull(productName, "productName");
    this.productAttributes = copyAttributes(productAttributes);
    this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    this.discountPrice = discountPrice;
    this.discountId = discountId;
    this.quantity = quantity;
    this.createdAt = createdAt;
  }

  /**
   * Snapshots a cart line at the moment of sale (order.md, rule 3.5).
   *
   * @param id application-generated identifier
   * @param productId the sold product
   * @param productName the product's name at the moment of sale
   * @param productAttributes the product's attribute values at the moment of sale, or {@code null}
   * @param unitPrice the base price at the moment of sale
   * @param discountPrice the discounted price at the moment of sale, or {@code null} if none applied
   * @param discountId the applied promotion's reference, or {@code null}
   * @param quantity units sold
   * @return the new order line
   */
  public static OrderItem snapshot(
      OrderItemId id,
      ProductId productId,
      String productName,
      Map<String, Object> productAttributes,
      BigDecimal unitPrice,
      BigDecimal discountPrice,
      DiscountId discountId,
      int quantity) {
    LOGGER.debug(
        "snapshot id={} productId={} productName={} unitPrice={} discountPrice={} discountId={} quantity={}",
        id,
        productId,
        LogSanitizer.sanitize(productName),
        unitPrice,
        discountPrice,
        discountId,
        quantity);
    OrderItem result =
        new OrderItem(
            id, productId, productName, productAttributes, unitPrice, discountPrice, discountId,
            quantity, null);
    LOGGER.debug("snapshot id={} -> created", id);
    return result;
  }

  /**
   * Rebuilds an order line from persisted state. Used only by the persistence mapper.
   *
   * @param id the persisted identifier
   * @param productId the persisted product reference
   * @param productName the persisted product name snapshot
   * @param productAttributes the persisted attribute snapshot
   * @param unitPrice the persisted unit price
   * @param discountPrice the persisted discount price, or {@code null}
   * @param discountId the persisted discount reference, or {@code null}
   * @param quantity the persisted quantity
   * @param createdAt when the row was created
   * @return the rebuilt order line
   */
  public static OrderItem reconstitute(
      OrderItemId id,
      ProductId productId,
      String productName,
      Map<String, Object> productAttributes,
      BigDecimal unitPrice,
      BigDecimal discountPrice,
      DiscountId discountId,
      int quantity,
      Instant createdAt) {
    return new OrderItem(
        id, productId, productName, productAttributes, unitPrice, discountPrice, discountId,
        quantity, createdAt);
  }

  /**
   * @return {@code discountPrice} if present, otherwise {@code unitPrice}, times {@code quantity} —
   *     mirrors the {@code line_total} generated column (order.md, section 2)
   */
  public BigDecimal lineTotal() {
    BigDecimal effectivePrice = discountPrice != null ? discountPrice : unitPrice;
    return effectivePrice.multiply(BigDecimal.valueOf(quantity));
  }

  /**
   * Defensively copies {@code attributes} into a mutable map (SpotBugs EI_EXPOSE_REP2). The getter
   * wraps it again in an unmodifiable view.
   *
   * @param attributes the attribute values to copy
   * @return a mutable copy, or an empty map if {@code attributes} is {@code null}
   */
  private static Map<String, Object> copyAttributes(Map<String, Object> attributes) {
    return attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
  }

  /**
   * @return the application-generated identifier
   */
  public OrderItemId id() {
    return id;
  }

  /**
   * @return the sold product's reference
   */
  public ProductId productId() {
    return productId;
  }

  /**
   * @return the product's name at the moment of sale
   */
  public String productName() {
    return productName;
  }

  /**
   * @return the product's attribute values at the moment of sale, unmodifiable
   */
  public Map<String, Object> productAttributes() {
    return Collections.unmodifiableMap(productAttributes);
  }

  /**
   * @return the base price at the moment of sale
   */
  public BigDecimal unitPrice() {
    return unitPrice;
  }

  /**
   * @return the discounted price at the moment of sale, or {@code null} if none applied
   */
  public BigDecimal discountPrice() {
    return discountPrice;
  }

  /**
   * @return the applied promotion's reference, or {@code null}
   */
  public DiscountId discountId() {
    return discountId;
  }

  /**
   * @return units sold
   */
  public int quantity() {
    return quantity;
  }

  /**
   * @return when the row was created, or {@code null} before the first save
   */
  public Instant createdAt() {
    return createdAt;
  }
}
