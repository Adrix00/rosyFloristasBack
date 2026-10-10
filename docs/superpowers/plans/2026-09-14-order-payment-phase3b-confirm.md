# Order + Payment — Phase 3b: Confirmation and Guest Linking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redsys's notification actually materializes or reverts a pedido. `ConfirmCheckoutService`
decrypts Phase 3a's pending payload, links/creates a guest customer, writes `orders`/`order_items`/
`order_deliveries`/`payments`/`order_status_history`/`notifications` in one transaction, clears the
cart, and resolves the idempotency lock — or, on decline/timeout, reverses the Fase 1 reservation.
`GET /orders/{orderNumber}` and `GET /orders` become real. This is the last task of the original
"Phase 3: Checkout" — after this, `POST /checkout` works end to end for a real customer.

**Architecture:** Same capability-based-port discipline as every prior phase. Two more minimal
module slices, sized to exactly what this phase calls (same reasoning as Phase 1's `customer`
scoping decision): `customer` (`FindOrCreateGuestCustomerUseCase` only — registration, addresses,
password, admin management are a future `customer.md` feature) and `notification`
(`RegisterNotificationUseCase` only, write-only — actually sending email is `notification.md`'s own
scheduled-task feature, out of scope here). `OrderPaymentPort` (Phase 2) gains one method,
`confirmCharge`, for the one thing it could not yet do: record what Redsys's *notification*
confirmed, as opposed to what `charge` returns synchronously.

**Tech Stack:** Same as Phase 3a — Jackson 3 (`tools.jackson.*`, confirmed by Phase 3a's own
research — do not use `com.fasterxml.jackson.*`), JPA for the new entities (ADR-002), JDBC only
where Phase 3a's own precedent already used it.

**Spec:** `docs/features/order.md` (sections 3.2, 3.9, 3.10, 3.11, 4, 6, 7, 8),
`docs/features/customer.md` (section 3.2 only), `docs/features/notification.md` (sections 1, 3.1,
3.3 only), `docs/architecture/ADR/ADR-017-card-payment-via-redsys-redirect.md`. Builds on Phases
1-3a, all merged to `feature/orderPayments` and green.

## Global Constraints

- No wildcard imports. Constructor injection only. Every method — public and private — gets
  Javadoc.
- **Before writing any code, read the actual current state of**: `OrderPaymentPort.java`,
  `PendingCheckoutPayload.java`, `CheckoutIdempotencyPort.java`, `CheckoutService.java` (all listed
  verbatim above in this session's own verification, but re-read them anyway — Phase 3a's
  implementer already deviated from its own plan's drafts in documented, sensible ways; treat this
  plan's code the same way: a draft to adapt, not a transcript to copy).
- `customer`'s minimal slice is genuinely minimal: `Customer` domain (shell: `CustomerType`,
  `CustomerStatus`, encrypted fields, `registerGuest`/`reconstitute`/`updateGuestContact`),
  `CustomerReadPort.findByEmailHash`, `CustomerWritePort.save`, `FindOrCreateGuestCustomerUseCase`.
  No registration endpoint, no address book, no password, no admin listing — those need the real
  `customer.md` feature later.
- `notification`'s minimal slice is genuinely minimal: a `Notification` entity (no domain aggregate
  needed — insert-only, no business methods, no lifecycle to protect), `RegisterNotificationUseCase`.
  No sending, no scheduled task, no retry endpoint — `notification.md`'s own future feature.
- Money-moving and PII-writing steps happen in one `@Transactional` method
  (`ConfirmCheckoutService`), same discipline as `CheckoutService`'s Fase 1.
- `orders.total` is a DB generated column — never set it from Java; the `Order` domain's `total()`
  is a computed method, not a persisted field (Phase 1's own design, unchanged).

---

## Task 1: `OrderPaymentPort.confirmCharge` and `ConfirmOrderPaymentService`

**Files:**
- Modify: `src/main/java/com/floristeriarosy/application/order/port/out/OrderPaymentPort.java`
- Create: `src/main/java/com/floristeriarosy/application/payment/port/in/ConfirmOrderPaymentUseCase.java`
- Create: `src/main/java/com/floristeriarosy/application/payment/service/ConfirmOrderPaymentService.java`
- Modify: `src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderPaymentAdapter.java`
- Test: `src/test/java/com/floristeriarosy/application/payment/service/ConfirmOrderPaymentServiceTest.java`

**Interfaces:**
- Consumes: `Payment`/`PaymentMethod`/`PaymentProvider` (Phase 1), `PaymentWritePort` (Phase 2),
  `OrderId`/`OrderNumber` (Phase 1).
- Produces: `OrderPaymentPort.confirmCharge(orderId, orderNumber, method, amount,
  providerPaymentIntentId)` → `Payment` (a saved, `CAPTURED` payment) — Task 5's
  `ConfirmCheckoutService` calls this exactly once, only on approval. Declined/timed-out attempts
  never call this — there is nothing to record (order.md §1: unpaid orders leave no trace).

- [ ] **Step 1: Read `OrderPaymentPort.java`'s current file in full**, then add the new method to
      the interface (do not remove `charge`/`refund`):

```java
  /**
   * @param orderId the order being confirmed
   * @param orderNumber the business identifier
   * @param method how the customer paid — always {@code CARD_ONLINE} in practice (order.md §3.2,
   *     Fase 3a); the parameter stays general because {@link Payment#initiate} takes it
   * @param amount the confirmed amount
   * @param providerPaymentIntentId Redsys's authorization reference from the notification
   * @return the saved, {@code CAPTURED} payment
   */
  Payment confirmCharge(OrderId orderId, OrderNumber orderNumber, PaymentMethod method, BigDecimal amount, String providerPaymentIntentId);
```

- [ ] **Step 2: Write the failing test**

```java
package com.floristeriarosy.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.payment.port.out.PaymentWritePort;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ConfirmOrderPaymentServiceTest {

  private final PaymentWritePort writePort = mock(PaymentWritePort.class);
  private final ConfirmOrderPaymentService service = new ConfirmOrderPaymentService(writePort);

  @Test
  void confirmChargeSavesAnAlreadyCapturedPayment() {
    when(writePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Payment result =
        service.execute(
            OrderId.newId(), com.floristeriarosy.domain.model.order.valueobject.OrderNumber.of("WEB-20260914-000001"),
            PaymentMethod.CARD_ONLINE, new BigDecimal("42.00"), "redsys-auth-1");

    assertThat(result.status()).isEqualTo(com.floristeriarosy.domain.model.payment.PaymentStatus.CAPTURED);
    assertThat(result.providerPaymentIntentId()).isEqualTo("redsys-auth-1");
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=ConfirmOrderPaymentServiceTest`

- [ ] **Step 4: Create `ConfirmOrderPaymentUseCase` and `ConfirmOrderPaymentService`**

```java
package com.floristeriarosy.application.payment.port.in;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;

/** Implements {@code OrderPaymentPort.confirmCharge} (ADR-017). */
public interface ConfirmOrderPaymentUseCase {

  /**
   * @param orderId the order being confirmed
   * @param orderNumber the business identifier
   * @param method how the customer paid
   * @param amount the confirmed amount
   * @param providerPaymentIntentId the gateway's authorization reference
   * @return the saved, captured payment
   */
  Payment execute(OrderId orderId, OrderNumber orderNumber, PaymentMethod method, BigDecimal amount, String providerPaymentIntentId);
}
```

```java
package com.floristeriarosy.application.payment.service;

import com.floristeriarosy.application.payment.port.in.ConfirmOrderPaymentUseCase;
import com.floristeriarosy.application.payment.port.out.PaymentWritePort;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.payment.PaymentProvider;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link ConfirmOrderPaymentUseCase}: Redsys already approved this charge (the
 * notification is the proof) — this just records it. Never called on decline (order.md §1: an
 * unpaid order leaves no trace, nothing to record).
 */
@Service
public class ConfirmOrderPaymentService implements ConfirmOrderPaymentUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmOrderPaymentService.class);

  private final PaymentWritePort writePort;

  /**
   * @param writePort persists the captured payment
   */
  public ConfirmOrderPaymentService(PaymentWritePort writePort) {
    this.writePort = writePort;
  }

  /**
   * @param orderId the order being confirmed
   * @param orderNumber the business identifier, unused here but part of the contract (ADR-017's
   *     signature diversification already happened before this is called)
   * @param method how the customer paid
   * @param amount the confirmed amount
   * @param providerPaymentIntentId the gateway's authorization reference
   * @return the saved, captured payment
   */
  @Override
  public Payment execute(OrderId orderId, OrderNumber orderNumber, PaymentMethod method, BigDecimal amount, String providerPaymentIntentId) {
    LOGGER.debug("confirmOrderPayment orderId={} providerPaymentIntentId={}", orderId, providerPaymentIntentId);
    Payment payment = Payment.initiate(PaymentId.newId(), orderId, method, PaymentProvider.REDSYS, amount);
    payment.capture(providerPaymentIntentId, Instant.now());
    Payment result = writePort.save(payment);
    LOGGER.debug("confirmOrderPayment orderId={} -> saved", orderId);
    return result;
  }
}
```

- [ ] **Step 5: Wire it into `OrderPaymentAdapter`**

Read the adapter's current file, add the `ConfirmOrderPaymentUseCase` collaborator (constructor
injection) and the `confirmCharge` override delegating to it — same shape as the existing
`charge`/`refund` delegations.

- [ ] **Step 6: Run tests, then the full build**

Run: `mvn -q test -Dtest=ConfirmOrderPaymentServiceTest` then `mvn -q clean verify`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/floristeriarosy/application/order/port/out/OrderPaymentPort.java \
        src/main/java/com/floristeriarosy/application/payment/port/in/ConfirmOrderPaymentUseCase.java \
        src/main/java/com/floristeriarosy/application/payment/service/ConfirmOrderPaymentService.java \
        src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderPaymentAdapter.java \
        src/test/java/com/floristeriarosy/application/payment/service/ConfirmOrderPaymentServiceTest.java
git commit -m "feat(order): OrderPaymentPort.confirmCharge (ADR-017)"
```

---

## Task 2: `customer`'s minimal domain — `Customer`, `CustomerId`, enums

**Files:**
- Create: `src/main/java/com/floristeriarosy/domain/model/customer/valueobject/CustomerId.java`
- Create: `src/main/java/com/floristeriarosy/domain/model/customer/CustomerType.java`
- Create: `src/main/java/com/floristeriarosy/domain/model/customer/CustomerStatus.java`
- Create: `src/main/java/com/floristeriarosy/domain/model/customer/Customer.java`
- Test: `src/test/java/com/floristeriarosy/domain/model/customer/CustomerTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `Customer.registerGuest(id, emailEncrypted, emailHash, firstNameEncrypted,
  lastNameEncrypted, phoneEncrypted, phoneHash)` → new `GUEST`, `Customer.reconstitute(...)`,
  `Customer.updateGuestContact(firstNameEncrypted, lastNameEncrypted, phoneEncrypted,
  phoneHash)` (customer.md, rule 3.2: a returning `GUEST`'s contact info is refreshed, not
  preserved). Task 3's `FindOrCreateGuestCustomerService` calls all three.

