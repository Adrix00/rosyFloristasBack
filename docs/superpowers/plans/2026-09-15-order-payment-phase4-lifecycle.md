# Phase 4 — Order lifecycle (order.md §3.9, §3.10)

Branch: `feature/orderPayments`. Builds on Phase 3b (merged commits up to `e9bf5aa`). Scope: the
five fulfillment transitions (`prepare`, `ship`, `complete`, `reject`, `cancel`) and the reversal
component `reject`/`cancel` share (order.md §3.10). Stays entirely inside the `order` module —
`payment`'s `OrderPaymentPort.refund()` and `RefundOrderPaymentService` already exist from Phase 2
and need no changes.

**Explicitly out of scope** (not needed to exercise any transition, which all address an order by
its UUID `id`, never by a list): `GET /orders/all` (admin paginated listing) and the richer admin
`GET /orders/{id}` view. Both are separate read use cases order.md §8's `OrderReadPort` already
anticipates (`findAllForAdmin`); deferred to whichever later phase builds the admin order panel.
`POST /orders/counter` (order.md §11, counter sales) stays "pendiente" as it already was.

## State graph recap (order.md §3.9)

```
PENDING ──(prepare)──> PREPARING ──┬─(DELIVERY: ship)──> OUT_FOR_DELIVERY ──(complete)──> DELIVERED
                                    └─(PICKUP/NONE: complete)───────────────────────────> DELIVERED

PENDING ──(reject, admin only)────────────> REJECTED
PENDING or PREPARING ──(cancel)──────────> CANCELLED   (admin: PENDING or PREPARING; customer: PENDING only)
```

