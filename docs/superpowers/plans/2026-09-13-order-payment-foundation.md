# Order + Payment — Phase 1: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the pure domain model (aggregates, entities, value objects, enums) for `order`
and `payment` — the shared foundation every later phase (Payment capability, Checkout, Order
lifecycle, Counter order, Saved cards, Cross-cutting) builds on. No REST, no persistence, no use
case in this phase: nothing calls this code yet, so nothing here should call anything else either.

**Architecture:** DDD aggregates following this codebase's established shape exactly (see
`Admin`/`InventoryAlert`/`Cart` for precedent): a private constructor, a `create`/`place`-style
factory for new instances (logs entry/exit, business-named), a `reconstitute` factory for the
persistence mapper (no logging, no validation — it is replaying state the database already
accepted), plain getters with one-line Javadoc, `byte[]` fields for encrypted PII cloned on every
read (SpotBugs `EI_EXPOSE_REP`/`EI_EXPOSE_REP2`). No business *transition* methods (`prepare()`,
`ship()`, `Payment.capture()`, etc.) are added in this phase — they belong to the phase that
introduces their first caller and test (Phase 2 for `Payment`, Phase 3/4 for `Order`); adding them
now would be untested, uncalled code.

**Tech Stack:** Java 21, JUnit 5, AssertJ. No Spring, no JPA, no JDBC in anything this plan touches
(`CLAUDE.md`: "The Domain must never depend on Spring/JPA/JDBC/Infrastructure/HTTP").

**Spec:** `docs/features/order.md` (sections 2, 3.5, 3.8, 3.9), `docs/features/payment.md`
(section 2), `docs/architecture/ADR/ADR-017-card-payment-via-redsys-redirect.md`,
`docs/architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md`,
`docs/architecture/ADR/ADR-009-optimistic-locking.md`.

## Global Constraints

- No wildcard imports. Constructor injection only — not applicable here (domain has no injected
  collaborators), but keep it in mind for later phases.
- Every public and private method gets Javadoc: what it does, `@param` per parameter, `@return` if
  non-void, `@throws` for a thrown exception (`CLAUDE.md`, Javadoc section).
- Domain business methods that create or mutate state log entry (every parameter) and exit
  (outcome) at `DEBUG` via SLF4J; `reconstitute` factories and plain getters do not
  (`CLAUDE.md`, Logging section — matches `Admin.create`/`Admin.reconstitute` exactly).
- Never log an encrypted/hashed field's value — only the identifier. None of this phase's log
  lines touch a PII field's content; they log ids, counts and enum values only.