Mirrors `Admin`'s shape (Phase 1's own precedent) exactly: private ctor, business-named factory +
`reconstitute`, `byte[]` fields cloned on every read.

- [ ] **Step 1: Create `CustomerId` and the two enums**

```java
package com.floristeriarosy.domain.model.customer.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Application-generated identifier of a {@code Customer} (never assigned by the database). */
public final class CustomerId {

  private final UUID value;

  private CustomerId(UUID value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  /** @return a new, random identifier */
  public static CustomerId newId() {
    return new CustomerId(UUID.randomUUID());
  }

  /**
   * @param value an existing identifier value
   * @return the wrapped identifier
   */
  public static CustomerId of(UUID value) {
    return new CustomerId(value);
  }

  /** @return the raw UUID value */
  public UUID value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CustomerId customerId && value.equals(customerId.value);
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

```java
package com.floristeriarosy.domain.model.customer;

/** {@code customer.md}, section 1: two rows with very different lifecycles in one table. */
public enum CustomerType {
  GUEST,
  REGISTERED
}
```

```java
package com.floristeriarosy.domain.model.customer;

/** {@code customer.md}, section 1 (ADR-007). */
public enum CustomerStatus {
  ACTIVE,
  ARCHIVED
}
```

- [ ] **Step 2: Write the failing tests**

```java
package com.floristeriarosy.domain.model.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Domain rules of {@link Customer}, minimal slice (customer.md, rule 3.2 only). */
class CustomerTest {

  private static final byte[] ENCRYPTED = "encrypted".getBytes(StandardCharsets.UTF_8);
  private static final byte[] HASH = "hash".getBytes(StandardCharsets.UTF_8);

  @Test
  void registerGuestIsBornActiveGuestWithNoPassword() {
    Customer customer = Customer.registerGuest(CustomerId.newId(), ENCRYPTED, HASH, ENCRYPTED, ENCRYPTED, ENCRYPTED, HASH);

    assertThat(customer.type()).isEqualTo(CustomerType.GUEST);
    assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
  }

  @Test
  void updateGuestContactReplacesNameAndPhone() {
    Customer customer = Customer.registerGuest(CustomerId.newId(), ENCRYPTED, HASH, ENCRYPTED, ENCRYPTED, ENCRYPTED, HASH);
    byte[] newName = "new-name".getBytes(StandardCharsets.UTF_8);
    byte[] newPhone = "new-phone".getBytes(StandardCharsets.UTF_8);
    byte[] newPhoneHash = "new-phone-hash".getBytes(StandardCharsets.UTF_8);

    customer.updateGuestContact(newName, newName, newPhone, newPhoneHash);

    assertThat(customer.firstNameEncrypted()).isEqualTo(newName);
    assertThat(customer.phoneEncrypted()).isEqualTo(newPhone);
  }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q test -Dtest=CustomerTest`

- [ ] **Step 4: Implement `Customer`**

```java
package com.floristeriarosy.domain.model.customer;

import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate root of the customer module's minimal slice (customer.md, section 1). Only what
 * {@code order.md}'s guest checkout needs (rule 3.2) — registration, addresses, password and admin
 * management belong to the full {@code customer.md} feature, not built here.
 */
public final class Customer {

  private static final Logger LOGGER = LoggerFactory.getLogger(Customer.class);

  private final CustomerId id;
  private final CustomerType type;
  private CustomerStatus status;
  private byte[] emailEncrypted;
  private byte[] emailHash;
  private byte[] firstNameEncrypted;
  private byte[] lastNameEncrypted;
  private byte[] phoneEncrypted;
  private byte[] phoneHash;
  private final long version;
  private final Instant createdAt;
  private Instant updatedAt;