Every transition is admin-only except `cancel` from `PENDING`, which the owning customer may also
trigger. `prepare`/`ship` generate no notification (notification.md §3.1: "preparar y salir a
reparto no generan correo"). `complete` fires `ORDER_DELIVERED`. `reject`/`cancel` fire the
reversal: stock `ADJUSTMENT`, discount `release()`, `OrderPaymentPort.refund()` (always full,
payment.md §3.3), and a notification — `ORDER_REJECTED`/`ORDER_CANCELLED` to the customer when an
admin acts, `STAFF_ORDER_CANCELLED_BY_CUSTOMER` to the shop when the customer cancels their own —
plus `REFUND_ISSUED` to the customer in every reversal case.

**Two distinct 409s, by design (order.md §9):** `ORDER_INVALID_TRANSITION` is the generic
wrong-state error every transition can raise; `ORDER_NOT_CANCELABLE` is `cancel`'s customer-facing
variant specifically — a customer trying to cancel a `PREPARING` order gets the friendlier,
cancel-specific code, an admin doing the same gets the generic one. This is the one place the error
code depends on *who* is asking, so the role check and the throw both live in the Service, not in
`Order` itself.

## Design decisions carried into the tasks below

1. **`Order`'s transition methods stay simple state setters with a narrow internal guard**
   (`IllegalStateException`, never surfaced to a caller), exactly mirroring `Payment.capture()` /
   `Payment.fail()` / `Payment.refundInFull()`'s `requireStatus()` pattern already in this codebase.
   The *real*, user-facing precondition check (the one that decides between
   `ORDER_INVALID_TRANSITION` and `ORDER_NOT_CANCELABLE`, or that needs the caller's role) lives in
   the Service, before it calls the domain method. Two layers doing the identical check would be
   redundant (ponytail rung 1); the Service's check is the one that must exist because it is
   role-aware and error-code-aware, so `Order`'s own check stays a defensive last resort, same as
   `Payment`'s.
2. **`OrderReversalComponent`** (new, plain `@Component`, no port/interface — it is not swapped for
   anything, so ADR-003's capability-port pattern does not apply here) is the "mismo componente
   interno" order.md §7 says `RejectOrderService`/`CancelOrderService` must share. It does exactly
   the four bullets of §3.10: stock reversal, discount release, refund, notification. It is
   constructor-injected into both services, not exposed as a use case.
3. **`OrderDtoMapper`** (new, small static mapper in `application.order.mapper`) replaces the
   `toDto`/`toDto(Item)` pair currently duplicated privately inside `GetOrderService` (and, before
   this phase, about to be duplicated a third, fourth and fifth time by the new services). Five call
   sites of identical mapping logic justifies extracting it now (CLAUDE.md: "no utility classes
   unless explicitly justified" — this is). `GetOrderService` is updated to use it; its behavior does
   not change.
4. **`order_status_history` is new ground** — Phase 3b never wrote to it (an order's *birth* status
   is not a transition; the table only ever recorded nothing until now). `OrderWritePort` gets one
   new method, `recordStatusChange`, alongside the existing `save`. It is a plain insert-only audit
   row (no `@Version`, same category as `payments`/`order_items` per ADR-009) — no read side is
   needed yet since no endpoint surfaces `order_status_history` in this phase (`OrderStatusHistoryResponse`
   is listed in order.md §6 for the eventual `GET /orders/{orderNumber}` enrichment, not required to
   close this phase's scope — flagged as a follow-up, not built here to avoid speculative code with no
   caller yet).
5. **The single `/orders/{id}/cancel` endpoint serves both roles**, exactly as order.md §4 lists it
   once under "Público" and once under "Administración" at the identical path — same pattern already
   used by `GET /orders/{orderNumber}` (`OrderController.resolveCustomerId`) for a dual-mode endpoint.
   `@PreAuthorize("isAuthenticated()")` on the controller (bars anonymous callers only); the
   Service branches on which id it received (`adminUserId` vs `customerId`, exactly one non-null).

---

## Task 1 — Domain: transition methods, new exceptions

**Files:**
- `src/main/java/com/floristeriarosy/domain/model/order/Order.java` (edit — add methods)
- `src/main/java/com/floristeriarosy/domain/exception/order/OrderInvalidTransitionException.java` (new)
- `src/main/java/com/floristeriarosy/domain/exception/order/OrderNotCancelableException.java` (new)
- `src/test/java/com/floristeriarosy/domain/model/order/OrderTest.java` (edit — add cases)

Add to `Order` (after `total()`, before the getters), each logging entry/exit per CLAUDE.md:

```java
  /**
   * Moves the order into preparation (order.md, section 3.9).
   *
   * @throws IllegalStateException the order is not {@code PENDING} — the caller must have already
   *     checked this and thrown the user-facing {@code OrderInvalidTransitionException} itself;
   *     this is a last-resort guard, not the real precondition check
   */
  public void prepare() {
    LOGGER.debug("prepare id={} status={}", id, status);
    requireStatus(OrderStatus.PENDING);
    status = OrderStatus.PREPARING;
    LOGGER.debug("prepare id={} -> preparing", id);
  }

  /**
   * Sends a {@code DELIVERY} order out for delivery (order.md, section 3.9).
   *
   * @throws IllegalStateException the order is not {@code PREPARING}, or {@code fulfillment} is not
   *     {@code DELIVERY} — same caveat as {@link #prepare()}
   */
  public void ship() {
    LOGGER.debug("ship id={} status={} fulfillment={}", id, status, fulfillment);
    requireStatus(OrderStatus.PREPARING);
    if (fulfillment != OrderFulfillment.DELIVERY) {
      throw new IllegalStateException("Order " + id + " fulfillment is " + fulfillment + ", expected DELIVERY");
    }
    status = OrderStatus.OUT_FOR_DELIVERY;
    LOGGER.debug("ship id={} -> out for delivery", id);
  }

  /**
   * Marks the order delivered or picked up (order.md, section 3.9): from {@code OUT_FOR_DELIVERY}
   * for {@code DELIVERY}, or directly from {@code PREPARING} for {@code PICKUP}/{@code NONE}.
   *
   * @throws IllegalStateException the order is not in the expected status for its fulfillment mode
   *     — same caveat as {@link #prepare()}
   */
  public void complete() {
    LOGGER.debug("complete id={} status={} fulfillment={}", id, status, fulfillment);
    requireStatus(fulfillment == OrderFulfillment.DELIVERY ? OrderStatus.OUT_FOR_DELIVERY : OrderStatus.PREPARING);
    status = OrderStatus.DELIVERED;
    LOGGER.debug("complete id={} -> delivered", id);
  }

  /**
   * Rejects the order before preparation starts (order.md, section 3.9) — the admin cannot fulfill
   * it. Reversal (stock, discount, refund, notification) is a separate concern, driven by the
   * caller after this returns (section 3.10).
   *
   * @throws IllegalStateException the order is not {@code PENDING} — same caveat as {@link
   *     #prepare()}
   */
  public void reject() {
    LOGGER.debug("reject id={} status={}", id, status);
    requireStatus(OrderStatus.PENDING);
    status = OrderStatus.REJECTED;
    LOGGER.debug("reject id={} -> rejected", id);
  }

  /**
   * Cancels the order (order.md, section 3.9) — from {@code PENDING} or {@code PREPARING}. Which of
   * those two the caller is actually allowed to use depends on who is asking (admin vs. the owning
   * customer, section 3.9) and is checked by the caller before this is invoked, not here — same
   * caveat as {@link #prepare()}. Reversal is a separate concern (section 3.10).
   *
   * @throws IllegalStateException the order is not {@code PENDING} or {@code PREPARING}
   */
  public void cancel() {
    LOGGER.debug("cancel id={} status={}", id, status);
    if (status != OrderStatus.PENDING && status != OrderStatus.PREPARING) {
      throw new IllegalStateException("Order " + id + " is " + status + ", expected PENDING or PREPARING");
    }
    status = OrderStatus.CANCELLED;
    LOGGER.debug("cancel id={} -> cancelled", id);
  }

  /**
   * @param expected the status this order must currently have
   * @throws IllegalStateException the current status is not {@code expected}
   */
  private void requireStatus(OrderStatus expected) {
    if (status != expected) {
      throw new IllegalStateException("Order " + id + " is " + status + ", expected " + expected);
    }
  }
```

New exceptions, both `ConflictException` + `HasErrorCode`, same shape as `OrderNotFoundException`:

```java
// OrderInvalidTransitionException.java
package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * An admin action does not apply to the order's current status or fulfillment mode (order.md, §9)
 * — e.g. {@code ship} on a {@code PICKUP} order, or {@code reject} on a {@code PREPARING} one.
 */
public final class OrderInvalidTransitionException extends ConflictException implements HasErrorCode {

  /** @param message what the caller should see */
  public OrderInvalidTransitionException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#ORDER_INVALID_TRANSITION} */
  @Override
  public String errorCode() {
    return OrderErrorCode.ORDER_INVALID_TRANSITION.name();
  }
}
```

```java
// OrderNotCancelableException.java
package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * A customer tried to cancel their own order outside {@code PENDING} (order.md, §9) — the
 * cancel-specific, friendlier sibling of {@link OrderInvalidTransitionException}, which an admin
 * gets instead for the same order in the same state.
 */
public final class OrderNotCancelableException extends ConflictException implements HasErrorCode {

  /** @param message what the caller should see */
  public OrderNotCancelableException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#ORDER_NOT_CANCELABLE} */
  @Override
  public String errorCode() {
    return OrderErrorCode.ORDER_NOT_CANCELABLE.name();
  }
}
```

Both error codes already exist in `OrderErrorCode` (added when order.md was first written, never
thrown until now) — no change needed there.

**Tests (`OrderTest`, TDD, red first):** for each of `prepare`/`ship`/`complete`/`reject`/`cancel` —
happy path from a `place()`d order (and, for `complete`/`ship`, from an order walked through the
prior transitions) asserts the new `status()`; and one case per method asserting
`IllegalStateException` from a wrong starting status. `ship()` also gets a case for a `PICKUP` order
in `PREPARING` throwing `IllegalStateException` (wrong fulfillment, not wrong status). Use the
existing `OrderTest` fixtures/builders already in the file rather than re-deriving them.

---

## Task 2 — `order_status_history`: port, entity, adapter

**Files:**
- `src/main/java/com/floristeriarosy/application/order/port/out/OrderWritePort.java` (edit)
- `src/main/java/com/floristeriarosy/infrastructure/persistence/entity/order/OrderStatusHistoryEntity.java` (new)
- `src/main/java/com/floristeriarosy/infrastructure/persistence/jpa/order/repository/OrderStatusHistoryJpaRepository.java` (new)
- `src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderPersistenceAdapter.java` (edit)
- Adapter test (edit)

Add to `OrderWritePort`:

```java
  /**
   * Appends one row to {@code order_status_history} (order.md, section 3.9): "cada transición
   * añade una fila". Insert-only, no optimistic lock of its own (ADR-009 — same category as {@code
   * payments}/{@code order_items}, written once by exactly one call site per transition).
   *
   * @param orderId the order that changed
   * @param status the status it changed to
   * @param changedByAdminId the admin who triggered it, or {@code null} when the customer did
   * @param reason the admin's stated reason for {@code REJECTED}/{@code CANCELLED}, or {@code null}
   *     for every other transition and for a customer-initiated cancel
   */
  void recordStatusChange(OrderId orderId, OrderStatus status, UUID changedByAdminId, String reason);
```

`OrderStatusHistoryEntity` — plain `@Entity` over the `order_status_history` table from `V1`/`V14`
(`id`, `order_id`, `status`, `changed_by_admin_id`, `changed_at` defaulted by the database, `reason`
from `V14`). No back-reference to `OrderEntity` needed (nothing reads through it yet, per design
decision 4) — a bare `orderId` column is enough, mirroring how `StockMovementEntity` holds a plain
`productId` rather than a JPA association. Constructor takes `(UUID id, UUID orderId, OrderStatus
status, UUID changedByAdminId, String reason)`; `changedAt` is DB-defaulted (`now()` per `V1`), so
leave it unmapped for insert and add a `@Column(name = "changed_at", insertable = false, updatable
= false) private Instant changedAt;` with just a getter, matching how the codebase already leaves
other DB-defaulted timestamps out of the write path (check `V1`'s `admin_audit_log` or `discount`
history-style table for the closest existing precedent of a DB-defaulted-timestamp entity before
inventing a new pattern).

`OrderStatusHistoryJpaRepository extends JpaRepository<OrderStatusHistoryEntity, UUID>` — no custom
query methods needed yet (nothing reads this table in this phase).

`OrderPersistenceAdapter.recordStatusChange`: builds a new `OrderStatusHistoryEntity` with a fresh
`UUID.randomUUID()` id and `jpaRepository.save(...)` (the *history* repository, not `OrderJpaRepository`
— inject `OrderStatusHistoryJpaRepository` alongside the existing one). Plain insert, no
optimistic-lock handling needed (nothing to conflict with — each row is independent and nothing
ever updates one).

---

## Task 3 — `OrderDtoMapper` extraction

**Files:**
- `src/main/java/com/floristeriarosy/application/order/mapper/OrderDtoMapper.java` (new)
- `src/main/java/com/floristeriarosy/application/order/service/GetOrderService.java` (edit — use it)

Move `GetOrderService`'s private `toDto(Order)`/`toDto(OrderItem)` verbatim into a new
`public final class OrderDtoMapper` with a private constructor and two `public static` methods,
`toDto(Order)` and the item one kept `private` (only the aggregate-level method has callers outside
this class). `GetOrderService` calls `OrderDtoMapper.toDto(order)` instead of its own private
method; delete the two methods it had. No behavior change — confirm with the existing
`GetOrderServiceTest` still passing unmodified.

---

## Task 4 — Commands and input ports

**Files (all new):**
- `application/order/command/PrepareOrderCommand.java` — `record PrepareOrderCommand(UUID orderId, UUID adminUserId)`
- `application/order/command/ShipOrderCommand.java` — `record ShipOrderCommand(UUID orderId, UUID adminUserId)`
- `application/order/command/CompleteOrderCommand.java` — `record CompleteOrderCommand(UUID orderId, UUID adminUserId)`
- `application/order/command/RejectOrderCommand.java` — `record RejectOrderCommand(UUID orderId, UUID adminUserId, String reason)`
- `application/order/command/CancelOrderCommand.java` — `record CancelOrderCommand(UUID orderId, UUID customerId, UUID adminUserId, String reason)` — `@param customerId the caller, when a customer is cancelling their own order, or {@code null} for an admin call` / `@param adminUserId the caller, when an admin is cancelling, or {@code null} for a customer call` — exactly one of the two is non-null, enforced by the controller construction, not by the record itself (mirrors how `GetOrderQuery` already carries an either/or shape for customer vs. guest)
- `application/order/port/in/PrepareOrderUseCase.java`, `ShipOrderUseCase.java`, `CompleteOrderUseCase.java`, `RejectOrderUseCase.java`, `CancelOrderUseCase.java` — each `OrderDto execute(XxxCommand command)`, Javadoc `@throws` listing `OrderNotFoundException` plus whichever of `OrderInvalidTransitionException`/`OrderNotCancelableException` applies (see Task 1's state graph).

Every command record and use case interface: full Javadoc per CLAUDE.md (`@param` per field/parameter, `@return`, `@throws`).

---

## Task 5 — `OrderReversalComponent` (shared reversal, order.md §3.10)

**Files:**
- `src/main/java/com/floristeriarosy/application/order/service/OrderReversalComponent.java` (new)
- Unit test (new) — mock every port, assert the four effects happen for a representative order

```java
package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.discount.port.out.DiscountReservationPort;
import com.floristeriarosy.application.inventory.port.in.RegisterStockMovementUseCase;
import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;
import com.floristeriarosy.application.notification.port.in.RegisterNotificationUseCase;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.domain.exception.inventory.InventoryNotManagedException;
import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The reversal {@code RejectOrderService} and {@code CancelOrderService} share (order.md, section
 * 3.10): reverses every line's stock reservation, releases every line's discount reservation,
 * refunds the payment in full, and registers the notification each case calls for. Not a use case
 * — nothing outside {@code order}'s own services calls this, and it is never swapped for another
 * implementation, so it is a plain internal component rather than a capability port (ADR-003).
 */
@Component
class OrderReversalComponent {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderReversalComponent.class);
  private static final String NOTE = "order lifecycle reversal";

  private final RegisterStockMovementUseCase registerStockMovementUseCase;
  private final DiscountReservationPort discountReservationPort;
  private final OrderPaymentPort orderPaymentPort;
  private final RegisterNotificationUseCase registerNotificationUseCase;

  OrderReversalComponent(
      RegisterStockMovementUseCase registerStockMovementUseCase,
      DiscountReservationPort discountReservationPort,
      OrderPaymentPort orderPaymentPort,
      RegisterNotificationUseCase registerNotificationUseCase) {
    this.registerStockMovementUseCase = registerStockMovementUseCase;
    this.discountReservationPort = discountReservationPort;
    this.orderPaymentPort = orderPaymentPort;
    this.registerNotificationUseCase = registerNotificationUseCase;
  }

  /**
   * @param order the order being rejected or cancelled, already moved to its terminal status
   * @param customerNotificationType {@code ORDER_REJECTED}, {@code ORDER_CANCELLED} or {@code
   *     STAFF_ORDER_CANCELLED_BY_CUSTOMER} — the one notification.md §3.1 catalog type this
   *     reversal fires besides {@code REFUND_ISSUED}
   */
  void reverse(Order order, String customerNotificationType) {
    LOGGER.debug(
        "reverse orderId={} notificationType={}", order.id(), customerNotificationType);
    for (OrderItem item : order.items()) {
      releaseStock(item);
      releaseDiscount(item);
    }
    orderPaymentPort.refund(order.id(), order.orderNumber());
    UUID recipientCustomerId = customerNotificationType.startsWith("STAFF_") ? null : order.customerId();
    registerNotificationUseCase.execute(
        new RegisterNotificationCommand(customerNotificationType, order.id().value(), recipientCustomerId));
    registerNotificationUseCase.execute(
        new RegisterNotificationCommand("REFUND_ISSUED", order.id().value(), order.customerId()));
    LOGGER.debug("reverse orderId={} -> reversed", order.id());
  }

  /**
   * @param item the sold line whose stock reservation must be reversed, if it had one
   */
  private void releaseStock(OrderItem item) {
    try {
      registerStockMovementUseCase.execute(
          item.productId().value(), StockMovementType.ADJUSTMENT, item.quantity(), null, NOTE);
    } catch (InventoryNotManagedException e) {
      LOGGER.debug("reverse releaseStock skipped productId={} (unmanaged)", item.productId());
    }
  }

  /**
   * @param item the sold line whose discount reservation must be released, if it had one
   */
  private void releaseDiscount(OrderItem item) {
    if (item.discountId() == null) {
      return;
    }
    discountReservationPort.release(item.discountId(), item.quantity());
  }
}
```

(Add the `import java.util.UUID;` for `recipientCustomerId`.) This is deliberately the same
`releaseStock`/`releaseDiscount` shape as `ConfirmCheckoutService.compensate()` — same underlying
rule (order.md §3.6's `SALE` reversal is always "an `ADJUSTMENT` of the opposite sign, never
delete the original"), applied here to already-materialized `OrderItem`s instead of
`PendingCheckoutPayload.Item`s. Package-private and un-annotated with any use-case interface on
purpose — it is wiring, not a capability.

---

## Task 6 — `PrepareOrderService`, `ShipOrderService`, `CompleteOrderService`

**Files (all new, plus one test each):**
- `application/order/service/PrepareOrderService.java`
- `application/order/service/ShipOrderService.java`
- `application/order/service/CompleteOrderService.java`

All three follow the same shape — load, check the *specific* precondition (not delegated to
`Order`, see design decision 1), mutate, persist, record history, return the DTO. `PrepareOrderService`
as the full example (the other two differ only in which status check and domain method they call,
and `CompleteOrderService` reads `order.fulfillment()` to know which prior status is legal):

```java
package com.floristeriarosy.application.order.service;

import com.floristeriarosy.application.order.command.PrepareOrderCommand;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.mapper.OrderDtoMapper;
import com.floristeriarosy.application.order.port.in.PrepareOrderUseCase;
import com.floristeriarosy.application.order.port.out.OrderWritePort;
import com.floristeriarosy.application.order.port.out.OrderReadPort;
import com.floristeriarosy.domain.exception.order.OrderInvalidTransitionException;
import com.floristeriarosy.domain.exception.order.OrderNotFoundException;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderStatus;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link PrepareOrderUseCase}: {@code PENDING} → {@code PREPARING} (order.md §3.9). */
@Service
@Transactional
public class PrepareOrderService implements PrepareOrderUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(PrepareOrderService.class);

  private final OrderReadPort orderReadPort;
  private final OrderWritePort orderWritePort;

  public PrepareOrderService(OrderReadPort orderReadPort, OrderWritePort orderWritePort) {
    this.orderReadPort = orderReadPort;
    this.orderWritePort = orderWritePort;
  }

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public OrderDto execute(PrepareOrderCommand command) {
    LOGGER.debug("prepareOrder orderId={} adminUserId={}", command.orderId(), command.adminUserId());
    Order order = loadOrder(command.orderId());
    if (order.status() != OrderStatus.PENDING) {
      throw new OrderInvalidTransitionException("Order " + command.orderId() + " is not PENDING");
    }
    order.prepare();
    Order saved = orderWritePort.save(order);
    orderWritePort.recordStatusChange(saved.id(), OrderStatus.PREPARING, command.adminUserId(), null);
    OrderDto result = OrderDtoMapper.toDto(saved);
    LOGGER.debug("prepareOrder orderId={} -> preparing", command.orderId());
    return result;
  }

  private Order loadOrder(java.util.UUID orderId) {
    return orderReadPort
        .findById(OrderId.of(orderId))
        .orElseThrow(() -> new OrderNotFoundException("Order " + orderId + " not found"));
  }
}
```

(Write the `import java.util.UUID;` properly rather than the inline-qualified form above, which is
only there to keep this plan's snippet short — same for every class below.)

`ShipOrderService`: same shape; precondition is `order.status() != PREPARING ||
order.fulfillment() != OrderFulfillment.DELIVERY` → `OrderInvalidTransitionException`; calls
`order.ship()`; records `OUT_FOR_DELIVERY`.

`CompleteOrderService`: precondition is
`order.fulfillment() == DELIVERY ? order.status() != OUT_FOR_DELIVERY : order.status() != PREPARING`
→ `OrderInvalidTransitionException`; calls `order.complete()`; records `DELIVERED`; **and** registers
`ORDER_DELIVERED` (`notification.md §3.1: "El pedido pasa a DELIVERED"`) — the one of these three
services that sends an email, so it additionally takes `RegisterNotificationUseCase` in its
constructor and calls
`registerNotificationUseCase.execute(new RegisterNotificationCommand("ORDER_DELIVERED", saved.id().value(), saved.customerId()))`
right after `recordStatusChange`.

Each gets a unit test (mocked ports) covering: happy path, `OrderNotFoundException` when the id
does not exist, and `OrderInvalidTransitionException` for a wrong starting status/fulfillment.
`ShipOrderServiceTest` and `CompleteOrderServiceTest` each add the fulfillment-mismatch case too.

---

## Task 7 — `RejectOrderService`, `CancelOrderService`

**Files (new, plus tests):**
- `application/order/service/RejectOrderService.java`
- `application/order/service/CancelOrderService.java`

`RejectOrderService` — admin-only, `reason` always present (validated `@NotBlank` at the DTO layer,
Task 8; the service does not re-check it):

```java
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public OrderDto execute(RejectOrderCommand command) {
    LOGGER.debug("rejectOrder orderId={} adminUserId={}", command.orderId(), command.adminUserId());
    Order order = loadOrder(command.orderId());
    if (order.status() != OrderStatus.PENDING) {
      throw new OrderInvalidTransitionException("Order " + command.orderId() + " is not PENDING");
    }
    order.reject();
    Order saved = orderWritePort.save(order);
    orderWritePort.recordStatusChange(saved.id(), OrderStatus.REJECTED, command.adminUserId(), command.reason());
    reversalComponent.reverse(saved, "ORDER_REJECTED");
    OrderDto result = OrderDtoMapper.toDto(saved);
    LOGGER.debug("rejectOrder orderId={} -> rejected", command.orderId());
    return result;
  }
```

(Constructor takes `OrderReadPort`, `OrderWritePort`, `OrderReversalComponent`; same
`loadOrder` helper as Task 6.)

`CancelOrderService` — the dual-actor one. Precondition and error code depend on who is calling
(design decision 1); reason is validated here, not at the DTO layer, because "required" depends on
that same role split:

```java
  @Override
  @PreAuthorize("isAuthenticated()")
  public OrderDto execute(CancelOrderCommand command) {
    LOGGER.debug(
        "cancelOrder orderId={} customerId={} adminUserId={}",
        command.orderId(), command.customerId(), command.adminUserId());
    Order order = loadOrder(command.orderId());
    boolean byAdmin = command.adminUserId() != null;
    if (byAdmin) {
      requireReason(command.reason());
      if (order.status() != OrderStatus.PENDING && order.status() != OrderStatus.PREPARING) {
        throw new OrderInvalidTransitionException("Order " + command.orderId() + " is not PENDING or PREPARING");
      }
    } else {
      requireOwnership(order, command.customerId());
      if (order.status() != OrderStatus.PENDING) {
        throw new OrderNotCancelableException("Order " + command.orderId() + " is not PENDING");
      }
    }
    order.cancel();
    Order saved = orderWritePort.save(order);
    orderWritePort.recordStatusChange(saved.id(), OrderStatus.CANCELLED, command.adminUserId(), command.reason());
    String notificationType = byAdmin ? "ORDER_CANCELLED" : "STAFF_ORDER_CANCELLED_BY_CUSTOMER";
    reversalComponent.reverse(saved, notificationType);
    OrderDto result = OrderDtoMapper.toDto(saved);
    LOGGER.debug("cancelOrder orderId={} -> cancelled", command.orderId());
    return result;
  }

  /**
   * @param reason the admin's stated reason
   * @throws OrderValidationFailedException {@code reason} is blank — role-conditional, so it is
   *     checked here rather than with a DTO-level {@code @NotBlank} that would also apply to the
   *     customer's optional {@code reason} (order.md, {@code CancelOrderRequest})
   */
  private void requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new OrderValidationFailedException("reason is required when an admin cancels an order");
    }
  }

  /**
   * @param order the order being cancelled
   * @param customerId the caller's id
   * @throws OrderNotFoundException the caller does not own the order — same not-found-not-forbidden
   *     convention {@code GetOrderService.ensureOwnership} already uses, so a probing customer
   *     cannot learn an order exists by getting a different error for "not yours" vs. "does not
   *     exist"
   */
  private void requireOwnership(Order order, UUID customerId) {
    if (!java.util.Objects.equals(order.customerId(), customerId)) {
      throw new OrderNotFoundException("Order " + order.id() + " not found");
    }
  }
```

Check `OrderValidationFailedException`'s actual constructor shape (it already exists,
`domain/exception/order/OrderValidationFailedException.java`, used by `CheckoutService`'s own
cross-field check per commit `810ba67`) and match it rather than guessing.

Both services' tests: `RejectOrderServiceTest` — happy path (asserts `OrderReversalComponent.reverse`
invoked with `"ORDER_REJECTED"`), not-`PENDING` → `OrderInvalidTransitionException`, not-found.
`CancelOrderServiceTest` — four happy paths (admin from `PENDING`, admin from `PREPARING`, customer
from `PENDING` who owns it, each asserting the right `reverse(...)` notification-type argument),
admin-blank-reason → `OrderValidationFailedException`, admin-wrong-status →
`OrderInvalidTransitionException`, customer-wrong-status → `OrderNotCancelableException`,
customer-not-owner → `OrderNotFoundException`, not-found.

---

## Task 8 — Web layer: requests and endpoints

**Files:**
- `infrastructure/web/request/order/RejectOrderRequest.java` (new) — `record RejectOrderRequest(@NotBlank @Size(max = 500) String reason) {}`
- `infrastructure/web/request/order/CancelOrderRequest.java` (new) — `record CancelOrderRequest(@Size(max = 500) String reason) {}`
- `infrastructure/web/controller/order/OrderController.java` (edit — add five endpoints)
- Controller test (edit)

Add to `OrderController` (constructor gains the five new use cases):

```java
  @PostMapping("/orders/{id}/prepare")
  public ResponseEntity<OrderResponse> prepare(@PathVariable UUID id) {
    LOGGER.debug("POST /orders/{}/prepare", id);
    OrderDto order = prepareOrderUseCase.execute(new PrepareOrderCommand(id, CurrentAdminResolver.resolve()));
    LOGGER.debug("POST /orders/{}/prepare -> 200", id);
    return ResponseEntity.ok(toResponse(order));
  }

  @PostMapping("/orders/{id}/ship")
  public ResponseEntity<OrderResponse> ship(@PathVariable UUID id) {
    ...same shape, ShipOrderCommand...
  }

  @PostMapping("/orders/{id}/complete")
  public ResponseEntity<OrderResponse> complete(@PathVariable UUID id) {
    ...same shape, CompleteOrderCommand...
  }

  @PostMapping("/orders/{id}/reject")
  public ResponseEntity<OrderResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectOrderRequest request) {
    ...RejectOrderCommand(id, CurrentAdminResolver.resolve(), request.reason())...
  }

  @PostMapping("/orders/{id}/cancel")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<OrderResponse> cancel(@PathVariable UUID id, @RequestBody(required = false) CancelOrderRequest request) {
    LOGGER.debug("POST /orders/{}/cancel", id);
    UUID adminUserId = CurrentAdminResolver.resolve();
    UUID customerId = adminUserId == null ? resolveCustomerId() : null;
    String reason = request == null ? null : request.reason();
    OrderDto order = cancelOrderUseCase.execute(new CancelOrderCommand(id, customerId, adminUserId, reason));
    LOGGER.debug("POST /orders/{}/cancel -> 200", id);
    return ResponseEntity.ok(toResponse(order));
  }
```

`prepare`/`ship`/`complete`/`reject` need no `@PreAuthorize` on the controller — each Service already
carries `@PreAuthorize("hasRole('ADMIN')")` per Task 6/7, matching `InventoryAlertController`'s
"`ADMIN`, `@PreAuthorize` on the service" convention. `cancel` is the one exception: it must accept
*either* role, so the check that belongs on the controller is only "not anonymous"
(`@PreAuthorize("isAuthenticated()")`), same as `GET /orders`.

`cancel`'s request body is optional — a customer cancelling their own `PENDING` order may send no
body at all (`reason` is optional for them); `@RequestBody(required = false)` plus the null-check
above covers that without a synthetic default object. Reuse `OrderController`'s existing
`resolveCustomerId()` private method (already reads the `CUSTOMER`-subject JWT) — no changes needed
to it.

`toResponse(OrderDto)` already exists in `OrderController` (Phase 3b) and needs no changes; all five
new endpoints reuse it as-is.

---

## Self-review

- **Ownership check on `cancel`, guest orders**: a guest (no account) cannot call `POST
  /orders/{id}/cancel` as "the owning customer" at all — there is no JWT to authenticate as. This
  matches order.md §4 exactly ("Cliente dueño"): a guest's only recourse to cancel is contacting the
  shop, which then does it as an admin action. Not a gap — the spec draws this line, not this plan.
- **`OrderReversalComponent` and `ConfirmCheckoutService.compensate()` stay two separate, un-shared
  private helpers** despite near-identical bodies, because order.md §7 only asks
  `RejectOrderService`/`CancelOrderService` to share one one component with each other — it never
  asks either of them to share with `ConfirmCheckoutService`, and the two operate on different types
  (`OrderItem` vs. `PendingCheckoutPayload.Item`) at different lifecycle moments (after an order
  exists vs. before it ever will). Unifying them would mean introducing a common item abstraction
  purely to satisfy DRY across a boundary the spec itself does not draw — speculative, so skipped.
- **`REFUND_ISSUED` is registered by `order`, not `payment`**: `payment` module has no
  `RegisterNotificationUseCase` dependency wired in from Phase 2 (it predates `notification`
  entirely), and giving it one now, for a single call site, would be a bigger change than letting
  the one caller that already has both ports (the reversal component) fire it. Flagged here rather
  than silently decided, since `payment.md` assigns the type without saying which module's code
  registers it.
- **No new `OrderErrorCode` values needed** — `ORDER_INVALID_TRANSITION`/`ORDER_NOT_CANCELABLE`
  already existed in the enum since order.md was first written; this phase is simply their first
  real throw site.
- **`mvn clean verify` before considering this phase done**, watching in particular for: the
  `InfrastructureArchitectureTest` rule about persistence adapters and `application..command`
  (Task 5/6/7 pass primitives or `Order`/`OrderItem` domain objects across that boundary, never a
  `command` record, so it should be clean, but confirm); and that `OrderStatusHistoryEntity`'s
  DB-defaulted `changed_at` actually comes back populated after `save()` (Hibernate needs
  `@Column(insertable = false)` plus either a `@Generated` annotation or a post-insert refresh to see
  a database default — verify which this codebase's closest precedent actually does before assuming
  the snippet in Task 2 is complete; do not guess if no precedent exists, ask ARCHITECT).

---

**When done:** normal incremental commits per task, then hand this phase to REVIEWER same as every
prior one. `/ultrareview` is optional now (CLAUDE.md's phase gate no longer requires it) — REVIEWER's
own manual pass plus a green `mvn clean verify` is what closes this phase.