- `byte[]` fields: every constructor/setter that receives one and every getter that returns one
  must defensively copy it (`clone()`), except where the incoming array is guaranteed fresh (the
  private constructor called only from a factory that just received the array itself — matches
  `Admin`'s own inconsistency-free pattern of cloning in mutators/getters, not in the constructor).
- Schema is already in place: `orders`, `order_items`, `order_deliveries`, `payments`,
  `order_number_seq` all exist since `V1__initial_schema.sql`, refined by `V3`, `V6`, `V9`, `V10`,
  `V14`, `V18`. This phase adds no migration.

---

## Task 1: Order value objects and enums

**Files:**
- Create: `src/main/java/com/floristeriarosy/domain/model/order/valueobject/OrderId.java`
- Create: `src/main/java/com/floristeriarosy/domain/model/order/valueobject/OrderItemId.java`
- Create: `src/main/java/com/floristeriarosy/domain/model/order/valueobject/OrderNumber.java`
- Create: `src/main/java/com/floristeriarosy/domain/model/order/OrderChannel.java`
- Create: `src/main/java/com/floristeriarosy/domain/model/order/OrderFulfillment.java`
- Create: `src/main/java/com/floristeriarosy/domain/model/order/OrderStatus.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `OrderId.newId()`/`OrderId.of(UUID)`/`.value()`, `OrderItemId.newId()`/`.of(UUID)`/
  `.value()`, `OrderNumber.of(String)`/`.value()`, `OrderChannel{WEB,STORE,INTERFLORA}`,
  `OrderFulfillment{DELIVERY,PICKUP,NONE}`,
  `OrderStatus{PENDING,PREPARING,OUT_FOR_DELIVERY,DELIVERED,REJECTED,CANCELLED}` — every later task
  in every phase that touches `order` uses these exact names.

No dedicated test for this task: this codebase does not unit-test its `newId`/`of`/`value`/
`equals`/`hashCode` value-object wrappers on their own (`ProductId`, `DiscountId`, `AdminId` have
none) — they are trivial and exercised indirectly by every aggregate test that uses them. Verify by
compiling only.

- [ ] **Step 1: Create `OrderId`**

```java
package com.floristeriarosy.domain.model.order.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of an {@code Order} (never assigned by the database). */
public final class OrderId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private OrderId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for an order being placed
   */
  public static OrderId newId() {
    return new OrderId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a path variable or a persisted row
   * @return the wrapped identifier
   */
  public static OrderId of(UUID value) {
    return new OrderId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof OrderId orderId && value.equals(orderId.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
```

- [ ] **Step 2: Create `OrderItemId`**

```java
package com.floristeriarosy.domain.model.order.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of an {@code OrderItem} (never assigned by the database). */
public final class OrderItemId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private OrderItemId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for an order line being created
   */
  public static OrderItemId newId() {
    return new OrderItemId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a persisted row
   * @return the wrapped identifier
   */
  public static OrderItemId of(UUID value) {
    return new OrderItemId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof OrderItemId orderItemId && value.equals(orderItemId.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
```

- [ ] **Step 3: Create `OrderNumber`**

```java
package com.floristeriarosy.domain.model.order.valueobject;

/**
 * The application-generated business identifier of an order (order.md, rule 3.1): {@code
 * {CHANNEL}-{YYYYMMDD}-{6-digit sequence}}, e.g. {@code WEB-20260826-000001}. Built from {@code
 * order_number_seq} outside the domain (a sequence read is JDBC, ADR-002) — this type only wraps
 * and validates the already-formatted value.
 */
public final class OrderNumber {

  private final String value;

  /**
   * @param value the formatted order number
   * @throws IllegalArgumentException {@code value} is {@code null} or blank
   */
  private OrderNumber(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
    this.value = value;
  }

  /**
   * @param value the formatted order number, already built from {@code order_number_seq}
   * @return the order number wrapping it
   */
  public static OrderNumber of(String value) {
    return new OrderNumber(value);
  }

  /**
   * @return the formatted order number
   */
  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof OrderNumber orderNumber && value.equals(orderNumber.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value;
  }
}
```

- [ ] **Step 4: Create the three enums**

```java
package com.floristeriarosy.domain.model.order;

/** Sales channel of an order (order.md, section 2). */
public enum OrderChannel {
  WEB,
  STORE,
  INTERFLORA
}
```

```java
package com.floristeriarosy.domain.model.order;

/** How an order reaches the customer (order.md, section 2). */
public enum OrderFulfillment {
  DELIVERY,
  PICKUP,
  NONE
}
```

```java
package com.floristeriarosy.domain.model.order;

/**
 * Fulfillment status of an order — accept, prepare, deliver (order.md, section 3.9). This is
 * separate from payment status, which lives in {@code payments} ({@code payment.md}).
 */
public enum OrderStatus {
  PENDING,
  PREPARING,
  OUT_FOR_DELIVERY,
  DELIVERED,
  REJECTED,
  CANCELLED
}
```

- [ ] **Step 5: Compile**

Run: `mvn -q compile`
Expected: `BUILD SUCCESS`, no Checkstyle violations.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/floristeriarosy/domain/model/order/valueobject/OrderId.java \
        src/main/java/com/floristeriarosy/domain/model/order/valueobject/OrderItemId.java \
        src/main/java/com/floristeriarosy/domain/model/order/valueobject/OrderNumber.java \
        src/main/java/com/floristeriarosy/domain/model/order/OrderChannel.java \
        src/main/java/com/floristeriarosy/domain/model/order/OrderFulfillment.java \
        src/main/java/com/floristeriarosy/domain/model/order/OrderStatus.java
git commit -m "feat(order): order value objects and enums (order.md §2, §3.9)"
```

---

## Task 2: `OrderItem` entity

**Files:**
- Create: `src/main/java/com/floristeriarosy/domain/model/order/OrderItem.java`
- Test: `src/test/java/com/floristeriarosy/domain/model/order/OrderItemTest.java`

**Interfaces:**
- Consumes: `OrderItemId` (Task 1), `com.floristeriarosy.domain.model.product.valueobject.ProductId`
  (existing), `com.floristeriarosy.domain.model.discount.valueobject.DiscountId` (existing).
- Produces: `OrderItem.snapshot(id, productId, productName, productAttributes, unitPrice,
  discountPrice, discountId, quantity)`, `OrderItem.reconstitute(..., createdAt)`,
  `.lineTotal()`, plus getters (`id()`, `productId()`, `productName()`, `productAttributes()`,
  `unitPrice()`, `discountPrice()`, `discountId()`, `quantity()`, `createdAt()`) — Phase 3
  (Checkout) and Phase 6 (`OrderItemResponse` mapping) both consume these exact names.

- [ ] **Step 1: Write the failing tests**

```java
package com.floristeriarosy.domain.model.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.order.valueobject.OrderItemId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Domain rules of {@link OrderItem} (order.md, rule 3.5). */
class OrderItemTest {

  @Test
  void lineTotalUsesDiscountPriceWhenPresent() {
    OrderItem item =
        OrderItem.snapshot(
            OrderItemId.newId(),
            ProductId.newId(),
            "Ramo de rosas",
            Map.of("color", "red"),
            new BigDecimal("25.00"),
            new BigDecimal("20.00"),
            DiscountId.newId(),
            3);

    assertThat(item.lineTotal()).isEqualByComparingTo("60.00");
  }

  @Test
  void lineTotalFallsBackToUnitPriceWhenNoDiscountApplied() {
    OrderItem item =
        OrderItem.snapshot(
            OrderItemId.newId(),
            ProductId.newId(),
            "Ramo de tulipanes",
            Map.of(),
            new BigDecimal("15.00"),
            null,
            null,
            2);

    assertThat(item.lineTotal()).isEqualByComparingTo("30.00");
    assertThat(item.discountId()).isNull();
  }

  @Test
  void snapshotDefensivelyCopiesProductAttributes() {
    Map<String, Object> attributes = new java.util.HashMap<>(Map.of("color", "red"));
    OrderItem item =
        OrderItem.snapshot(
            OrderItemId.newId(), ProductId.newId(), "Ramo", attributes, BigDecimal.TEN, null, null, 1);

    attributes.put("color", "blue");

    assertThat(item.productAttributes()).containsEntry("color", "red");
  }

  @Test
  void snapshotDefaultsNullAttributesToEmptyMap() {
    OrderItem item =
        OrderItem.snapshot(
            OrderItemId.newId(), ProductId.newId(), "Ramo", null, BigDecimal.TEN, null, null, 1);

    assertThat(item.productAttributes()).isEmpty();
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=OrderItemTest`
Expected: compilation failure — `OrderItem` does not exist yet.

- [ ] **Step 3: Implement `OrderItem`**

```java
package com.floristeriarosy.domain.model.order;

import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.order.valueobject.OrderItemId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * One sold line of an order — a snapshot at the moment of sale (order.md, rule 3.5). Never re-reads
 * {@code products} after creation: {@code productName}, {@code productAttributes} and both prices
 * are copied once and stay fixed even if the product later changes, is deactivated or is
 * discontinued (ADR-007).
 */
public final class OrderItem {

  private final OrderItemId id;
  private final ProductId productId;
  private final String productName;
  private final Map<String, Object> productAttributes;
  private final BigDecimal unitPrice;
  private final BigDecimal discountPrice;
  private final DiscountId discountId;
  private final int quantity;
  private final Instant createdAt;

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
    return new OrderItem(
        id, productId, productName, productAttributes, unitPrice, discountPrice, discountId,
        quantity, null);
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
   * Defensively copies {@code attributes} into an unmodifiable map (SpotBugs EI_EXPOSE_REP2).
   *
   * @param attributes the attribute values to copy
   * @return an unmodifiable copy, or an empty map if {@code attributes} is {@code null}
   */
  private static Map<String, Object> copyAttributes(Map<String, Object> attributes) {
    return attributes == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(attributes));
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
   * @return the product's attribute values at the moment of sale
   */
  public Map<String, Object> productAttributes() {
    return productAttributes;
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=OrderItemTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/floristeriarosy/domain/model/order/OrderItem.java \
        src/test/java/com/floristeriarosy/domain/model/order/OrderItemTest.java
git commit -m "feat(order): OrderItem snapshot entity (order.md §3.5)"
```

---

## Task 3: `OrderDelivery` entity

**Files:**
- Create: `src/main/java/com/floristeriarosy/domain/model/order/OrderDelivery.java`
- Test: `src/test/java/com/floristeriarosy/domain/model/order/OrderDeliveryTest.java`

**Interfaces:**
- Consumes: nothing beyond JDK types.
- Produces: `OrderDelivery.forDelivery(...)`, `OrderDelivery.forPickup(deliveryDate, slotFrom,
  slotTo)`, `OrderDelivery.reconstitute(...)`, getters (`recipientNameEncrypted()`,
  `recipientPhoneEncrypted()`, `streetEncrypted()`, `numberEncrypted()`, `detailEncrypted()`,
  `postalCode()`, `latitudeEncrypted()`, `longitudeEncrypted()`, `distanceKm()`, `deliveryDate()`,
  `slotFrom()`, `slotTo()`, `cardMessageEncrypted()`, `createdAt()`, `updatedAt()`) — Phase 3
  (Checkout, building it from `DeliveryRequest`/`PickupRequest`) and Phase 6 (response mapping)
  consume these exact names.

- [ ] **Step 1: Write the failing tests**

```java
package com.floristeriarosy.domain.model.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Domain rules of {@link OrderDelivery} (order.md, rule 3.8). */
class OrderDeliveryTest {

  private static final byte[] ENCRYPTED = "encrypted".getBytes(StandardCharsets.UTF_8);

  @Test
  void forPickupCarriesNoRecipientOrAddress() {
    OrderDelivery delivery =
        OrderDelivery.forPickup(LocalDate.of(2026, 9, 20), LocalTime.of(10, 0), LocalTime.of(11, 0));

    assertThat(delivery.recipientNameEncrypted()).isNull();
    assertThat(delivery.streetEncrypted()).isNull();
    assertThat(delivery.postalCode()).isNull();
    assertThat(delivery.distanceKm()).isNull();
    assertThat(delivery.deliveryDate()).isEqualTo(LocalDate.of(2026, 9, 20));
  }

  @Test
  void forDeliveryCarriesRecipientAndAddress() {
    OrderDelivery delivery =
        OrderDelivery.forDelivery(
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            null,
            "28034",
            ENCRYPTED,
            ENCRYPTED,
            new BigDecimal("3.50"),
            LocalDate.of(2026, 9, 20),
            LocalTime.of(10, 0),
            LocalTime.of(11, 0),
            null);

    assertThat(delivery.recipientNameEncrypted()).isEqualTo(ENCRYPTED);
    assertThat(delivery.postalCode()).isEqualTo("28034");
    assertThat(delivery.distanceKm()).isEqualByComparingTo("3.50");
  }

  @Test
  void rejectsASlotWhereFromIsNotBeforeTo() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> OrderDelivery.forPickup(LocalDate.of(2026, 9, 20), LocalTime.of(11, 0), LocalTime.of(11, 0)));
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=OrderDeliveryTest`
Expected: compilation failure — `OrderDelivery` does not exist yet.

- [ ] **Step 3: Implement `OrderDelivery`**

```java
package com.floristeriarosy.domain.model.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Where and when an order is delivered or picked up (order.md, rule 3.8). Exists only for {@link
 * OrderFulfillment#DELIVERY} and {@link OrderFulfillment#PICKUP} — {@code
 * OrderFulfillment#NONE} carries none, and is never wrapped by this type. For {@code PICKUP}, the
 * recipient and address fields are {@code null}: the buyer collects in person, there is nothing to
 * encrypt.
 */
public final class OrderDelivery {

  private final byte[] recipientNameEncrypted;
  private final byte[] recipientPhoneEncrypted;
  private final byte[] streetEncrypted;
  private final byte[] numberEncrypted;
  private final byte[] detailEncrypted;
  private final String postalCode;
  private final byte[] latitudeEncrypted;
  private final byte[] longitudeEncrypted;
  private final BigDecimal distanceKm;
  private final LocalDate deliveryDate;
  private final LocalTime slotFrom;
  private final LocalTime slotTo;
  private final byte[] cardMessageEncrypted;
  private final Instant createdAt;
  private final Instant updatedAt;

  private OrderDelivery(
      byte[] recipientNameEncrypted,
      byte[] recipientPhoneEncrypted,
      byte[] streetEncrypted,
      byte[] numberEncrypted,
      byte[] detailEncrypted,
      String postalCode,
      byte[] latitudeEncrypted,
      byte[] longitudeEncrypted,
      BigDecimal distanceKm,
      LocalDate deliveryDate,
      LocalTime slotFrom,
      LocalTime slotTo,
      byte[] cardMessageEncrypted,
      Instant createdAt,
      Instant updatedAt) {
    if (!slotFrom.isBefore(slotTo)) {
      throw new IllegalArgumentException("slotFrom must be before slotTo");
    }
    this.recipientNameEncrypted = recipientNameEncrypted;
    this.recipientPhoneEncrypted = recipientPhoneEncrypted;
    this.streetEncrypted = streetEncrypted;
    this.numberEncrypted = numberEncrypted;
    this.detailEncrypted = detailEncrypted;
    this.postalCode = postalCode;
    this.latitudeEncrypted = latitudeEncrypted;
    this.longitudeEncrypted = longitudeEncrypted;
    this.distanceKm = distanceKm;
    this.deliveryDate = deliveryDate;
    this.slotFrom = slotFrom;
    this.slotTo = slotTo;
    this.cardMessageEncrypted = cardMessageEncrypted;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * A home-delivery slot, with recipient and address (order.md, rule 3.8).
   *
   * @param recipientNameEncrypted the encrypted recipient name
   * @param recipientPhoneEncrypted the encrypted recipient phone
   * @param streetEncrypted the encrypted street
   * @param numberEncrypted the encrypted street number
   * @param detailEncrypted the encrypted address detail (floor, door), or {@code null}
   * @param postalCode the postal code, not encrypted
   * @param latitudeEncrypted the encrypted geocoded latitude
   * @param longitudeEncrypted the encrypted geocoded longitude
   * @param distanceKm the straight-line distance from the store, in kilometers
   * @param deliveryDate the requested delivery date
   * @param slotFrom the requested slot's start time
   * @param slotTo the requested slot's end time
   * @param cardMessageEncrypted the encrypted card message, or {@code null}
   * @return the new delivery slot
   * @throws IllegalArgumentException {@code slotFrom} is not before {@code slotTo}
   */
  public static OrderDelivery forDelivery(
      byte[] recipientNameEncrypted,
      byte[] recipientPhoneEncrypted,
      byte[] streetEncrypted,
      byte[] numberEncrypted,
      byte[] detailEncrypted,
      String postalCode,
      byte[] latitudeEncrypted,
      byte[] longitudeEncrypted,
      BigDecimal distanceKm,
      LocalDate deliveryDate,
      LocalTime slotFrom,
      LocalTime slotTo,
      byte[] cardMessageEncrypted) {
    return new OrderDelivery(
        recipientNameEncrypted,
        recipientPhoneEncrypted,
        streetEncrypted,
        numberEncrypted,
        detailEncrypted,
        postalCode,
        latitudeEncrypted,
        longitudeEncrypted,
        distanceKm,
        deliveryDate,
        slotFrom,
        slotTo,
        cardMessageEncrypted,
        null,
        null);
  }

  /**
   * A pickup slot: no recipient, no address — the buyer (or whoever they name in store) collects in
   * person (order.md, rule 3.8).
   *
   * @param deliveryDate the requested pickup date
   * @param slotFrom the requested slot's start time
   * @param slotTo the requested slot's end time
   * @return the new pickup slot
   * @throws IllegalArgumentException {@code slotFrom} is not before {@code slotTo}
   */
  public static OrderDelivery forPickup(LocalDate deliveryDate, LocalTime slotFrom, LocalTime slotTo) {
    return new OrderDelivery(
        null, null, null, null, null, null, null, null, null, deliveryDate, slotFrom, slotTo, null,
        null, null);
  }

  /**
   * Rebuilds a delivery/pickup slot from persisted state. Used only by the persistence mapper.
   *
   * @param recipientNameEncrypted the persisted encrypted recipient name, or {@code null} for pickup
   * @param recipientPhoneEncrypted the persisted encrypted recipient phone, or {@code null} for
   *     pickup
   * @param streetEncrypted the persisted encrypted street, or {@code null} for pickup
   * @param numberEncrypted the persisted encrypted street number, or {@code null} for pickup
   * @param detailEncrypted the persisted encrypted address detail, or {@code null}
   * @param postalCode the persisted postal code, or {@code null} for pickup
   * @param latitudeEncrypted the persisted encrypted latitude, or {@code null} for pickup
   * @param longitudeEncrypted the persisted encrypted longitude, or {@code null} for pickup
   * @param distanceKm the persisted distance in kilometers, or {@code null} for pickup
   * @param deliveryDate the persisted date
   * @param slotFrom the persisted slot start
   * @param slotTo the persisted slot end
   * @param cardMessageEncrypted the persisted encrypted card message, or {@code null}
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt delivery/pickup slot
   */
  public static OrderDelivery reconstitute(
      byte[] recipientNameEncrypted,
      byte[] recipientPhoneEncrypted,
      byte[] streetEncrypted,
      byte[] numberEncrypted,
      byte[] detailEncrypted,
      String postalCode,
      byte[] latitudeEncrypted,
      byte[] longitudeEncrypted,
      BigDecimal distanceKm,
      LocalDate deliveryDate,
      LocalTime slotFrom,
      LocalTime slotTo,
      byte[] cardMessageEncrypted,
      Instant createdAt,
      Instant updatedAt) {
    return new OrderDelivery(
        recipientNameEncrypted,
        recipientPhoneEncrypted,
        streetEncrypted,
        numberEncrypted,
        detailEncrypted,
        postalCode,
        latitudeEncrypted,
        longitudeEncrypted,
        distanceKm,
        deliveryDate,
        slotFrom,
        slotTo,
        cardMessageEncrypted,
        createdAt,
        updatedAt);
  }

  /**
   * @return the encrypted recipient name, or {@code null} for pickup
   */
  public byte[] recipientNameEncrypted() {
    return recipientNameEncrypted == null ? null : recipientNameEncrypted.clone();
  }

  /**
   * @return the encrypted recipient phone, or {@code null} for pickup
   */
  public byte[] recipientPhoneEncrypted() {
    return recipientPhoneEncrypted == null ? null : recipientPhoneEncrypted.clone();
  }

  /**
   * @return the encrypted street, or {@code null} for pickup
   */
  public byte[] streetEncrypted() {
    return streetEncrypted == null ? null : streetEncrypted.clone();
  }

  /**
   * @return the encrypted street number, or {@code null} for pickup
   */
  public byte[] numberEncrypted() {
    return numberEncrypted == null ? null : numberEncrypted.clone();
  }

  /**
   * @return the encrypted address detail, or {@code null}
   */
  public byte[] detailEncrypted() {
    return detailEncrypted == null ? null : detailEncrypted.clone();
  }

  /**
   * @return the postal code, or {@code null} for pickup
   */
  public String postalCode() {
    return postalCode;
  }

  /**
   * @return the encrypted geocoded latitude, or {@code null} for pickup
   */
  public byte[] latitudeEncrypted() {
    return latitudeEncrypted == null ? null : latitudeEncrypted.clone();
  }

  /**
   * @return the encrypted geocoded longitude, or {@code null} for pickup
   */
  public byte[] longitudeEncrypted() {
    return longitudeEncrypted == null ? null : longitudeEncrypted.clone();
  }

  /**
   * @return the straight-line distance from the store, in kilometers, or {@code null} for pickup
   */
  public BigDecimal distanceKm() {
    return distanceKm;
  }

  /**
   * @return the requested delivery or pickup date
   */
  public LocalDate deliveryDate() {
    return deliveryDate;
  }

  /**
   * @return the requested slot's start time
   */
  public LocalTime slotFrom() {
    return slotFrom;
  }

  /**
   * @return the requested slot's end time
   */
  public LocalTime slotTo() {
    return slotTo;
  }

  /**
   * @return the encrypted card message, or {@code null}
   */
  public byte[] cardMessageEncrypted() {
    return cardMessageEncrypted == null ? null : cardMessageEncrypted.clone();
  }

  /**
   * @return when the row was created, or {@code null} before the first save
   */
  public Instant createdAt() {
    return createdAt;
  }

  /**
   * @return when the row was last updated, or {@code null} before the first save
   */
  public Instant updatedAt() {
    return updatedAt;
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=OrderDeliveryTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/floristeriarosy/domain/model/order/OrderDelivery.java \
        src/test/java/com/floristeriarosy/domain/model/order/OrderDeliveryTest.java
git commit -m "feat(order): OrderDelivery entity (order.md §3.8)"
```

---

## Task 4: `Order` aggregate root

**Files:**
- Create: `src/main/java/com/floristeriarosy/domain/model/order/Order.java`
- Test: `src/test/java/com/floristeriarosy/domain/model/order/OrderTest.java`

**Interfaces:**
- Consumes: `OrderId`, `OrderNumber` (Task 1), `OrderChannel`, `OrderFulfillment`, `OrderStatus`
  (Task 1), `OrderItem` (Task 2), `OrderDelivery` (Task 3).
- Produces: `Order.place(id, orderNumber, channel, fulfillment, customerId,
  buyerFirstNameEncrypted, buyerLastNameEncrypted, buyerEmailEncrypted, buyerEmailHash,
  buyerPhoneEncrypted, buyerPhoneHash, items, delivery, subtotal, shippingCost)`,
  `Order.reconstitute(...)`, `.total()`, `.status()`, `.items()`, `.delivery()`, and one getter per
  field. Phase 3's `ConfirmCheckoutService` calls `Order.place(...)` directly; Phase 4's lifecycle
  services and Phase 6/7's mappers call the getters. **No transition methods yet** (`prepare()`,
  `ship()`, `complete()`, `reject()`, `cancel()`) — Phase 3/4 add them to this same file, each with
  its own test, when their calling service exists.

**A note on `customerId`:** `customer` is not implemented yet in this codebase (only `.gitkeep`
scaffolding) — there is no `CustomerId` value object to depend on. `Order.customerId()` is a plain
nullable `UUID` for now; tighten it to a real `CustomerId` type when `customer.md`'s minimal slice
(`FindOrCreateGuestCustomerUseCase`) lands, planned as part of Phase 3.

- [ ] **Step 1: Write the failing tests**

```java
package com.floristeriarosy.domain.model.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderItemId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Domain rules of {@link Order} (order.md, sections 1, 2, 3.9). */
class OrderTest {

  private static final byte[] ENCRYPTED = "encrypted".getBytes(StandardCharsets.UTF_8);

  private OrderItem oneRose() {
    return OrderItem.snapshot(
        OrderItemId.newId(), ProductId.newId(), "Rosa roja", Map.of(), new BigDecimal("10.00"), null,
        null, 2);
  }

  @Test
  void placeIsBornPendingWithTotalAsSubtotalPlusShipping() {
    Order order =
        Order.place(
            OrderId.newId(),
            OrderNumber.of("WEB-20260913-000001"),
            OrderChannel.WEB,
            OrderFulfillment.PICKUP,
            null,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            List.of(oneRose()),
            OrderDelivery.forPickup(LocalDate.of(2026, 9, 20), LocalTime.of(10, 0), LocalTime.of(11, 0)),
            new BigDecimal("20.00"),
            BigDecimal.ZERO);

    assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.total()).isEqualByComparingTo("20.00");
    assertThat(order.version()).isZero();
    assertThat(order.customerId()).isNull();
  }

  @Test
  void placeRejectsAnOrderWithNoItems() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                Order.place(
                    OrderId.newId(),
                    OrderNumber.of("WEB-20260913-000002"),
                    OrderChannel.WEB,
                    OrderFulfillment.NONE,
                    null,
                    ENCRYPTED,
                    ENCRYPTED,
                    ENCRYPTED,
                    ENCRYPTED,
                    ENCRYPTED,
                    ENCRYPTED,
                    List.of(),
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO));
  }

  @Test
  void totalAddsShippingCostToSubtotal() {
    Order order =
        Order.place(
            OrderId.newId(),
            OrderNumber.of("WEB-20260913-000003"),
            OrderChannel.WEB,
            OrderFulfillment.NONE,
            null,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            List.of(oneRose()),
            null,
            new BigDecimal("20.00"),
            new BigDecimal("5.00"));

    assertThat(order.total()).isEqualByComparingTo("25.00");
  }

  @Test
  void itemsAreDefensivelyCopiedAndUnmodifiable() {
    List<OrderItem> items = new java.util.ArrayList<>(List.of(oneRose()));
    Order order =
        Order.place(
            OrderId.newId(),
            OrderNumber.of("WEB-20260913-000004"),
            OrderChannel.WEB,
            OrderFulfillment.NONE,
            null,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            items,
            null,
            new BigDecimal("20.00"),
            BigDecimal.ZERO);

    items.clear();

    assertThat(order.items()).hasSize(1);
    assertThatIllegalArgumentException().isThrownBy(() -> order.items().add(oneRose()));
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=OrderTest`
Expected: compilation failure — `Order` does not exist yet.

- [ ] **Step 3: Implement `Order`**

```java
package com.floristeriarosy.domain.model.order;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root of the order module (order.md). A self-contained document: it carries its own
 * encrypted snapshot of the buyer and of every sold line, and survives the buyer's account being
 * archived or a product changing or being discontinued after the sale (ADR-007). An order that was
 * never paid never reaches this type — {@code order.md}, section 1.
 */
public final class Order {

  private static final Logger LOGGER = LoggerFactory.getLogger(Order.class);

  private final OrderId id;
  private final OrderNumber orderNumber;
  private final OrderChannel channel;
  private final OrderFulfillment fulfillment;
  private OrderStatus status;
  private final UUID customerId;
  private final byte[] buyerFirstNameEncrypted;
  private final byte[] buyerLastNameEncrypted;
  private final byte[] buyerEmailEncrypted;
  private final byte[] buyerEmailHash;
  private final byte[] buyerPhoneEncrypted;
  private final byte[] buyerPhoneHash;
  private final List<OrderItem> items;
  private final OrderDelivery delivery;
  private final BigDecimal subtotal;
  private final BigDecimal shippingCost;
  private final Instant placedAt;
  private LocalDate retentionUntil;
  private Instant personalDataPurgedAt;
  private final long version;
  private final Instant createdAt;
  private Instant updatedAt;

  private Order(
      OrderId id,
      OrderNumber orderNumber,
      OrderChannel channel,
      OrderFulfillment fulfillment,
      OrderStatus status,
      UUID customerId,
      byte[] buyerFirstNameEncrypted,
      byte[] buyerLastNameEncrypted,
      byte[] buyerEmailEncrypted,
      byte[] buyerEmailHash,
      byte[] buyerPhoneEncrypted,
      byte[] buyerPhoneHash,
      List<OrderItem> items,
      OrderDelivery delivery,
      BigDecimal subtotal,
      BigDecimal shippingCost,
      Instant placedAt,
      LocalDate retentionUntil,
      Instant personalDataPurgedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    if (items.isEmpty()) {
      throw new IllegalArgumentException("items must not be empty");
    }
    this.id = Objects.requireNonNull(id, "id");
    this.orderNumber = Objects.requireNonNull(orderNumber, "orderNumber");
    this.channel = Objects.requireNonNull(channel, "channel");
    this.fulfillment = Objects.requireNonNull(fulfillment, "fulfillment");
    this.status = Objects.requireNonNull(status, "status");
    this.customerId = customerId;
    this.buyerFirstNameEncrypted = buyerFirstNameEncrypted;
    this.buyerLastNameEncrypted = buyerLastNameEncrypted;
    this.buyerEmailEncrypted = buyerEmailEncrypted;
    this.buyerEmailHash = buyerEmailHash;
    this.buyerPhoneEncrypted = buyerPhoneEncrypted;
    this.buyerPhoneHash = buyerPhoneHash;
    this.items = List.copyOf(items);
    this.delivery = delivery;
    this.subtotal = Objects.requireNonNull(subtotal, "subtotal");
    this.shippingCost = Objects.requireNonNull(shippingCost, "shippingCost");
    this.placedAt = placedAt;
    this.retentionUntil = retentionUntil;
    this.personalDataPurgedAt = personalDataPurgedAt;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Places a new, already-paid order (order.md, section 3.2, Fase 3a). Born {@code PENDING} — an
   * order that was never charged never reaches this factory (section 1).
   *
   * @param id application-generated identifier, minted at Fase 1 (ADR-017)
   * @param orderNumber the application-generated business identifier (section 3.1)
   * @param channel the sales channel
   * @param fulfillment how the order reaches the customer
   * @param customerId the linked customer, or {@code null} for an unlinked guest checkout
   * @param buyerFirstNameEncrypted the buyer's encrypted first name
   * @param buyerLastNameEncrypted the buyer's encrypted last name
   * @param buyerEmailEncrypted the buyer's encrypted email
   * @param buyerEmailHash the buyer's email HMAC
   * @param buyerPhoneEncrypted the buyer's encrypted phone
   * @param buyerPhoneHash the buyer's phone HMAC
   * @param items the sold lines, at least one
   * @param delivery the delivery/pickup slot, or {@code null} for {@link OrderFulfillment#NONE}
   * @param subtotal the sum of every line's total, before shipping
   * @param shippingCost the computed shipping cost, zero for {@code PICKUP}/{@code NONE}
   * @return the new, {@code PENDING} order
   * @throws IllegalArgumentException {@code items} is empty
   */
  public static Order place(
      OrderId id,
      OrderNumber orderNumber,
      OrderChannel channel,
      OrderFulfillment fulfillment,
      UUID customerId,
      byte[] buyerFirstNameEncrypted,
      byte[] buyerLastNameEncrypted,
      byte[] buyerEmailEncrypted,
      byte[] buyerEmailHash,
      byte[] buyerPhoneEncrypted,
      byte[] buyerPhoneHash,
      List<OrderItem> items,
      OrderDelivery delivery,
      BigDecimal subtotal,
      BigDecimal shippingCost) {
    LOGGER.debug(
        "place id={} orderNumber={} channel={} fulfillment={} itemCount={}",
        id,
        orderNumber,
        channel,
        fulfillment,
        items.size());
    Order result =
        new Order(
            id,
            orderNumber,
            channel,
            fulfillment,
            OrderStatus.PENDING,
            customerId,
            buyerFirstNameEncrypted,
            buyerLastNameEncrypted,
            buyerEmailEncrypted,
            buyerEmailHash,
            buyerPhoneEncrypted,
            buyerPhoneHash,
            items,
            delivery,
            subtotal,
            shippingCost,
            Instant.now(),
            null,
            null,
            0L,
            null,
            null);
    LOGGER.debug("place id={} -> placed", id);
    return result;
  }

  /**
   * Rebuilds an order from persisted state. Used only by the persistence mapper.
   *
   * @param id the persisted identifier
   * @param orderNumber the persisted business identifier
   * @param channel the persisted sales channel
   * @param fulfillment the persisted fulfillment mode
   * @param status the persisted fulfillment status
   * @param customerId the persisted linked customer, or {@code null}
   * @param buyerFirstNameEncrypted the persisted encrypted buyer first name, or {@code null} if
   *     purged
   * @param buyerLastNameEncrypted the persisted encrypted buyer last name, or {@code null} if purged
   * @param buyerEmailEncrypted the persisted encrypted buyer email, or {@code null} if purged
   * @param buyerEmailHash the persisted buyer email HMAC, or {@code null} if purged
   * @param buyerPhoneEncrypted the persisted encrypted buyer phone, or {@code null} if purged
   * @param buyerPhoneHash the persisted buyer phone HMAC, or {@code null} if purged
   * @param items the persisted sold lines
   * @param delivery the persisted delivery/pickup slot, or {@code null}
   * @param subtotal the persisted subtotal
   * @param shippingCost the persisted shipping cost
   * @param placedAt when the order was placed
   * @param retentionUntil the persisted PII retention deadline, or {@code null}
   * @param personalDataPurgedAt when PII was purged, or {@code null} if not yet purged
   * @param version the optimistic-locking version (ADR-009)
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt order
   */
  public static Order reconstitute(
      OrderId id,
      OrderNumber orderNumber,
      OrderChannel channel,
      OrderFulfillment fulfillment,
      OrderStatus status,
      UUID customerId,
      byte[] buyerFirstNameEncrypted,
      byte[] buyerLastNameEncrypted,
      byte[] buyerEmailEncrypted,
      byte[] buyerEmailHash,
      byte[] buyerPhoneEncrypted,
      byte[] buyerPhoneHash,
      List<OrderItem> items,
      OrderDelivery delivery,
      BigDecimal subtotal,
      BigDecimal shippingCost,
      Instant placedAt,
      LocalDate retentionUntil,
      Instant personalDataPurgedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Order(
        id,
        orderNumber,
        channel,
        fulfillment,
        status,
        customerId,
        buyerFirstNameEncrypted,
        buyerLastNameEncrypted,
        buyerEmailEncrypted,
        buyerEmailHash,
        buyerPhoneEncrypted,
        buyerPhoneHash,
        items,
        delivery,
        subtotal,
        shippingCost,
        placedAt,
        retentionUntil,
        personalDataPurgedAt,
        version,
        createdAt,
        updatedAt);
  }

  /**
   * @return {@code subtotal} plus {@code shippingCost} — mirrors the {@code total} generated column
   *     (order.md, section 2)
   */
  public BigDecimal total() {
    return subtotal.add(shippingCost);
  }

  /**
   * @return the application-generated identifier
   */
  public OrderId id() {
    return id;
  }

  /**
   * @return the application-generated business identifier
   */
  public OrderNumber orderNumber() {
    return orderNumber;
  }

  /**
   * @return the sales channel
   */
  public OrderChannel channel() {
    return channel;
  }

  /**
   * @return how the order reaches the customer
   */
  public OrderFulfillment fulfillment() {
    return fulfillment;
  }

  /**
   * @return the fulfillment status
   */
  public OrderStatus status() {
    return status;
  }

  /**
   * @return the linked customer, or {@code null} for an unlinked guest checkout
   */
  public UUID customerId() {
    return customerId;
  }

  /**
   * @return the buyer's encrypted first name, or {@code null} if purged
   */
  public byte[] buyerFirstNameEncrypted() {
    return buyerFirstNameEncrypted == null ? null : buyerFirstNameEncrypted.clone();
  }

  /**
   * @return the buyer's encrypted last name, or {@code null} if purged
   */
  public byte[] buyerLastNameEncrypted() {
    return buyerLastNameEncrypted == null ? null : buyerLastNameEncrypted.clone();
  }

  /**
   * @return the buyer's encrypted email, or {@code null} if purged
   */
  public byte[] buyerEmailEncrypted() {
    return buyerEmailEncrypted == null ? null : buyerEmailEncrypted.clone();
  }

  /**
   * @return the buyer's email HMAC, or {@code null} if purged
   */
  public byte[] buyerEmailHash() {
    return buyerEmailHash == null ? null : buyerEmailHash.clone();
  }

  /**
   * @return the buyer's encrypted phone, or {@code null} if purged
   */
  public byte[] buyerPhoneEncrypted() {
    return buyerPhoneEncrypted == null ? null : buyerPhoneEncrypted.clone();
  }

  /**
   * @return the buyer's phone HMAC, or {@code null} if purged
   */
  public byte[] buyerPhoneHash() {
    return buyerPhoneHash == null ? null : buyerPhoneHash.clone();
  }

  /**
   * @return the sold lines, unmodifiable
   */
  public List<OrderItem> items() {
    return items;
  }

  /**
   * @return the delivery/pickup slot, or {@code null} for {@link OrderFulfillment#NONE}
   */
  public OrderDelivery delivery() {
    return delivery;
  }

  /**
   * @return the sum of every line's total, before shipping
   */
  public BigDecimal subtotal() {
    return subtotal;
  }

  /**
   * @return the computed shipping cost
   */
  public BigDecimal shippingCost() {
    return shippingCost;
  }

  /**
   * @return when the order was placed, or {@code null} before the first save
   */
  public Instant placedAt() {
    return placedAt;
  }

  /**
   * @return the PII retention deadline, or {@code null} before the order reaches a terminal status
   */
  public LocalDate retentionUntil() {
    return retentionUntil;
  }

  /**
   * @return when PII was purged, or {@code null} if not yet purged
   */
  public Instant personalDataPurgedAt() {
    return personalDataPurgedAt;
  }

  /**
   * @return the optimistic-locking version (ADR-009)
   */
  public long version() {
    return version;
  }

  /**
   * @return when the row was created, or {@code null} before the first save
   */
  public Instant createdAt() {
    return createdAt;
  }

  /**
   * @return when the row was last updated, or {@code null} before the first save
   */
  public Instant updatedAt() {
    return updatedAt;
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=OrderTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/floristeriarosy/domain/model/order/Order.java \
        src/test/java/com/floristeriarosy/domain/model/order/OrderTest.java
git commit -m "feat(order): Order aggregate root, shell only (order.md §1-3.2)"
```

---

## Task 5: Payment value objects and enums

**Files:**
- Create: `src/main/java/com/floristeriarosy/domain/model/payment/valueobject/PaymentId.java`
- Create: `src/main/java/com/floristeriarosy/domain/model/payment/PaymentMethod.java`
- Create: `src/main/java/com/floristeriarosy/domain/model/payment/PaymentProvider.java`
- Create: `src/main/java/com/floristeriarosy/domain/model/payment/PaymentStatus.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `PaymentId.newId()`/`.of(UUID)`/`.value()`,
  `PaymentMethod{CARD_ONLINE,CASH,DATAPHONE,INTERFLORA}`, `PaymentProvider{REDSYS,MANUAL}`,
  `PaymentStatus{PENDING,AUTHORIZED,CAPTURED,CANCELED,FAILED}` — Phase 2 (Payment capability) and
  every phase after it that touches `payment` uses these exact names.

No dedicated test, same reasoning as Task 1.

- [ ] **Step 1: Create `PaymentId`**

```java
package com.floristeriarosy.domain.model.payment.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of a {@code Payment} (never assigned by the database). */
public final class PaymentId {

  private final UUID value;

  /**
   * @param value the identifier value
   * @throws NullPointerException {@code value} is {@code null}
   */
  private PaymentId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * @return a new, random identifier for a payment being initiated
   */
  public static PaymentId newId() {
    return new PaymentId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value, e.g. from a persisted row
   * @return the wrapped identifier
   */
  public static PaymentId of(UUID value) {
    return new PaymentId(value);
  }

  /**
   * @return the raw UUID value
   */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof PaymentId paymentId && value.equals(paymentId.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
```

- [ ] **Step 2: Create the three enums**

```java
package com.floristeriarosy.domain.model.payment;

/** How the customer pays (payment.md, section 2). */
public enum PaymentMethod {
  CARD_ONLINE,
  CASH,
  DATAPHONE,
  INTERFLORA
}
```

```java
package com.floristeriarosy.domain.model.payment;

/**
 * Who actually captures the charge (payment.md, rule 3.1). {@code payments.provider} has no
 * database {@code CHECK} constraint — this enum is the domain-side safety net.
 */
public enum PaymentProvider {
  /** {@code CARD_ONLINE}, via the Redsys TPV Virtual redirect (ADR-017). */
  REDSYS,
  /** {@code CASH}, {@code DATAPHONE}, {@code INTERFLORA} — captured directly, no gateway call. */
  MANUAL
}
```

```java
package com.floristeriarosy.domain.model.payment;

/** Lifecycle of a payment (payment.md, section 2), independent of {@code orders.status}. */
public enum PaymentStatus {
  PENDING,
  AUTHORIZED,
  CAPTURED,
  CANCELED,
  FAILED
}
```

- [ ] **Step 3: Compile**

Run: `mvn -q compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/floristeriarosy/domain/model/payment/valueobject/PaymentId.java \
        src/main/java/com/floristeriarosy/domain/model/payment/PaymentMethod.java \
        src/main/java/com/floristeriarosy/domain/model/payment/PaymentProvider.java \
        src/main/java/com/floristeriarosy/domain/model/payment/PaymentStatus.java
git commit -m "feat(payment): payment value objects and enums (payment.md §2)"
```

---

## Task 6: `Payment` aggregate root

**Files:**
- Create: `src/main/java/com/floristeriarosy/domain/model/payment/Payment.java`
- Test: `src/test/java/com/floristeriarosy/domain/model/payment/PaymentTest.java`

**Interfaces:**
- Consumes: `PaymentId` (Task 5), `com.floristeriarosy.domain.model.order.valueobject.OrderId`
  (Task 1), `PaymentMethod`, `PaymentProvider`, `PaymentStatus` (Task 5).
- Produces: `Payment.initiate(id, orderId, method, provider, amount)`, `Payment.reconstitute(...)`,
  getters (`id()`, `orderId()`, `method()`, `provider()`, `providerPaymentIntentId()`,
  `providerReference()`, `amount()`, `status()`, `refundedAmount()`, `authorizedAt()`,
  `capturedAt()`, `canceledAt()`, `refundedAt()`, `failureReason()`, `refundReason()`,
  `createdAt()`, `updatedAt()`). **No `capture()`/`fail()`/`refund()` methods yet** — Phase 2
  (`ChargeOrderPaymentService`/`RefundOrderPaymentService`) adds them to this same file, each with
  its own test, when their calling service exists.

- [ ] **Step 1: Write the failing test**

```java
package com.floristeriarosy.domain.model.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Domain rules of {@link Payment} (payment.md, section 2). */
class PaymentTest {

  @Test
  void initiateIsBornPendingWithNothingCapturedOrRefundedYet() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS,
            new BigDecimal("42.00"));

    assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
    assertThat(payment.amount()).isEqualByComparingTo("42.00");
    assertThat(payment.refundedAmount()).isEqualByComparingTo("0");
    assertThat(payment.capturedAt()).isNull();
    assertThat(payment.providerPaymentIntentId()).isNull();
  }

  @Test
  void initiateForManualProviderCarriesNoGatewayReference() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CASH, PaymentProvider.MANUAL,
            new BigDecimal("15.00"));

    assertThat(payment.provider()).isEqualTo(PaymentProvider.MANUAL);
    assertThat(payment.providerReference()).isNull();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=PaymentTest`
Expected: compilation failure — `Payment` does not exist yet.

- [ ] **Step 3: Implement `Payment`**

```java
package com.floristeriarosy.domain.model.payment;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root of the payment module (payment.md). The source of truth of an order's payment,
 * independent of {@code orders.status} (payment.md, section 1).
 */
public final class Payment {

  private static final Logger LOGGER = LoggerFactory.getLogger(Payment.class);

  private final PaymentId id;
  private final OrderId orderId;
  private final PaymentMethod method;
  private final PaymentProvider provider;
  private String providerPaymentIntentId;
  private String providerReference;
  private final BigDecimal amount;
  private PaymentStatus status;
  private BigDecimal refundedAmount;
  private Instant authorizedAt;
  private Instant capturedAt;
  private Instant canceledAt;
  private Instant refundedAt;
  private String failureReason;
  private String refundReason;
  private final Instant createdAt;
  private Instant updatedAt;

  private Payment(
      PaymentId id,
      OrderId orderId,
      PaymentMethod method,
      PaymentProvider provider,
      String providerPaymentIntentId,
      String providerReference,
      BigDecimal amount,
      PaymentStatus status,
      BigDecimal refundedAmount,
      Instant authorizedAt,
      Instant capturedAt,
      Instant canceledAt,
      Instant refundedAt,
      String failureReason,
      String refundReason,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.orderId = Objects.requireNonNull(orderId, "orderId");
    this.method = Objects.requireNonNull(method, "method");
    this.provider = Objects.requireNonNull(provider, "provider");
    this.providerPaymentIntentId = providerPaymentIntentId;
    this.providerReference = providerReference;
    this.amount = Objects.requireNonNull(amount, "amount");
    this.status = Objects.requireNonNull(status, "status");
    this.refundedAmount = Objects.requireNonNull(refundedAmount, "refundedAmount");
    this.authorizedAt = authorizedAt;
    this.capturedAt = capturedAt;
    this.canceledAt = canceledAt;
    this.refundedAt = refundedAt;
    this.failureReason = failureReason;
    this.refundReason = refundReason;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Initiates a new payment for an order, {@code PENDING}, nothing captured yet (payment.md,
   * section 3.2). Capturing, failing or refunding it is added in Phase 2, alongside the capability
   * that implements {@code OrderPaymentPort}.
   *
   * @param id application-generated identifier
   * @param orderId the order this payment belongs to
   * @param method how the customer pays
   * @param provider {@code REDSYS} for {@code CARD_ONLINE}, {@code MANUAL} for the rest
   * @param amount the amount to charge
   * @return the new, {@code PENDING} payment
   */
  public static Payment initiate(
      PaymentId id, OrderId orderId, PaymentMethod method, PaymentProvider provider, BigDecimal amount) {
    LOGGER.debug(
        "initiate id={} orderId={} method={} provider={} amount={}", id, orderId, method, provider,
        amount);
    Payment result =
        new Payment(
            id,
            orderId,
            method,
            provider,
            null,
            null,
            amount,
            PaymentStatus.PENDING,
            BigDecimal.ZERO,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    LOGGER.debug("initiate id={} -> initiated", id);
    return result;
  }

  /**
   * Rebuilds a payment from persisted state. Used only by the persistence mapper.
   *
   * @param id the persisted identifier
   * @param orderId the persisted order reference
   * @param method the persisted payment method
   * @param provider the persisted provider
   * @param providerPaymentIntentId the persisted gateway charge id, or {@code null}
   * @param providerReference the persisted gateway refund id, or {@code null}
   * @param amount the persisted amount
   * @param status the persisted status
   * @param refundedAmount the persisted refunded amount so far
   * @param authorizedAt when the charge was authorized, or {@code null}
   * @param capturedAt when the charge was captured, or {@code null}
   * @param canceledAt when the charge was canceled, or {@code null}
   * @param refundedAt when it was last refunded, or {@code null}
   * @param failureReason why the charge failed, or {@code null}
   * @param refundReason the manual refund's reason, or {@code null}
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt payment
   */
  public static Payment reconstitute(
      PaymentId id,
      OrderId orderId,
      PaymentMethod method,
      PaymentProvider provider,
      String providerPaymentIntentId,
      String providerReference,
      BigDecimal amount,
      PaymentStatus status,
      BigDecimal refundedAmount,
      Instant authorizedAt,
      Instant capturedAt,
      Instant canceledAt,
      Instant refundedAt,
      String failureReason,
      String refundReason,
      Instant createdAt,
      Instant updatedAt) {
    return new Payment(
        id,
        orderId,
        method,
        provider,
        providerPaymentIntentId,
        providerReference,
        amount,
        status,
        refundedAmount,
        authorizedAt,
        capturedAt,
        canceledAt,
        refundedAt,
        failureReason,
        refundReason,
        createdAt,
        updatedAt);
  }

  /**
   * @return the application-generated identifier
   */
  public PaymentId id() {
    return id;
  }

  /**
   * @return the order this payment belongs to
   */
  public OrderId orderId() {
    return orderId;
  }

  /**
   * @return how the customer pays
   */
  public PaymentMethod method() {
    return method;
  }

  /**
   * @return who actually captures the charge
   */
  public PaymentProvider provider() {
    return provider;
  }

  /**
   * @return the gateway's charge id, or {@code null} until captured
   */
  public String providerPaymentIntentId() {
    return providerPaymentIntentId;
  }

  /**
   * @return the gateway's refund id, or {@code null} until refunded
   */
  public String providerReference() {
    return providerReference;
  }

  /**
   * @return the amount to charge
   */
  public BigDecimal amount() {
    return amount;
  }

  /**
   * @return the lifecycle status
   */
  public PaymentStatus status() {
    return status;
  }

  /**
   * @return the amount refunded so far, zero if none
   */
  public BigDecimal refundedAmount() {
    return refundedAmount;
  }

  /**
   * @return when the charge was authorized, or {@code null}
   */
  public Instant authorizedAt() {
    return authorizedAt;
  }

  /**
   * @return when the charge was captured, or {@code null}
   */
  public Instant capturedAt() {
    return capturedAt;
  }

  /**
   * @return when the charge was canceled, or {@code null}
   */
  public Instant canceledAt() {
    return canceledAt;
  }

  /**
   * @return when it was last refunded, or {@code null}
   */
  public Instant refundedAt() {
    return refundedAt;
  }

  /**
   * @return why the charge failed, or {@code null}
   */
  public String failureReason() {
    return failureReason;
  }

  /**
   * @return the manual refund's reason, or {@code null} for an automatic refund
   */
  public String refundReason() {
    return refundReason;
  }

  /**
   * @return when the row was created, or {@code null} before the first save
   */
  public Instant createdAt() {
    return createdAt;
  }

  /**
   * @return when the row was last updated, or {@code null} before the first save
   */
  public Instant updatedAt() {
    return updatedAt;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=PaymentTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full build**

Run: `mvn -q verify`
Expected: `BUILD SUCCESS` — Checkstyle, all tests (including ArchUnit — the new packages must not
import Spring/JPA/JDBC/HTTP, verify no violation appears), and SpotBugs all green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/floristeriarosy/domain/model/payment/Payment.java \
        src/test/java/com/floristeriarosy/domain/model/payment/PaymentTest.java
git commit -m "feat(payment): Payment aggregate root, shell only (payment.md §1-3.2)"
```

---

## Self-Review

**Spec coverage:** `order.md` §2 (orders/order_items/order_deliveries columns) → Tasks 1-4.
`order.md` §3.5 (line snapshot) → Task 2. `order.md` §3.8 (delivery/pickup) → Task 3. `order.md`
§3.9 (status enum values) → Task 1. `payment.md` §2 (payments columns) → Tasks 5-6. No migration
needed — confirmed against `V1`/`V3`/`V6`/`V9`/`V10`/`V14`/`V18`, all columns this phase's domain
fields map to already exist.

**Deliberately not covered here** (belongs to a later phase, not a gap): `OrderErrorCode`/
`PaymentErrorCode` (first real error condition arrives in Phase 3/Phase 2 — an error code with no
thrower yet is dead code); `Order`'s transition methods and `Payment`'s capture/refund methods
(same reasoning); `Customer`'s minimal slice (`FindOrCreateGuestCustomerUseCase`) — net-new
dependency discovered while designing Phase 3, scoped to that phase, not Foundation; all
persistence (JPA entities, mappers, repositories) — Phase 3 onward, once a service exists to call
them.

**Placeholder scan:** none — every step has real, complete code.

**Type consistency:** `OrderId`, `OrderItemId`, `OrderNumber`, `OrderChannel`, `OrderFulfillment`,
`OrderStatus`, `OrderItem`, `OrderDelivery`, `Order`, `PaymentId`, `PaymentMethod`,
`PaymentProvider`, `PaymentStatus`, `Payment` — every one of these exact names is used identically
across all six tasks; cross-checked against the "Produces" list of the task that defines it.

---

Plan complete and saved to `docs/superpowers/plans/2026-09-13-order-payment-foundation.md`. Two
execution options:

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks,
   fast iteration.
2. **Inline Execution** — execute tasks in this session, batch execution with checkpoints.

A third option in this project: hand the plan to the **IMPLEMENTER** peer session already running
for this repo, and have **REVIEWER** check its work — mirrors the coder/tester/reviewer agent
split this project already uses.

Which approach?