  private Customer(
      CustomerId id,
      CustomerType type,
      CustomerStatus status,
      byte[] emailEncrypted,
      byte[] emailHash,
      byte[] firstNameEncrypted,
      byte[] lastNameEncrypted,
      byte[] phoneEncrypted,
      byte[] phoneHash,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.type = Objects.requireNonNull(type, "type");
    this.status = Objects.requireNonNull(status, "status");
    this.emailEncrypted = emailEncrypted;
    this.emailHash = emailHash;
    this.firstNameEncrypted = firstNameEncrypted;
    this.lastNameEncrypted = lastNameEncrypted;
    this.phoneEncrypted = phoneEncrypted;
    this.phoneHash = phoneHash;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * A new {@code GUEST}, born from a sessionless checkout's buyer details (customer.md, rule 3.2).
   *
   * @param id application-generated identifier
   * @param emailEncrypted the buyer's encrypted email
   * @param emailHash the buyer's email HMAC, for the {@code UNIQUE} lookup
   * @param firstNameEncrypted the buyer's encrypted first name
   * @param lastNameEncrypted the buyer's encrypted last name
   * @param phoneEncrypted the buyer's encrypted phone
   * @param phoneHash the buyer's phone HMAC
   * @return the new, {@code ACTIVE} {@code GUEST}
   */
  public static Customer registerGuest(
      CustomerId id,
      byte[] emailEncrypted,
      byte[] emailHash,
      byte[] firstNameEncrypted,
      byte[] lastNameEncrypted,
      byte[] phoneEncrypted,
      byte[] phoneHash) {
    LOGGER.debug("registerGuest id={}", id);
    Customer result =
        new Customer(
            id, CustomerType.GUEST, CustomerStatus.ACTIVE, emailEncrypted, emailHash, firstNameEncrypted,
            lastNameEncrypted, phoneEncrypted, phoneHash, 0L, null, null);
    LOGGER.debug("registerGuest id={} -> registered", id);
    return result;
  }

  /**
   * Rebuilds a customer from persisted state. Used only by the persistence mapper.
   *
   * @param id the persisted identifier
   * @param type {@code GUEST} or {@code REGISTERED}
   * @param status {@code ACTIVE} or {@code ARCHIVED}
   * @param emailEncrypted the persisted encrypted email, or {@code null} if purged
   * @param emailHash the persisted email HMAC, or {@code null} if purged
   * @param firstNameEncrypted the persisted encrypted first name, or {@code null} if purged
   * @param lastNameEncrypted the persisted encrypted last name, or {@code null} if purged
   * @param phoneEncrypted the persisted encrypted phone, or {@code null} if purged
   * @param phoneHash the persisted phone HMAC, or {@code null} if purged
   * @param version the optimistic-locking version (ADR-009)
   * @param createdAt when the row was created
   * @param updatedAt when the row was last updated
   * @return the rebuilt customer
   */
  public static Customer reconstitute(
      CustomerId id,
      CustomerType type,
      CustomerStatus status,
      byte[] emailEncrypted,
      byte[] emailHash,
      byte[] firstNameEncrypted,
      byte[] lastNameEncrypted,
      byte[] phoneEncrypted,
      byte[] phoneHash,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Customer(
        id, type, status, emailEncrypted, emailHash, firstNameEncrypted, lastNameEncrypted, phoneEncrypted,
        phoneHash, version, createdAt, updatedAt);
  }

  /**
   * A returning {@code GUEST}'s contact is refreshed with the latest checkout's details, not
   * preserved — a {@code GUEST} has no profile of its own to protect (customer.md, rule 3.2).
   *
   * @param firstNameEncrypted the latest encrypted first name
   * @param lastNameEncrypted the latest encrypted last name
   * @param phoneEncrypted the latest encrypted phone
   * @param phoneHash the latest phone HMAC
   */
  public void updateGuestContact(byte[] firstNameEncrypted, byte[] lastNameEncrypted, byte[] phoneEncrypted, byte[] phoneHash) {
    LOGGER.debug("updateGuestContact id={}", id);
    this.firstNameEncrypted = firstNameEncrypted;
    this.lastNameEncrypted = lastNameEncrypted;
    this.phoneEncrypted = phoneEncrypted;
    this.phoneHash = phoneHash;
    LOGGER.debug("updateGuestContact id={} -> updated", id);
  }

  /** @return the application-generated identifier */
  public CustomerId id() {
    return id;
  }

  /** @return {@code GUEST} or {@code REGISTERED} */
  public CustomerType type() {
    return type;
  }

  /** @return {@code ACTIVE} or {@code ARCHIVED} */
  public CustomerStatus status() {
    return status;
  }

  /** @return the encrypted email, or {@code null} if purged */
  public byte[] emailEncrypted() {
    return emailEncrypted == null ? null : emailEncrypted.clone();
  }

  /** @return the email HMAC, or {@code null} if purged */
  public byte[] emailHash() {
    return emailHash == null ? null : emailHash.clone();
  }

  /** @return the encrypted first name, or {@code null} if purged */
  public byte[] firstNameEncrypted() {
    return firstNameEncrypted == null ? null : firstNameEncrypted.clone();
  }

  /** @return the encrypted last name, or {@code null} if purged */
  public byte[] lastNameEncrypted() {
    return lastNameEncrypted == null ? null : lastNameEncrypted.clone();
  }

  /** @return the encrypted phone, or {@code null} if purged */
  public byte[] phoneEncrypted() {
    return phoneEncrypted == null ? null : phoneEncrypted.clone();
  }

  /** @return the phone HMAC, or {@code null} if purged */
  public byte[] phoneHash() {
    return phoneHash == null ? null : phoneHash.clone();
  }

  /** @return the optimistic-locking version (ADR-009) */
  public long version() {
    return version;
  }

  /** @return when the row was created, or {@code null} before the first save */
  public Instant createdAt() {
    return createdAt;
  }

  /** @return when the row was last updated, or {@code null} before the first save */
  public Instant updatedAt() {
    return updatedAt;
  }
}
```

- [ ] **Step 5: Run tests to verify they pass, then commit**

Run: `mvn -q test -Dtest=CustomerTest`

```bash
git add src/main/java/com/floristeriarosy/domain/model/customer \
        src/test/java/com/floristeriarosy/domain/model/customer
git commit -m "feat(customer): minimal Customer domain slice (customer.md §1, §3.2)"
```

---

## Task 3: `CustomerReadPort`/`CustomerWritePort` and `FindOrCreateGuestCustomerService`

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/customer/port/out/CustomerReadPort.java`
- Create: `src/main/java/com/floristeriarosy/application/customer/port/out/CustomerWritePort.java`
- Create: `src/main/java/com/floristeriarosy/application/customer/port/in/FindOrCreateGuestCustomerUseCase.java`
- Create: `src/main/java/com/floristeriarosy/application/customer/command/FindOrCreateGuestCustomerCommand.java`
- Create: `src/main/java/com/floristeriarosy/application/customer/service/FindOrCreateGuestCustomerService.java`
- Test: `src/test/java/com/floristeriarosy/application/customer/service/FindOrCreateGuestCustomerServiceTest.java`

**Interfaces:**
- Consumes: `Customer`/`CustomerId` (Task 2), `PiiCryptoPort` (existing, ADR-005).
- Produces: `FindOrCreateGuestCustomerUseCase.execute(FindOrCreateGuestCustomerCommand)` →
  `CustomerId` (or `null` — see the three branches below). Task 5's `ConfirmCheckoutService` calls
  this directly, no controller (customer.md, §8: "no hay un puerto de infraestructura para ello, es
  una capacidad de aplicación entre módulos" — same pattern as `RegisterStockMovementUseCase`).

Implements exactly customer.md's rule 3.2, three branches:

- [ ] **Step 1: Create the ports and command**

```java
package com.floristeriarosy.application.customer.port.out;

import com.floristeriarosy.domain.model.customer.Customer;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import java.util.Optional;

/** Read access to {@code customers}, minimal slice (customer.md, section 8). */
public interface CustomerReadPort {

  /**
   * @param id the customer to load
   * @return the customer, if it exists
   */
  Optional<Customer> findById(CustomerId id);

  /**
   * @param emailHash the HMAC of a normalized email
   * @return the customer using that email, if any (any type, any status)
   */
  Optional<Customer> findByEmailHash(byte[] emailHash);
}
```

```java
package com.floristeriarosy.application.customer.port.out;

import com.floristeriarosy.domain.model.customer.Customer;

/** Write access to {@code customers}, minimal slice (customer.md, section 8). */
public interface CustomerWritePort {

  /**
   * @param customer the customer to insert or update
   * @return the saved customer, with timestamps populated by the database
   */
  Customer save(Customer customer);
}
```

```java
package com.floristeriarosy.application.customer.command;

/**
 * @param email the buyer's email from the checkout (customer.md, rule 3.2)
 * @param firstName the buyer's first name
 * @param lastName the buyer's last name
 * @param phone the buyer's phone
 */
public record FindOrCreateGuestCustomerCommand(String email, String firstName, String lastName, String phone) {}
```

- [ ] **Step 2: Write the failing tests**

```java
package com.floristeriarosy.application.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.customer.command.FindOrCreateGuestCustomerCommand;
import com.floristeriarosy.application.customer.port.out.CustomerReadPort;
import com.floristeriarosy.application.customer.port.out.CustomerWritePort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.customer.Customer;
import com.floristeriarosy.domain.model.customer.CustomerType;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@link FindOrCreateGuestCustomerService} — the three branches of customer.md, rule 3.2. */
class FindOrCreateGuestCustomerServiceTest {

  private final CustomerReadPort readPort = mock(CustomerReadPort.class);
  private final CustomerWritePort writePort = mock(CustomerWritePort.class);
  private final PiiCryptoPort piiCryptoPort = mock(PiiCryptoPort.class);
  private final FindOrCreateGuestCustomerService service = new FindOrCreateGuestCustomerService(readPort, writePort, piiCryptoPort);

  private FindOrCreateGuestCustomerCommand command() {
    return new FindOrCreateGuestCustomerCommand("ana@example.com", "Ana", "García", "600111222");
  }

  @Test
  void createsANewGuestWhenNoRowExistsForTheEmail() {
    when(piiCryptoPort.hmac(any())).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(piiCryptoPort.encrypt(any())).thenReturn("enc".getBytes(StandardCharsets.UTF_8));
    when(readPort.findByEmailHash(any())).thenReturn(Optional.empty());
    when(writePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CustomerId result = service.execute(command());

    assertThat(result).isNotNull();
    verify(writePort).save(any());
  }

  @Test
  void reusesAnExistingGuestAndRefreshesContact() {
    when(piiCryptoPort.hmac(any())).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(piiCryptoPort.encrypt(any())).thenReturn("enc".getBytes(StandardCharsets.UTF_8));
    Customer existingGuest =
        Customer.registerGuest(
            CustomerId.newId(), "old".getBytes(StandardCharsets.UTF_8), "hash".getBytes(StandardCharsets.UTF_8),
            "old".getBytes(StandardCharsets.UTF_8), "old".getBytes(StandardCharsets.UTF_8),
            "old".getBytes(StandardCharsets.UTF_8), "hash".getBytes(StandardCharsets.UTF_8));
    when(readPort.findByEmailHash(any())).thenReturn(Optional.of(existingGuest));
    when(writePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CustomerId result = service.execute(command());

    assertThat(result).isEqualTo(existingGuest.id());
  }

  @Test
  void doesNotLinkToAnExistingRegisteredCustomer() {
    when(piiCryptoPort.hmac(any())).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    Customer registered =
        Customer.reconstitute(
            CustomerId.newId(), CustomerType.REGISTERED, com.floristeriarosy.domain.model.customer.CustomerStatus.ACTIVE,
            "e".getBytes(StandardCharsets.UTF_8), "h".getBytes(StandardCharsets.UTF_8), "f".getBytes(StandardCharsets.UTF_8),
            "l".getBytes(StandardCharsets.UTF_8), "p".getBytes(StandardCharsets.UTF_8), "ph".getBytes(StandardCharsets.UTF_8),
            0L, null, null);
    when(readPort.findByEmailHash(any())).thenReturn(Optional.of(registered));

    CustomerId result = service.execute(command());

    assertThat(result).isNull();
    verify(writePort, never()).save(any());
  }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q test -Dtest=FindOrCreateGuestCustomerServiceTest`

- [ ] **Step 4: Create `FindOrCreateGuestCustomerUseCase` and its service**

```java
package com.floristeriarosy.application.customer.port.in;

import com.floristeriarosy.application.customer.command.FindOrCreateGuestCustomerCommand;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;

/**
 * Consumed directly by {@code order.md}'s {@code ConfirmCheckoutService} (customer.md, section 8)
 * — no controller of its own.
 */
public interface FindOrCreateGuestCustomerUseCase {

  /**
   * @param command the checkout's buyer details
   * @return the linked customer's id, or {@code null} if the email belongs to a {@code
   *     REGISTERED} account (customer.md, rule 3.2 — the order stays unlinked in that case)
   */
  CustomerId execute(FindOrCreateGuestCustomerCommand command);
}
```

```java
package com.floristeriarosy.application.customer.service;

import com.floristeriarosy.application.customer.command.FindOrCreateGuestCustomerCommand;
import com.floristeriarosy.application.customer.port.in.FindOrCreateGuestCustomerUseCase;
import com.floristeriarosy.application.customer.port.out.CustomerReadPort;
import com.floristeriarosy.application.customer.port.out.CustomerWritePort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.customer.Customer;
import com.floristeriarosy.domain.model.customer.CustomerType;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link FindOrCreateGuestCustomerUseCase} (customer.md, rule 3.2): a {@code WEB}
 * guest checkout always anchors by email — create if new, refresh if a {@code GUEST} already
 * exists, leave unlinked if the email belongs to a {@code REGISTERED} account.
 */
@Service
@Transactional
public class FindOrCreateGuestCustomerService implements FindOrCreateGuestCustomerUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(FindOrCreateGuestCustomerService.class);

  private final CustomerReadPort readPort;
  private final CustomerWritePort writePort;
  private final PiiCryptoPort piiCryptoPort;

  /**
   * @param readPort locates an existing customer by email
   * @param writePort persists a new or refreshed guest
   * @param piiCryptoPort encrypts and hashes the buyer's details (ADR-005)
   */
  public FindOrCreateGuestCustomerService(CustomerReadPort readPort, CustomerWritePort writePort, PiiCryptoPort piiCryptoPort) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.piiCryptoPort = piiCryptoPort;
  }

  /**
   * @param command the checkout's buyer details
   * @return the linked customer's id, or {@code null} for a {@code REGISTERED} email match
   */
  @Override
  public CustomerId execute(FindOrCreateGuestCustomerCommand command) {
    LOGGER.debug("findOrCreateGuestCustomer");
    String normalizedEmail = command.email().trim().toLowerCase(Locale.ROOT);
    byte[] emailHash = piiCryptoPort.hmac(normalizedEmail);
    var existing = readPort.findByEmailHash(emailHash);
    if (existing.isPresent() && existing.get().type() == CustomerType.REGISTERED) {
      LOGGER.debug("findOrCreateGuestCustomer -> email belongs to a REGISTERED account, leaving unlinked");
      return null;
    }
    byte[] firstNameEncrypted = piiCryptoPort.encrypt(command.firstName());
    byte[] lastNameEncrypted = piiCryptoPort.encrypt(command.lastName());
    byte[] phoneEncrypted = piiCryptoPort.encrypt(command.phone());
    byte[] phoneHash = piiCryptoPort.hmac(command.phone());
    Customer customer;
    if (existing.isPresent()) {
      customer = existing.get();
      customer.updateGuestContact(firstNameEncrypted, lastNameEncrypted, phoneEncrypted, phoneHash);
    } else {
      byte[] emailEncrypted = piiCryptoPort.encrypt(normalizedEmail);
      customer =
          Customer.registerGuest(
              CustomerId.newId(), emailEncrypted, emailHash, firstNameEncrypted, lastNameEncrypted, phoneEncrypted, phoneHash);
    }
    CustomerId result = writePort.save(customer).id();
    LOGGER.debug("findOrCreateGuestCustomer -> id={}", result);
    return result;
  }
}
```

- [ ] **Step 5: Run tests to verify they pass, then commit**

Run: `mvn -q test -Dtest=FindOrCreateGuestCustomerServiceTest`

```bash
git add src/main/java/com/floristeriarosy/application/customer/port \
        src/main/java/com/floristeriarosy/application/customer/command \
        src/main/java/com/floristeriarosy/application/customer/service \
        src/test/java/com/floristeriarosy/application/customer/service
git commit -m "feat(customer): FindOrCreateGuestCustomerService (customer.md §3.2, §7-8)"
```

---

## Task 4: Customer persistence

**Files:**
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/entity/customer/CustomerEntity.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/mapper/customer/CustomerPersistenceMapper.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/jpa/customer/repository/CustomerJpaRepository.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/customer/CustomerPersistenceAdapter.java`
- Test: `src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/customer/CustomerPersistenceAdapterTest.java`

**Interfaces:**
- Consumes: `Customer` (Task 2), `CustomerReadPort`/`CustomerWritePort` (Task 3).
- Produces: `CustomerPersistenceAdapter implements CustomerReadPort, CustomerWritePort` — Task 3's
  `FindOrCreateGuestCustomerService` gets injected with this.

Mirrors `AdminUserEntity`/`AdminPersistenceMapper`/`AdminPersistenceAdapter` exactly (Phase 1's own
precedent, including the `@Version`-safe `save()` pattern: reuse the managed entity, `saveAndFlush`,
catch `ObjectOptimisticLockingFailureException`) — `customers` has a real `version` column
(`V3__optimistic_locking.sql`), unlike `payments`.

- [ ] **Step 1: Create `CustomerEntity`**

Map exactly the columns from `V1__initial_schema.sql`'s `customers` table (id, type, status,
email_encrypted, email_hash, phone_encrypted, phone_hash, first_name_encrypted,
last_name_encrypted, password_hash, email_verified_at, anonymized_at, version, created_at,
updated_at) — this minimal slice only ever writes `type`, `status`, the four encrypted/hash pairs,
`version`, `created_at`, `updated_at`; leave `password_hash`/`email_verified_at`/`anonymized_at`
mapped (JPA needs every column) but always `null` from this slice, matching
`chk_customers_guest_no_password`. Follow `AdminUserEntity`'s exact structure: `@Version` field,
`@PrePersist`/`@PreUpdate` for timestamps, full constructor + getters, each with one-line Javadoc.

- [ ] **Step 2: Create `CustomerPersistenceMapper`**

Same shape as `AdminPersistenceMapper.toEntity`/`toDomain`, mapping `Customer.type()`/`.status()`
to/from the entity's `type`/`status` enum columns and passing `null` through for
`password_hash`/`email_verified_at`/`anonymized_at`.

- [ ] **Step 3: Write the failing adapter test**

```java
package com.floristeriarosy.infrastructure.persistence.adapter.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.customer.Customer;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class CustomerPersistenceAdapterTest {

  @Autowired private CustomerPersistenceAdapter adapter;

  @Test
  void savingThenFindingByEmailHashRoundTrips() {
    byte[] emailHash = "unique-hash-1".getBytes(StandardCharsets.UTF_8);
    Customer customer =
        Customer.registerGuest(
            CustomerId.newId(), "enc".getBytes(StandardCharsets.UTF_8), emailHash, "enc".getBytes(StandardCharsets.UTF_8),
            "enc".getBytes(StandardCharsets.UTF_8), "enc".getBytes(StandardCharsets.UTF_8),
            "phone-hash".getBytes(StandardCharsets.UTF_8));

    adapter.save(customer);

    assertThat(adapter.findByEmailHash(emailHash)).isPresent();
  }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -q test -Dtest=CustomerPersistenceAdapterTest`

- [ ] **Step 5: Create `CustomerJpaRepository` and `CustomerPersistenceAdapter`**

`CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID>` with
`Optional<CustomerEntity> findByEmailHash(byte[] emailHash)`. The adapter follows
`AdminPersistenceAdapter.save()` verbatim (reuse managed entity, `saveAndFlush`, catch
`ObjectOptimisticLockingFailureException` → `ResourceModifiedException`).

- [ ] **Step 6: Run test to verify it passes, then commit**

Run: `mvn -q test -Dtest=CustomerPersistenceAdapterTest`

```bash
git add src/main/java/com/floristeriarosy/infrastructure/persistence/entity/customer \
        src/main/java/com/floristeriarosy/infrastructure/persistence/mapper/customer \
        src/main/java/com/floristeriarosy/infrastructure/persistence/jpa/customer \
        src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/customer \
        src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/customer
git commit -m "feat(customer): Customer JPA persistence (customer.md §2, §8)"
```

---

## Task 5: `notification`'s minimal write-only slice

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/notification/command/RegisterNotificationCommand.java`
- Create: `src/main/java/com/floristeriarosy/application/notification/port/in/RegisterNotificationUseCase.java`
- Create: `src/main/java/com/floristeriarosy/application/notification/service/RegisterNotificationService.java`
- Create: `src/main/java/com/floristeriarosy/application/notification/port/out/NotificationWritePort.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/entity/notification/NotificationEntity.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/mapper/notification/NotificationPersistenceMapper.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/jpa/notification/repository/NotificationJpaRepository.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/notification/NotificationPersistenceAdapter.java`
- Test: `src/test/java/com/floristeriarosy/application/notification/service/RegisterNotificationServiceTest.java`

**Interfaces:**
- Consumes: `OrderId` (Phase 1), `CustomerId` (Task 2).
- Produces: `RegisterNotificationUseCase.execute(RegisterNotificationCommand)` — Task 6's
  `ConfirmCheckoutService` calls this twice on success (`ORDER_CONFIRMED`, `STAFF_NEW_ORDER`,
  notification.md rule 3.1), never on decline (notification.md: a checkout that never confirms
  generates no notification, symmetric with `orders`).

No domain aggregate: `notifications` is insert-only from this slice (notification.md, rule 3.4 —
"la fila guarda una referencia, nunca el contenido"), so there is no lifecycle to protect with a
rich domain type; a plain entity is the right amount of structure (YAGNI against an aggregate with
no business method).

- [ ] **Step 1: Create the command and ports**

```java
package com.floristeriarosy.application.notification.command;

import java.util.UUID;

/**
 * @param type one of the nine catalog types (notification.md, rule 3.1) — this slice only ever
 *     sends {@code ORDER_CONFIRMED}/{@code STAFF_NEW_ORDER}, but the column accepts any of them
 * @param orderId the referenced order, or {@code null} if this notification is not order-related
 * @param customerId the referenced customer, or {@code null}
 */
public record RegisterNotificationCommand(String type, UUID orderId, UUID customerId) {}
```

```java
package com.floristeriarosy.application.notification.port.in;

import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;

/**
 * Consumed directly by other modules (notification.md, section 1: "ningún otro módulo habla con
 * un proveedor de correo; registran una notificación y siguen con lo suyo") — no controller.
 */
public interface RegisterNotificationUseCase {

  /**
   * @param command what to notify about
   */
  void execute(RegisterNotificationCommand command);
}
```

```java
package com.floristeriarosy.application.notification.port.out;

import java.util.UUID;

/** Write access to {@code notifications} (notification.md, section 2/3.4: transactional outbox). */
public interface NotificationWritePort {

  /**
   * @param type the notification type
   * @param orderId the referenced order, or {@code null}
   * @param customerId the referenced customer, or {@code null}
   */
  void insert(String type, UUID orderId, UUID customerId);
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.floristeriarosy.application.notification.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;
import com.floristeriarosy.application.notification.port.out.NotificationWritePort;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegisterNotificationServiceTest {

  private final NotificationWritePort writePort = mock(NotificationWritePort.class);
  private final RegisterNotificationService service = new RegisterNotificationService(writePort);

  @Test
  void registersTheNotificationAsGiven() {
    UUID orderId = UUID.randomUUID();

    service.execute(new RegisterNotificationCommand("ORDER_CONFIRMED", orderId, null));

    verify(writePort).insert("ORDER_CONFIRMED", orderId, null);
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=RegisterNotificationServiceTest`

- [ ] **Step 4: Implement `RegisterNotificationService`**

```java
package com.floristeriarosy.application.notification.service;

import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;
import com.floristeriarosy.application.notification.port.in.RegisterNotificationUseCase;
import com.floristeriarosy.application.notification.port.out.NotificationWritePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Implements {@link RegisterNotificationUseCase} — the transactional-outbox insert, nothing else
 * (notification.md, section 1: sending is a separate, future scheduled task). */
@Service
public class RegisterNotificationService implements RegisterNotificationUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(RegisterNotificationService.class);

  private final NotificationWritePort writePort;

  /**
   * @param writePort inserts the {@code notifications} row
   */
  public RegisterNotificationService(NotificationWritePort writePort) {
    this.writePort = writePort;
  }

  /**
   * @param command what to notify about
   */
  @Override
  public void execute(RegisterNotificationCommand command) {
    LOGGER.debug("registerNotification type={} orderId={}", command.type(), command.orderId());
    writePort.insert(command.type(), command.orderId(), command.customerId());
    LOGGER.debug("registerNotification type={} -> registered", command.type());
  }
}
```

- [ ] **Step 5: Create the entity, mapper, JPA repository, and adapter**

`NotificationEntity` maps `notifications` (`V15`): `id`, `type`, `order_id`, `customer_id`,
`status` (always `"PENDING"` from this slice), `attempts` (`0`), `next_attempt_at` (`now()`),
`last_error` (`null`), `sent_at` (`null`), `created_at`. Read `V15__notifications_outbox.sql`
first for the exact column list and any `NOT NULL`/`CHECK` constraints before writing the entity —
do not guess columns this plan did not enumerate. `NotificationPersistenceAdapter implements
NotificationWritePort`, one `insert` method, no reads needed by this slice.

- [ ] **Step 6: Run the full build, then commit**

Run: `mvn -q clean verify`

```bash
git add src/main/java/com/floristeriarosy/application/notification \
        src/main/java/com/floristeriarosy/infrastructure/persistence/entity/notification \
        src/main/java/com/floristeriarosy/infrastructure/persistence/mapper/notification \
        src/main/java/com/floristeriarosy/infrastructure/persistence/jpa/notification \
        src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/notification \
        src/test/java/com/floristeriarosy/application/notification
git commit -m "feat(notification): minimal RegisterNotificationUseCase slice (notification.md §1, §3.1)"
```

---

## Task 6: Order persistence — `OrderReadPort`/`OrderWritePort`

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/OrderReadPort.java`
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/OrderWritePort.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/entity/order/OrderEntity.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/entity/order/OrderItemEntity.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/entity/order/OrderDeliveryEntity.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/mapper/order/OrderPersistenceMapper.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/jpa/order/repository/OrderJpaRepository.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderPersistenceAdapter.java`
- Test: `src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderPersistenceAdapterTest.java`

**Interfaces:**
- Consumes: `Order`/`OrderItem`/`OrderDelivery` (Phase 1).
- Produces: `OrderReadPort.findById(OrderId)`/`.findByOrderNumber(OrderNumber)` →
  `Optional<Order>`, `OrderWritePort.save(Order)` → `Order`. Task 7's `ConfirmCheckoutService` and
  Task 8's `GetOrderService`/`GetOrdersService` depend on these.

`order.md` §8 also lists `findByCustomer`/`findAllForAdmin` (JDBC, paginated) — **not built in this
task**, they belong to `GET /orders` (Task 8, plain JPA suffices for "my own orders, no filters
yet") and a future admin-listing phase respectively; do not add JDBC infrastructure with no caller
yet.

- [ ] **Step 1: Create `OrderReadPort` and `OrderWritePort`**

```java
package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.util.Optional;

/** Read access to {@code orders} (order.md, section 8). */
public interface OrderReadPort {

  /**
   * @param id the order to load
   * @return the order, if it exists
   */
  Optional<Order> findById(OrderId id);

  /**
   * @param orderNumber the business identifier to look up
   * @return the order, if it exists
   */
  Optional<Order> findByOrderNumber(OrderNumber orderNumber);
}
```

```java
package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.order.Order;

/** Write access to {@code orders} (order.md, section 8): a single {@code save}, no {@code update}
 * — every transition still goes through the same aggregate root, matching {@code ProductWritePort}. */
public interface OrderWritePort {

  /**
   * @param order the order to insert (Fase 3a materializes it fully formed — order.md §1, an
   *     order is never partially created)
   * @return the saved order, with timestamps populated by the database
   */
  Order save(Order order);
}
```

- [ ] **Step 2: Write the failing adapter test**

```java
package com.floristeriarosy.infrastructure.persistence.adapter.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.OrderItem;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderItemId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OrderPersistenceAdapterTest {

  @Autowired private OrderPersistenceAdapter adapter;

  private static final byte[] ENCRYPTED = "encrypted".getBytes(StandardCharsets.UTF_8);

  @Test
  void savingThenFindingByOrderNumberRoundTripsItemsAndTotal() {
    OrderItem item =
        OrderItem.snapshot(OrderItemId.newId(), ProductId.newId(), "Rosa", Map.of(), new BigDecimal("10.00"), null, null, 2);
    OrderNumber orderNumber = OrderNumber.of("WEB-" + UUID.randomUUID());
    Order order =
        Order.place(
            OrderId.newId(), orderNumber, OrderChannel.WEB, OrderFulfillment.NONE, null, ENCRYPTED, ENCRYPTED,
            ENCRYPTED, ENCRYPTED, ENCRYPTED, ENCRYPTED, List.of(item), null, new BigDecimal("20.00"), BigDecimal.ZERO);

    adapter.save(order);
    Order found = adapter.findByOrderNumber(orderNumber).orElseThrow();

    assertThat(found.items()).hasSize(1);
    assertThat(found.total()).isEqualByComparingTo("20.00");
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=OrderPersistenceAdapterTest`

- [ ] **Step 4: Create `OrderItemEntity` and `OrderDeliveryEntity`**

`OrderItemEntity` maps `order_items` (V1 + `discount_id` from `V6`): `id`, `order_id` (FK, not a
Java field on this side — set via the owning `OrderEntity`'s relationship), `product_id`,
`product_name`, `product_attributes` (JSONB — check how `ProductEntity` maps its own JSONB
`attributes` column, e.g. `@JdbcTypeCode(SqlTypes.JSON)` or a converter, and reuse the exact same
mechanism, do not invent a second one), `unit_price`, `discount_price`, `discount_id`, `quantity`,
`created_at`. No `@Version` (child of `Order`'s own optimistic lock).

`OrderDeliveryEntity` maps `order_deliveries` (1:1, `order_id` is the primary key, no separate
`id`): every encrypted/plain column listed in order.md §2 (`recipient_name_encrypted` through
`card_message_encrypted`), `created_at`, `updated_at`.

- [ ] **Step 5: Create `OrderEntity`**

Maps `orders` (V1 + `V3` version + `V6`/`V9`/`V14` — no `ACCEPTED` per `V18`): `id`, `order_number`,
`channel`, `fulfillment`, `status`, `customer_id`, six buyer encrypted/hash columns, `subtotal`,
`shipping_cost` (never write `total`, it is `GENERATED ALWAYS`), `placed_at`, `retention_until`,
`personal_data_purged_at`, `@Version version`, `created_at`, `updated_at`, plus
`@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) List<OrderItemEntity> items`
and `@OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) OrderDeliveryEntity delivery`
(nullable). Follow `AdminUserEntity`'s `@PrePersist`/`@PreUpdate` timestamp pattern. Every
constructor parameter and getter gets one-line Javadoc, same as every other entity in this plan.

- [ ] **Step 6: Create `OrderPersistenceMapper`**

`toEntity(Order)` builds the full object graph (`OrderEntity` + its `OrderItemEntity` list + its
optional `OrderDeliveryEntity`), setting each child's back-reference to the parent so JPA's
cascade persists all three tables from one `save()`. `toDomain(OrderEntity)` calls
`Order.reconstitute(...)` with `entity.getItems().stream().map(this::itemToDomain).toList()` and
`entity.getDelivery() != null ? deliveryToDomain(...) : null`.

- [ ] **Step 7: Create `OrderJpaRepository` and `OrderPersistenceAdapter`**

`OrderJpaRepository extends JpaRepository<OrderEntity, UUID>` with
`Optional<OrderEntity> findByOrderNumber(String orderNumber)`. The adapter's `save()` follows
`ProductPersistenceAdapter`'s pattern (Phase 1's own research already has this verbatim): reuse the
managed entity via `findById` before mutating, `saveAndFlush`, catch
`ObjectOptimisticLockingFailureException` → `ResourceModifiedException`.

- [ ] **Step 8: Run test to verify it passes, then commit**

Run: `mvn -q test -Dtest=OrderPersistenceAdapterTest`

```bash
git add src/main/java/com/floristeriarosy/application/order/port/out/OrderReadPort.java \
        src/main/java/com/floristeriarosy/application/order/port/out/OrderWritePort.java \
        src/main/java/com/floristeriarosy/infrastructure/persistence/entity/order \
        src/main/java/com/floristeriarosy/infrastructure/persistence/mapper/order \
        src/main/java/com/floristeriarosy/infrastructure/persistence/jpa/order \
        src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderPersistenceAdapter.java \
        src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderPersistenceAdapterTest.java
git commit -m "feat(order): Order JPA persistence, cascades items and delivery (order.md §2, §8)"
```

---

## Task 7: `ConfirmCheckoutService`

**Files:**
- Modify: `src/main/java/com/floristeriarosy/application/order/dto/PendingCheckoutPayload.java`
  (add `sessionToken` — Phase 3a's payload has everything for the order except what `ClearCartUseCase`
  needs to identify *which* cart to clear; a guest's cart is keyed by session token, not customer id)
- Create: `src/main/java/com/floristeriarosy/application/order/command/ConfirmCheckoutCommand.java`
- Create: `src/main/java/com/floristeriarosy/application/order/port/in/ConfirmCheckoutUseCase.java`
- Create: `src/main/java/com/floristeriarosy/application/order/service/ConfirmCheckoutService.java`
- Test: `src/test/java/com/floristeriarosy/application/order/service/ConfirmCheckoutServiceTest.java`

**Interfaces:**
- Consumes: everything from Phase 3a (`CheckoutIdempotencyPort`, `PiiCryptoPort`, `ObjectMapper`),
  `ClearCartUseCase` (`cart`, existing), `RegisterStockMovementUseCase`/`DiscountReservationPort`
  (existing, same collaborators `CheckoutService` already uses, now called in reverse for a
  decline), `OrderPaymentPort.confirmCharge`/`.refund`... no, `refund` is not used here — a decline
  never captured anything to refund (Task 1 note: `confirmCharge` is the only new method this task
  needs), `OrderWritePort` (Task 6), `FindOrCreateGuestCustomerUseCase` (Task 3),
  `RegisterNotificationUseCase` (Task 5).
- Produces: `ConfirmCheckoutService implements ConfirmCheckoutUseCase` — Task 8's notification
  controller calls this; it is the single entry point Redsys's approval or decline both go through.

- [ ] **Step 1: Add `sessionToken` to `PendingCheckoutPayload`**

Read the current file (already fetched verbatim in this session), add `String sessionToken` as a
new component (after `customerId`, before the buyer fields), update its Javadoc `@param` list.
`CheckoutService` (Phase 3a) must also be updated to pass `command.sessionToken()` when building
the payload — a one-line addition there, not a redesign.

- [ ] **Step 2: Create `ConfirmCheckoutCommand` and `ConfirmCheckoutUseCase`**

```java
package com.floristeriarosy.application.order.command;

import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;

/**
 * @param idempotencyKey Redsys's echoed {@code Ds_Merchant_MerchantData} (ADR-017) — finds the
 *     Fase-1 lock
 * @param orderNumber Redsys's echoed {@code Ds_Merchant_Order}
 * @param approved whether Redsys approved the charge
 * @param providerPaymentIntentId Redsys's authorization reference, present only when {@code
 *     approved}
 * @param failureReason why Redsys declined, present only when not {@code approved}
 */
public record ConfirmCheckoutCommand(String idempotencyKey, OrderNumber orderNumber, boolean approved, String providerPaymentIntentId, String failureReason) {}
```

```java
package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.command.ConfirmCheckoutCommand;

/** Resolves a Redsys notification, approved or declined (order.md §3.2, ADR-017). */
public interface ConfirmCheckoutUseCase {

  /**
   * @param command the decoded, signature-verified notification
   */
  void execute(ConfirmCheckoutCommand command);
}
```

- [ ] **Step 3: Write the failing tests**

```java
package com.floristeriarosy.application.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.command.ConfirmCheckoutCommand;
import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.application.order.port.out.OrderWritePort;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.application.customer.port.in.FindOrCreateGuestCustomerUseCase;
import com.floristeriarosy.application.notification.port.in.RegisterNotificationUseCase;
import com.floristeriarosy.application.cart.port.in.ClearCartUseCase;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link ConfirmCheckoutService} — Fase 3a (approve) and Fase 3b (decline), order.md §3.2. */
class ConfirmCheckoutServiceTest {

  private final CheckoutIdempotencyPort idempotencyPort = mock(CheckoutIdempotencyPort.class);
  private final OrderPaymentPort orderPaymentPort = mock(OrderPaymentPort.class);
  private final OrderWritePort orderWritePort = mock(OrderWritePort.class);
  private final FindOrCreateGuestCustomerUseCase findOrCreateGuestCustomerUseCase = mock(FindOrCreateGuestCustomerUseCase.class);
  private final RegisterNotificationUseCase registerNotificationUseCase = mock(RegisterNotificationUseCase.class);
  private final ClearCartUseCase clearCartUseCase = mock(ClearCartUseCase.class);
  private final PiiCryptoPort piiCryptoPort = mock(PiiCryptoPort.class);
  private ConfirmCheckoutService service;
  // Add mocks for RegisterStockMovementUseCase/DiscountReservationPort, needed only by the
  // decline branch — read their real signatures from CheckoutService before wiring (same note as
  // Phase 3a's own Task 8).

  @BeforeEach
  void setUp() {
    when(piiCryptoPort.encrypt(org.mockito.ArgumentMatchers.anyString())).thenReturn("enc".getBytes(StandardCharsets.UTF_8));
    when(piiCryptoPort.hmac(org.mockito.ArgumentMatchers.anyString())).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    service =
        new ConfirmCheckoutService(
            idempotencyPort, orderPaymentPort, orderWritePort, findOrCreateGuestCustomerUseCase,
            registerNotificationUseCase, clearCartUseCase, piiCryptoPort, new ObjectMapper()
            /* , registerStockMovementUseCase, discountReservationPort */);
  }

  private PendingCheckoutPayload payload() {
    return new PendingCheckoutPayload(
        "WEB", "NONE", null, "session-1", "Ana", "García", "ana@example.com", "600111222",
        List.of(new PendingCheckoutPayload.Item(UUID.randomUUID(), "Rosa", Map.of(), new BigDecimal("10.00"), null, null, 2)),
        null, null, new BigDecimal("20.00"), BigDecimal.ZERO);
  }

  @Test
  void approvedMaterializesTheOrderAndClearsTheCart() throws Exception {
    var lock = new com.floristeriarosy.application.order.dto.IdempotencyLock("PENDING", new byte[0], UUID.randomUUID());
    when(idempotencyPort.find("idem-1")).thenReturn(java.util.Optional.of(lock));
    // The adapter's real find() would need to also expose the encrypted payload — see the note
    // below about extending IdempotencyLock/CheckoutIdempotencyPort in Step 4 if it does not
    // already carry it; this test assumes it does once that gap is closed.

    service.execute(new ConfirmCheckoutCommand("idem-1", OrderNumber.of("WEB-20260914-000001"), true, "redsys-auth-1", null));

    verify(orderWritePort).save(any());
    verify(clearCartUseCase).execute(any());
    verify(registerNotificationUseCase, times(2)).execute(any());
    verify(idempotencyPort).markCompleted("idem-1", 202);
  }

  @Test
  void alreadyCompletedIsIgnoredNotReprocessed() {
    when(idempotencyPort.find("idem-2"))
        .thenReturn(java.util.Optional.of(new com.floristeriarosy.application.order.dto.IdempotencyLock("COMPLETED", new byte[0], UUID.randomUUID())));

    service.execute(new ConfirmCheckoutCommand("idem-2", OrderNumber.of("WEB-20260914-000002"), true, "redsys-auth-2", null));

    verify(orderWritePort, never()).save(any());
  }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn -q test -Dtest=ConfirmCheckoutServiceTest`
Expected: compilation failure. **Before implementing, check whether `CheckoutIdempotencyPort.find`
and `IdempotencyLock` already expose `checkoutPayloadEncrypted`** — Phase 3a's own version (read
verbatim above) does not: `IdempotencyLock` only carries `status`/`requestFingerprint`/
`resourceId`. This task must extend both — add `byte[] checkoutPayloadEncrypted` to
`IdempotencyLock` and populate it in `CheckoutIdempotencyPersistenceAdapter.find(...)` — before
`ConfirmCheckoutService` can decrypt anything. Do this as part of Step 5, not silently skip it.

- [ ] **Step 5: Extend `IdempotencyLock` and `CheckoutIdempotencyPersistenceAdapter.find`**

Add the field, defensively cloned (SpotBugs), and populate it from
`entity.getCheckoutPayloadEncrypted()` in `find(...)`.

- [ ] **Step 6: Implement `ConfirmCheckoutService`**

```java
package com.floristeriarosy.application.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floristeriarosy.application.cart.command.ClearCartCommand;
import com.floristeriarosy.application.cart.port.in.ClearCartUseCase;
import com.floristeriarosy.application.customer.command.FindOrCreateGuestCustomerCommand;
import com.floristeriarosy.application.customer.port.in.FindOrCreateGuestCustomerUseCase;
import com.floristeriarosy.application.notification.command.RegisterNotificationCommand;
import com.floristeriarosy.application.notification.port.in.RegisterNotificationUseCase;
import com.floristeriarosy.application.order.command.ConfirmCheckoutCommand;
import com.floristeriarosy.application.order.dto.IdempotencyLock;
import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.application.order.port.in.ConfirmCheckoutUseCase;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.application.order.port.out.OrderWritePort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.order.Order;
import com.floristeriarosy.domain.model.order.OrderChannel;
import com.floristeriarosy.domain.model.order.OrderDelivery;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.order.OrderItem;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderItemId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link ConfirmCheckoutUseCase} — Fase 3a (approved) or Fase 3b (declined) of
 * order.md §3.2, driven by Redsys's notification, never by the browser's return. Idempotent by
 * construction: a lock not {@code PENDING} means this was already handled, and Redsys's own retry
 * behavior (resend until {@code 200 OK}) is exactly why that matters (ADR-017).
 */
@Service
@Transactional
public class ConfirmCheckoutService implements ConfirmCheckoutUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmCheckoutService.class);

  private final CheckoutIdempotencyPort idempotencyPort;
  private final OrderPaymentPort orderPaymentPort;
  private final OrderWritePort orderWritePort;
  private final FindOrCreateGuestCustomerUseCase findOrCreateGuestCustomerUseCase;
  private final RegisterNotificationUseCase registerNotificationUseCase;
  private final ClearCartUseCase clearCartUseCase;
  private final PiiCryptoPort piiCryptoPort;
  private final ObjectMapper objectMapper;

  /**
   * @param idempotencyPort reads and resolves the Fase-1 lock
   * @param orderPaymentPort records the confirmed capture
   * @param orderWritePort persists the materialized order
   * @param findOrCreateGuestCustomerUseCase links a {@code WEB} guest checkout (customer.md §3.2)
   * @param registerNotificationUseCase registers the confirmation emails (notification.md §3.1)
   * @param clearCartUseCase empties the cart the order was built from
   * @param piiCryptoPort encrypts the buyer/delivery snapshot and decrypts the pending payload
   * @param objectMapper deserializes the pending payload
   */
  public ConfirmCheckoutService(
      CheckoutIdempotencyPort idempotencyPort,
      OrderPaymentPort orderPaymentPort,
      OrderWritePort orderWritePort,
      FindOrCreateGuestCustomerUseCase findOrCreateGuestCustomerUseCase,
      RegisterNotificationUseCase registerNotificationUseCase,
      ClearCartUseCase clearCartUseCase,
      PiiCryptoPort piiCryptoPort,
      ObjectMapper objectMapper) {
    this.idempotencyPort = idempotencyPort;
    this.orderPaymentPort = orderPaymentPort;
    this.orderWritePort = orderWritePort;
    this.findOrCreateGuestCustomerUseCase = findOrCreateGuestCustomerUseCase;
    this.registerNotificationUseCase = registerNotificationUseCase;
    this.clearCartUseCase = clearCartUseCase;
    this.piiCryptoPort = piiCryptoPort;
    this.objectMapper = objectMapper;
  }

  /**
   * @param command the decoded, signature-verified Redsys notification
   */
  @Override
  public void execute(ConfirmCheckoutCommand command) {
    LOGGER.debug("confirmCheckout idempotencyKey={} approved={}", command.idempotencyKey(), command.approved());
    Optional<IdempotencyLock> lock = idempotencyPort.find(command.idempotencyKey());
    if (lock.isEmpty() || !"PENDING".equals(lock.get().status())) {
      LOGGER.debug(
          "confirmCheckout idempotencyKey={} -> already resolved or unknown, ignoring (Redsys retry)",
          command.idempotencyKey());
      return;
    }
    PendingCheckoutPayload payload = decryptPayload(lock.get().checkoutPayloadEncrypted());
    OrderId orderId = OrderId.of(lock.get().resourceId());
    if (command.approved()) {
      confirm(orderId, command, payload);
    } else {
      compensate(orderId, payload);
    }
    idempotencyPort.markCompleted(command.idempotencyKey(), command.approved() ? 202 : 200);
    LOGGER.debug("confirmCheckout idempotencyKey={} -> resolved", command.idempotencyKey());
  }

  /**
   * @param orderId the order to materialize
   * @param command the approved notification
   * @param payload the decrypted pending-checkout payload
   */
  private void confirm(OrderId orderId, ConfirmCheckoutCommand command, PendingCheckoutPayload payload) {
    UUID customerId = resolveCustomer(payload);
    List<OrderItem> items = payload.items().stream().map(this::toOrderItem).toList();
    OrderDelivery delivery = toOrderDelivery(payload);
    Order order =
        Order.place(
            orderId, command.orderNumber(), OrderChannel.valueOf(payload.channel()), OrderFulfillment.valueOf(payload.fulfillment()),
            customerId, piiCryptoPort.encrypt(payload.buyerFirstName()), piiCryptoPort.encrypt(payload.buyerLastName()),
            piiCryptoPort.encrypt(payload.buyerEmail()), piiCryptoPort.hmac(payload.buyerEmail()),
            piiCryptoPort.encrypt(payload.buyerPhone()), piiCryptoPort.hmac(payload.buyerPhone()), items, delivery,
            payload.subtotal(), payload.shippingCost());
    orderWritePort.save(order);
    orderPaymentPort.confirmCharge(orderId, command.orderNumber(), PaymentMethod.CARD_ONLINE, order.total(), command.providerPaymentIntentId());
    clearCartUseCase.execute(new ClearCartCommand(payload.customerId(), payload.sessionToken()));
    registerNotificationUseCase.execute(new RegisterNotificationCommand("ORDER_CONFIRMED", orderId.value(), customerId));
    registerNotificationUseCase.execute(new RegisterNotificationCommand("STAFF_NEW_ORDER", orderId.value(), null));
  }

  /**
   * @param orderId the order that never happened
   * @param payload the decrypted pending-checkout payload, to reverse the exact reservation made
   */
  private void compensate(OrderId orderId, PendingCheckoutPayload payload) {
    LOGGER.debug("confirmCheckout orderId={} -> declined, reversing Fase 1", orderId);
    // Reverse the reservation: RegisterStockMovementUseCase (ADJUSTMENT, positive quantity) for
    // every managed line, DiscountReservationPort.release() for every discounted line — the exact
    // mirror of CheckoutService's reserveInventoryAndDiscounts (Phase 3a). Wire the two real
    // collaborators here once their signatures are confirmed, same note as that task.
    for (PendingCheckoutPayload.Item item : payload.items()) {
      LOGGER.debug("confirmCheckout release productId={} quantity={}", item.productId(), item.quantity());
    }
  }

  /**
   * @param payload the decrypted pending-checkout payload
   * @return the linked customer's id, or {@code null}
   */
  private UUID resolveCustomer(PendingCheckoutPayload payload) {
    if (payload.customerId() != null) {
      return payload.customerId();
    }
    var result =
        findOrCreateGuestCustomerUseCase.execute(
            new FindOrCreateGuestCustomerCommand(payload.buyerEmail(), payload.buyerFirstName(), payload.buyerLastName(), payload.buyerPhone()));
    return result != null ? result.value() : null;
  }

  /**
   * @param item the pending-checkout line
   * @return the domain snapshot
   */
  private OrderItem toOrderItem(PendingCheckoutPayload.Item item) {
    return OrderItem.snapshot(
        OrderItemId.newId(), com.floristeriarosy.domain.model.product.valueobject.ProductId.of(item.productId()),
        item.productName(), item.productAttributes(), item.unitPrice(), item.discountPrice(),
        item.discountId() != null ? com.floristeriarosy.domain.model.discount.valueobject.DiscountId.of(item.discountId()) : null,
        item.quantity());
  }

  /**
   * @param payload the decrypted pending-checkout payload
   * @return the domain delivery/pickup slot, or {@code null} for {@code NONE}
   */
  private OrderDelivery toOrderDelivery(PendingCheckoutPayload payload) {
    if (payload.delivery() != null) {
      var d = payload.delivery();
      return OrderDelivery.forDelivery(
          piiCryptoPort.encrypt(d.recipientName()), piiCryptoPort.encrypt(d.recipientPhone()), piiCryptoPort.encrypt(d.street()),
          piiCryptoPort.encrypt(d.number()), d.detail() != null ? piiCryptoPort.encrypt(d.detail()) : null, d.postalCode(),
          piiCryptoPort.encrypt(d.latitude().toString()), piiCryptoPort.encrypt(d.longitude().toString()), d.distanceKm(),
          d.deliveryDate(), d.slotFrom(), d.slotTo(), d.cardMessage() != null ? piiCryptoPort.encrypt(d.cardMessage()) : null);
    }
    if (payload.pickup() != null) {
      var p = payload.pickup();
      return OrderDelivery.forPickup(p.deliveryDate(), p.slotFrom(), p.slotTo());
    }
    return null;
  }

  /**
   * @param encryptedPayload the encrypted pending-checkout payload
   * @return the decoded payload
   */
  private PendingCheckoutPayload decryptPayload(byte[] encryptedPayload) {
    try {
      return objectMapper.readValue(piiCryptoPort.decrypt(encryptedPayload), PendingCheckoutPayload.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Could not deserialize the pending checkout payload", e);
    }
  }
}
```

**Notes for the implementer, same discipline as Phase 3a:** `compensate`'s
`RegisterStockMovementUseCase`/`DiscountReservationPort` calls are a stub, replace with the real
ones. `piiCryptoPort.encrypt(d.latitude().toString())` is a placeholder for however
`OrderDelivery`'s latitude/longitude fields are actually typed at the persistence boundary — check
`OrderDeliveryEntity` (Task 6) for the real column type before deciding whether this needs
encrypting-a-string-of-a-number at all, or whether the entity stores the `BigDecimal` encrypted
some other way; do not ship this exact line without checking. Jackson 3 import corrections apply
here too, same as every other task (`tools.jackson.*`, not `com.fasterxml.jackson.*`).

- [ ] **Step 7: Run tests to verify they pass, then commit**

Run: `mvn -q test -Dtest=ConfirmCheckoutServiceTest`

```bash
git add src/main/java/com/floristeriarosy/application/order/dto/PendingCheckoutPayload.java \
        src/main/java/com/floristeriarosy/application/order/dto/IdempotencyLock.java \
        src/main/java/com/floristeriarosy/application/order/port/out/CheckoutIdempotencyPort.java \
        src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/CheckoutIdempotencyPersistenceAdapter.java \
        src/main/java/com/floristeriarosy/application/order/command/ConfirmCheckoutCommand.java \
        src/main/java/com/floristeriarosy/application/order/port/in/ConfirmCheckoutUseCase.java \
        src/main/java/com/floristeriarosy/application/order/service/ConfirmCheckoutService.java \
        src/main/java/com/floristeriarosy/application/order/service/CheckoutService.java \
        src/test/java/com/floristeriarosy/application/order/service/ConfirmCheckoutServiceTest.java
git commit -m "feat(order): ConfirmCheckoutService — Fase 3a/3b (order.md §3.2, §3.10, ADR-017)"
```

---

## Task 8: The Redsys notification endpoint, `GET /orders`, and `GetCheckoutStatusService`'s last gap

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/payment/port/in/ReceiveRedsysNotificationUseCase.java`
- Create: `src/main/java/com/floristeriarosy/application/payment/service/ReceiveRedsysNotificationService.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/web/controller/payment/RedsysNotificationController.java`
- Create: `src/main/java/com/floristeriarosy/application/order/port/in/GetOrderUseCase.java`
- Create: `src/main/java/com/floristeriarosy/application/order/port/in/GetOrdersUseCase.java`
- Create: `src/main/java/com/floristeriarosy/application/order/query/GetOrderQuery.java`
- Create: `src/main/java/com/floristeriarosy/application/order/query/GetOrdersQuery.java`
- Create: `src/main/java/com/floristeriarosy/application/order/dto/OrderDto.java`
- Create: `src/main/java/com/floristeriarosy/application/order/service/GetOrderService.java`
- Create: `src/main/java/com/floristeriarosy/application/order/service/GetOrdersService.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/web/response/order/OrderResponse.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/web/response/order/OrderItemResponse.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/web/controller/order/OrderController.java`
- Modify: `src/main/java/com/floristeriarosy/application/order/service/GetCheckoutStatusService.java`
  (resolve `orderNumber` for `COMPLETED`, now that `OrderReadPort` exists — Phase 3a's own
  Self-Review flagged this exact gap as Phase 3b's job, not a leftover)
- Test: `src/test/java/com/floristeriarosy/application/payment/service/ReceiveRedsysNotificationServiceTest.java`
- Test: `src/test/java/com/floristeriarosy/infrastructure/web/controller/order/OrderControllerTest.java`

**Interfaces:**
- Consumes: `RedsysSignature` (Phase 2), `RedsysProperties` (Phase 2), `ConfirmCheckoutUseCase`
  (Task 7), `OrderReadPort` (Task 6).
- Produces: `POST /api/v1/payments/redsys/notification` (public, signature-verified, no session —
  payment.md §4), `GET /orders/{orderNumber}`, `GET /orders` (order.md §4).

- [ ] **Step 1: Create `ReceiveRedsysNotificationUseCase` and its service**

```java
package com.floristeriarosy.application.payment.port.in;

/** Verifies and resolves Redsys's checkout notification (ADR-017). No controller-owned business
 * logic beyond signature verification — {@code order}'s {@code ConfirmCheckoutUseCase} does the rest. */
public interface ReceiveRedsysNotificationUseCase {

  /**
   * @param encodedMerchantParameters the raw {@code Ds_MerchantParameters} field, Base64-encoded
   * @param receivedSignature the raw {@code Ds_Signature} field
   */
  void execute(String encodedMerchantParameters, String receivedSignature);
}
```

```java
package com.floristeriarosy.application.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floristeriarosy.application.order.command.ConfirmCheckoutCommand;
import com.floristeriarosy.application.order.port.in.ConfirmCheckoutUseCase;
import com.floristeriarosy.application.payment.port.in.ReceiveRedsysNotificationUseCase;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysProperties;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysSignature;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link ReceiveRedsysNotificationUseCase}: verify the signature before touching any
 * state, decode {@code Ds_Response} (0000-0099 approves, per Redsys convention), then hand off to
 * {@link ConfirmCheckoutUseCase} — this class knows Redsys's field names, {@code order} does not
 * (ADR-017).
 */
@Service
public class ReceiveRedsysNotificationService implements ReceiveRedsysNotificationUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReceiveRedsysNotificationService.class);

  private final RedsysProperties properties;
  private final ObjectMapper objectMapper;
  private final ConfirmCheckoutUseCase confirmCheckoutUseCase;

  /**
   * @param properties holds the merchant signing key
   * @param objectMapper decodes {@code Ds_MerchantParameters}
   * @param confirmCheckoutUseCase resolves the checkout, once verified
   */
  public ReceiveRedsysNotificationService(RedsysProperties properties, ObjectMapper objectMapper, ConfirmCheckoutUseCase confirmCheckoutUseCase) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.confirmCheckoutUseCase = confirmCheckoutUseCase;
  }

  /**
   * @param encodedMerchantParameters the raw {@code Ds_MerchantParameters} field
   * @param receivedSignature the raw {@code Ds_Signature} field
   * @throws IllegalArgumentException the signature does not verify — never processed further
   */
  @Override
  public void execute(String encodedMerchantParameters, String receivedSignature) {
    Map<String, String> params = decode(encodedMerchantParameters);
    String orderNumber = params.get("Ds_Merchant_Order") != null ? params.get("Ds_Merchant_Order") : params.get("Ds_Order");
    LOGGER.debug("receiveRedsysNotification orderNumber={}", orderNumber);
    if (!RedsysSignature.verify(properties.secretKey(), orderNumber, encodedMerchantParameters, receivedSignature)) {
      throw new IllegalArgumentException("Invalid Redsys signature for order " + orderNumber);
    }
    String responseCode = params.get("Ds_Response");
    boolean approved = responseCode != null && responseCode.matches("0\\d{3}") && Integer.parseInt(responseCode) <= 99;
    String idempotencyKey = params.get("Ds_Merchant_MerchantData");
    confirmCheckoutUseCase.execute(
        new ConfirmCheckoutCommand(
            idempotencyKey, OrderNumber.of(orderNumber), approved, approved ? params.get("Ds_AuthorisationCode") : null,
            approved ? null : "Ds_Response=" + responseCode));
    LOGGER.debug("receiveRedsysNotification orderNumber={} -> approved={}", orderNumber, approved);
  }

  /**
   * @param encodedMerchantParameters the Base64-encoded JSON
   * @return the decoded {@code Ds_*} fields
   */
  private Map<String, String> decode(String encodedMerchantParameters) {
    try {
      return objectMapper.readValue(Base64.getDecoder().decode(encodedMerchantParameters), Map.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("Could not decode Ds_MerchantParameters", e);
    }
  }
}
```

Fix the Jackson 3 imports (`tools.jackson.*`) before shipping, same as every other task — this
draft uses Jackson 2 package names as a placeholder for readability only.

- [ ] **Step 2: Write the failing service test**

```java
package com.floristeriarosy.application.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.port.in.ConfirmCheckoutUseCase;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ReceiveRedsysNotificationServiceTest {

  private static final RedsysProperties PROPERTIES =
      new RedsysProperties("999008881", "001", "sq7HjrUOBfKmC576ILgskD5srU870gJ7",
          "https://sis-t.redsys.es:25443/sis/realizarPago", "https://sis-t.redsys.es:25443/sis/rest/trataPeticionREST",
          "http://localhost:8080/api/v1/payments/redsys/notification", "http://localhost:5173/checkout/ok",
          "http://localhost:5173/checkout/ko");

  private final ConfirmCheckoutUseCase confirmCheckoutUseCase = mock(ConfirmCheckoutUseCase.class);
  private final ReceiveRedsysNotificationService service =
      new ReceiveRedsysNotificationService(PROPERTIES, new ObjectMapper(), confirmCheckoutUseCase);

  @Test
  void approvedNotificationConfirmsTheCheckout() {
    String params =
        Base64.getEncoder().encodeToString(
            "{\"Ds_Merchant_Order\":\"WEB-20260914-000001\",\"Ds_Response\":\"0000\",\"Ds_AuthorisationCode\":\"123456\",\"Ds_Merchant_MerchantData\":\"idem-1\"}"
                .getBytes());
    String signature =
        com.floristeriarosy.infrastructure.payment.redsys.RedsysSignature.sign(PROPERTIES.secretKey(), "WEB-20260914-000001", params);

    service.execute(params, signature);

    verify(confirmCheckoutUseCase).execute(any());
  }

  @Test
  void tamperedSignatureIsRejectedBeforeConfirming() {
    String params = Base64.getEncoder().encodeToString("{\"Ds_Merchant_Order\":\"WEB-20260914-000002\",\"Ds_Response\":\"0000\"}".getBytes());

    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> service.execute(params, "not-a-real-signature"));
    verify(confirmCheckoutUseCase, org.mockito.Mockito.never()).execute(any());
  }
}
```

- [ ] **Step 3: Run test to verify it fails, then implement and re-run**

Run: `mvn -q test -Dtest=ReceiveRedsysNotificationServiceTest`

- [ ] **Step 4: Create `RedsysNotificationController`**

```java
package com.floristeriarosy.infrastructure.web.controller.payment;

import com.floristeriarosy.application.payment.port.in.ReceiveRedsysNotificationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/payments/redsys/notification} (payment.md §4): unauthenticated, verified by
 * signature instead. Must be excluded from CSRF and from any session/auth filter — check {@code
 * SecurityConfig}'s existing exemptions (e.g. how the health endpoint or similar is excluded) and
 * add this path the same way rather than inventing a new exemption mechanism.
 */
@RestController
@RequestMapping("/api/v1/payments/redsys")
public class RedsysNotificationController {

  private final ReceiveRedsysNotificationUseCase receiveRedsysNotificationUseCase;

  /**
   * @param receiveRedsysNotificationUseCase verifies and resolves the notification
   */
  public RedsysNotificationController(ReceiveRedsysNotificationUseCase receiveRedsysNotificationUseCase) {
    this.receiveRedsysNotificationUseCase = receiveRedsysNotificationUseCase;
  }

  /**
   * @param dsSignatureVersion Redsys's signature version field, unused but required to be accepted
   * @param dsMerchantParameters the Base64-encoded parameters
   * @param dsSignature the signature to verify
   * @return {@code 200 OK} with plain-text body {@code "OK"} — Redsys retries until it sees exactly
   *     this
   */
  @PostMapping("/notification")
  public ResponseEntity<String> notification(
      @RequestParam("Ds_SignatureVersion") String dsSignatureVersion,
      @RequestParam("Ds_MerchantParameters") String dsMerchantParameters,
      @RequestParam("Ds_Signature") String dsSignature) {
    receiveRedsysNotificationUseCase.execute(dsMerchantParameters, dsSignature);
    return ResponseEntity.ok("OK");
  }
}
```

- [ ] **Step 5: `GetOrderUseCase`/`GetOrdersUseCase` and their services**

```java
package com.floristeriarosy.application.order.query;

/**
 * @param orderNumber the order to look up
 * @param customerId the requesting customer, or {@code null} for a guest
 * @param guestEmail a guest's email, required to prove ownership when {@code customerId} is
 *     {@code null} (order.md §4: "sin sesión, el número de pedido solo no basta")
 */
public record GetOrderQuery(String orderNumber, java.util.UUID customerId, String guestEmail) {}
```

```java
package com.floristeriarosy.application.order.query;

import java.util.UUID;

/**
 * @param customerId the authenticated customer whose orders to list
 */
public record GetOrdersQuery(UUID customerId) {}
```

```java
package com.floristeriarosy.application.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The public view of an order (order.md §6) — never the decrypted {@code buyer_*} beyond what the
 * owner already knows, never {@code customer_id}.
 *
 * @param id the order's identifier
 * @param orderNumber the business identifier
 * @param channel the sales channel
 * @param fulfillment how the order reaches the customer
 * @param status the fulfillment status
 * @param items the sold lines
 * @param subtotal the subtotal
 * @param shippingCost the shipping cost
 * @param total the total
 * @param placedAt when the order was placed
 */
public record OrderDto(
    UUID id, String orderNumber, String channel, String fulfillment, String status, List<Item> items,
    BigDecimal subtotal, BigDecimal shippingCost, BigDecimal total, Instant placedAt) {

  /**
   * @param productId the sold product
   * @param productName the snapshot name
   * @param productAttributes the snapshot attributes
   * @param unitPrice the snapshot unit price
   * @param discountPrice the snapshot discount price, or {@code null}
   * @param quantity units sold
   * @param lineTotal the line's total
   */
  public record Item(UUID productId, String productName, Map<String, Object> productAttributes, BigDecimal unitPrice, BigDecimal discountPrice, int quantity, BigDecimal lineTotal) {}
}
```

`GetOrderService`/`GetOrdersService` read via `OrderReadPort`, map `Order`/`OrderItem` to `OrderDto`
(decrypting nothing — `OrderDto` never carries buyer/delivery fields at all, matching order.md §6's
"la vista pública no incluye buyer_* descifrado"), and enforce ownership: `GetOrderService` throws
`OrderNotFoundException` (new — order.md §9's `ORDER_NOT_FOUND`, this task's first thrower) when
`customerId` doesn't match the order's `customer_id`, or, for a guest, when `guestEmail`'s HMAC
doesn't match `buyer_email_hash`. `GetOrdersService` filters by `customer_id` only — no admin
listing here (order.md §4's `/orders/all` is a future admin-listing phase, JDBC, out of scope).

- [ ] **Step 6: `OrderResponse`/`OrderItemResponse` and `OrderController`**

Mirror `OrderDto` exactly, no new fields. `OrderController`: `GET /orders/{orderNumber}` (public,
optional `customerId` from the same JWT-or-absent pattern `CheckoutController` uses, optional
`?email=` query param for a guest), `GET /orders` (customer only, paginate later if needed — this
task returns the full list, `order.md` §4 says "paginado" but no consumer needs pagination logic
yet; add it when the frontend actually asks for a page size, not speculatively).

- [ ] **Step 7: Close `GetCheckoutStatusService`'s `orderNumber` gap**

Inject `OrderReadPort`, and when the lock is `COMPLETED`, look up the order by `resourceId` (its
id) and return its `orderNumber` in the `CheckoutStatusDto` instead of `null`.

- [ ] **Step 8: Run the full build**

Run: `mvn -q clean verify`
Expected: `BUILD SUCCESS` — this closes the original "Phase 3: Checkout".

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/floristeriarosy/application/payment/port/in/ReceiveRedsysNotificationUseCase.java \
        src/main/java/com/floristeriarosy/application/payment/service/ReceiveRedsysNotificationService.java \
        src/main/java/com/floristeriarosy/infrastructure/web/controller/payment/RedsysNotificationController.java \
        src/main/java/com/floristeriarosy/application/order/port/in/GetOrderUseCase.java \
        src/main/java/com/floristeriarosy/application/order/port/in/GetOrdersUseCase.java \
        src/main/java/com/floristeriarosy/application/order/query/GetOrderQuery.java \
        src/main/java/com/floristeriarosy/application/order/query/GetOrdersQuery.java \
        src/main/java/com/floristeriarosy/application/order/dto/OrderDto.java \
        src/main/java/com/floristeriarosy/application/order/service/GetOrderService.java \
        src/main/java/com/floristeriarosy/application/order/service/GetOrdersService.java \
        src/main/java/com/floristeriarosy/application/order/service/GetCheckoutStatusService.java \
        src/main/java/com/floristeriarosy/infrastructure/web/response/order \
        src/main/java/com/floristeriarosy/infrastructure/web/controller/order/OrderController.java \
        src/main/java/com/floristeriarosy/domain/exception/order \
        src/test/java/com/floristeriarosy/application/payment/service/ReceiveRedsysNotificationServiceTest.java \
        src/test/java/com/floristeriarosy/infrastructure/web/controller/order/OrderControllerTest.java
git commit -m "feat(order): notification endpoint, GET /orders/{orderNumber}, GET /orders (order.md §4, ADR-017)"
```

---

## Self-Review

**Spec coverage:** order.md §3.2 (Fase 3a/3b) → Task 7. §3.9/§3.10 (reversal on decline) → Task 7.
§3.11 (guest linking) → Tasks 2-4, 7. §4 (endpoints) → Task 8. §6 (response shape) → Task 8.
customer.md §3.2 → Tasks 2-4. notification.md §1, §3.1 → Task 5. ADR-017 (notification handler,
signature verification, idempotent replay) → Task 8.

**Known incomplete pieces, flagged not hidden:** `ConfirmCheckoutService.compensate`'s reversal
calls are a stub (Task 7), same discipline as Phase 3a's own reservation stub — the implementer
replaces it with the real `RegisterStockMovementUseCase`/`DiscountReservationPort` calls.
`OrderDelivery`'s latitude/longitude handling in `toOrderDelivery` is flagged as needing a real
check against `OrderDeliveryEntity`'s actual column type (Task 6 vs Task 7 — written before Task 6
was implemented, since this is one plan authored ahead of execution, same situation Phase 3a was in
for its own cross-task dependencies).

**Deliberately out of scope:** `GET /orders/all` (admin, JDBC, paginated) — a future admin-listing
phase. Pagination on `GET /orders` — add when a real consumer needs it. `scheduled-tasks.md`'s
10-minute-abandonment sweep — Phase 7, cross-cutting, unchanged from the original phase plan.
Saved cards / `attachPaymentMethod` — Phase 6. `customer.md`'s registration/address/password
surface — its own future feature, this phase only ever touches the guest-linking rule.

**Placeholder scan:** every intentional gap above is called out in its own task, not silent.

**Type consistency:** `ConfirmCheckoutCommand`/`ConfirmCheckoutUseCase` (Task 7) match
`ReceiveRedsysNotificationService`'s call (Task 8) exactly. `OrderReadPort`/`OrderWritePort`
(Task 6) match `ConfirmCheckoutService` (Task 7) and `GetOrderService`/`GetOrdersService`
(Task 8) exactly. `CustomerReadPort`/`CustomerWritePort`/`FindOrCreateGuestCustomerUseCase`
(Tasks 2-4) match `ConfirmCheckoutService`'s `resolveCustomer` (Task 7) exactly.
`NotificationWritePort`/`RegisterNotificationUseCase` (Task 5) match `ConfirmCheckoutService`'s
two calls (Task 7) exactly.

---

Plan complete and saved to `docs/superpowers/plans/2026-09-14-order-payment-phase3b-confirm.md`.
Same handoff choice as before — subagent-driven, inline, or IMPLEMENTER/REVIEWER with your
`/ultrareview` gate — which approach?
