# Order + Payment — Phase 2: Payment Capability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the payment capability that `order` will consume in Phase 3: `OrderPaymentPort`
(defined by `order`), implemented by `payment` via `ChargeOrderPaymentService` /
`RefundOrderPaymentService`, backed by a real Redsys TPV Virtual adapter (ADR-017). No controller,
no wiring into checkout yet — that is Phase 3. This phase is done when `OrderPaymentPort.charge()`
for `CARD_ONLINE` produces a correctly signed Redsys redirect and for `MANUAL` produces an
already-captured `Payment`, and `OrderPaymentPort.refund()` refunds a captured payment in full,
calling Redsys for `CARD_ONLINE` and settling directly for `MANUAL` — all independently testable
with no real network calls except the one adapter task that talks to Redsys's sandbox.

**Architecture:** Capability-based ports (ADR-003), same pattern as `ProductInventoryPort` /
`RegisterStockMovementUseCase`: `order` defines `OrderPaymentPort`; `payment` defines its own input
ports (`ChargeOrderPaymentUseCase`, `RefundOrderPaymentUseCase`) and implements them with real
logic; a small adapter (`OrderPaymentAdapter`) implements `OrderPaymentPort` by delegating to those
two use cases — order never depends on payment's internals, only on the port it owns.

**Tech Stack:** Java 21, `java.net.http.HttpClient` (JDK stdlib — no new HTTP client dependency),
`javax.crypto` (JDK stdlib — no new crypto library) for the Redsys 3DES/HMAC-SHA256 signature,
Jackson (`ObjectMapper`, already on the classpath via `spring-boot-starter-web`) for
`Ds_MerchantParameters`, JUnit 5 + AssertJ.

**Spec:** `docs/features/payment.md` (sections 1-3, 7-9), `docs/features/order.md` (section 8),
`docs/architecture/ADR/ADR-017-card-payment-via-redsys-redirect.md`,
`docs/architecture/ADR/ADR-003-capability-based-ports.md`. Builds on Phase 1's `Payment`/`Order`
domain (`docs/superpowers/plans/2026-09-13-order-payment-foundation.md`, already merged).

## Global Constraints

- No wildcard imports. Constructor injection only.
- Every method — public and private — gets Javadoc (`CLAUDE.md`, Javadoc section).
- Services log entry (every parameter) and exit at `DEBUG`; the Redsys adapter logs entry/exit too
  (it does I/O) but **never logs `merchantKeyBase64`, `Ds_MerchantParameters`, or
  `Ds_Signature`** — those are the equivalent of a credential/PII field for this integration; log
  the order number and the outcome instead.
- **`RedsysSignature` is a justified utility class** (`CLAUDE.md`'s "no utility classes unless
  explicitly justified"): a fixed, external, well-documented cryptographic algorithm with no
  meaningful object state, needed by exactly one adapter, that must be unit-testable without a
  network call — same justification as the existing `PiiCrypto` class.
- The Redsys sandbox key from `GetNet.md` (`sq7HjrUOBfKmC576ILgskD5srU870gJ7`) is a test-only value
  documented publicly by Redsys for the shared UAT environment — safe to use as this project's
  local/test default, never as a real merchant's key. It goes in `application.yml` as the *default*
  for `app.payment.redsys.secret-key`, overridable by an env var for anything beyond local dev.
- `payments.provider` has no `CHECK` constraint (Phase 1 confirmed) — `PaymentProvider.REDSYS`/
  `MANUAL` need no migration.
- No new dependency: `HttpClient` and `javax.crypto` are JDK 21 stdlib; `ObjectMapper` is already a
  Spring bean.

---

## Task 1: `Payment` domain — capture, fail, refund

**Files:**
- Modify: `src/main/java/com/floristeriarosy/domain/model/payment/Payment.java`
- Modify: `src/test/java/com/floristeriarosy/domain/model/payment/PaymentTest.java`

**Interfaces:**
- Consumes: `Payment.initiate(...)` (Phase 1, unchanged).
- Produces: `Payment.capture(providerPaymentIntentId, capturedAt)`, `Payment.fail(reason)`,
  `Payment.refundInFull(providerReference, refundedAt)` — Tasks 8 and 9 call these. Named
  `refundInFull`, not `refund`, because this module only ever produces a total refund
  (`payment.md` §3.3); a future partial manual refund (Phase 6) gets its own, differently-named
  method — never overload one method to mean two different business rules.

- [ ] **Step 1: Write the failing tests**

Append to `PaymentTest`:

```java
  @Test
  void captureMovesFromPendingToCapturedAndStoresTheGatewayReference() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS,
            new BigDecimal("42.00"));
    Instant capturedAt = Instant.parse("2026-09-13T10:00:00Z");

    payment.capture("redsys-auth-123456", capturedAt);

    assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
    assertThat(payment.providerPaymentIntentId()).isEqualTo("redsys-auth-123456");
    assertThat(payment.capturedAt()).isEqualTo(capturedAt);
  }

  @Test
  void captureAcceptsANullReferenceForManualMethods() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CASH, PaymentProvider.MANUAL,
            new BigDecimal("15.00"));

    payment.capture(null, Instant.now());

    assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
    assertThat(payment.providerPaymentIntentId()).isNull();
  }

  @Test
  void captureRejectsAPaymentThatIsNotPending() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS,
            new BigDecimal("42.00"));
    payment.capture(null, Instant.now());

    assertThatIllegalStateException().isThrownBy(() -> payment.capture(null, Instant.now()));
  }

  @Test
  void failMovesFromPendingToFailedAndStoresTheReason() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS,
            new BigDecimal("42.00"));

    payment.fail("Card declined by issuer");

    assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.failureReason()).isEqualTo("Card declined by issuer");
  }

  @Test
  void refundInFullMovesFromCapturedToItselfAndRefundsTheWholeAmount() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS,
            new BigDecimal("42.00"));
    payment.capture("redsys-auth-123456", Instant.now());
    Instant refundedAt = Instant.parse("2026-09-14T10:00:00Z");

    payment.refundInFull("redsys-refund-789", refundedAt);

    assertThat(payment.refundedAmount()).isEqualByComparingTo("42.00");
    assertThat(payment.providerReference()).isEqualTo("redsys-refund-789");
    assertThat(payment.refundedAt()).isEqualTo(refundedAt);
  }

  @Test
  void refundInFullRejectsAPaymentThatIsNotCaptured() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CASH, PaymentProvider.MANUAL,
            new BigDecimal("15.00"));

    assertThatIllegalStateException()
        .isThrownBy(() -> payment.refundInFull(null, Instant.now()));
  }
```

Add the import `import static org.assertj.core.api.Assertions.assertThatIllegalStateException;` at
the top of `PaymentTest`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=PaymentTest`
Expected: compilation failure — `capture`/`fail`/`refundInFull` do not exist yet.

- [ ] **Step 3: Add the three methods to `Payment`**

Insert after `reconstitute(...)` and before the getters:

```java
  /**
   * Marks the charge captured — the money has moved (payment.md, section 3.1/3.2).
   *
   * @param providerPaymentIntentId the gateway's charge id, or {@code null} for {@code MANUAL}
   * @param capturedAt when the capture happened
   * @throws IllegalStateException the payment is not {@code PENDING}
   */
  public void capture(String providerPaymentIntentId, Instant capturedAt) {
    LOGGER.debug("capture id={} providerPaymentIntentId={}", id, providerPaymentIntentId);
    requireStatus(PaymentStatus.PENDING);
    this.providerPaymentIntentId = providerPaymentIntentId;
    this.status = PaymentStatus.CAPTURED;
    this.capturedAt = capturedAt;
    LOGGER.debug("capture id={} -> captured", id);
  }

  /**
   * Marks the charge failed — the gateway declined it, or it was never attempted for {@code
   * MANUAL} reasons that never reach this far in practice (payment.md, section 3.2).
   *
   * @param reason why the charge failed
   * @throws IllegalStateException the payment is not {@code PENDING}
   */
  public void fail(String reason) {
    LOGGER.debug("fail id={}", id);
    requireStatus(PaymentStatus.PENDING);
    this.status = PaymentStatus.FAILED;
    this.failureReason = reason;
    LOGGER.debug("fail id={} -> failed", id);
  }

  /**
   * Refunds the whole amount — the only refund this module ever produces automatically (payment.md,
   * section 3.3). {@code refundReason} is deliberately left {@code null}: the order's status
   * transition is already the reason.
   *
   * @param providerReference the gateway's refund id, or {@code null} for {@code MANUAL}
   * @param refundedAt when the refund happened
   * @throws IllegalStateException the payment is not {@code CAPTURED}
   */
  public void refundInFull(String providerReference, Instant refundedAt) {
    LOGGER.debug("refundInFull id={} providerReference={}", id, providerReference);
    requireStatus(PaymentStatus.CAPTURED);
    this.providerReference = providerReference;
    this.refundedAmount = amount;
    this.refundedAt = refundedAt;
    LOGGER.debug("refundInFull id={} -> refunded {}", id, amount);
  }

  /**
   * @param expected the status this payment must currently have
   * @throws IllegalStateException the current status is not {@code expected}
   */
  private void requireStatus(PaymentStatus expected) {
    if (status != expected) {
      throw new IllegalStateException("Payment " + id + " is " + status + ", expected " + expected);
    }
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=PaymentTest`
Expected: `Tests run: 8, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/floristeriarosy/domain/model/payment/Payment.java \
        src/test/java/com/floristeriarosy/domain/model/payment/PaymentTest.java
git commit -m "feat(payment): capture/fail/refundInFull transitions (payment.md §3.2-3.3)"
```

---

## Task 2: `OrderPaymentPort` and its command/result types

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/OrderPaymentPort.java`
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/ChargeCommand.java`
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/ChargeResult.java`
- Create: `src/main/java/com/floristeriarosy/application/order/port/out/RefundCommand.java`

**Interfaces:**
- Consumes: `OrderId`, `OrderNumber` (Phase 1), `PaymentMethod` (Phase 1), `Payment` (Phase 1).
- Produces: `OrderPaymentPort.charge(ChargeCommand)` → `ChargeResult`,
  `OrderPaymentPort.refund(RefundCommand)` → `Payment`. Phase 3's `CheckoutService`/
  `ConfirmCheckoutService` and Phase 4's `RejectOrderService`/`CancelOrderService` call these
  exact names. Task 10 (`OrderPaymentAdapter`) implements this interface.

No test for this task — it is pure interface/record declarations with no behaviour of their own,
same as every other port in this codebase.

- [ ] **Step 1: Create `ChargeCommand`**

```java
package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;

/**
 * What {@code order} asks {@link OrderPaymentPort#charge} to do (order.md, section 3.2;
 * payment.md, section 3.2).
 *
 * @param orderId the order being charged
 * @param orderNumber the business identifier — becomes {@code Ds_Merchant_Order} for {@code
 *     CARD_ONLINE} (ADR-017)
 * @param amount the amount to charge
 * @param method how the customer pays
 * @param paymentMethodToken the gateway token for a new card, required when {@code method} is
 *     {@code CARD_ONLINE}; saved cards (Phase 6) are not yet supported by this command
 * @param idempotencyKey the checkout's {@code Idempotency-Key} — becomes {@code
 *     Ds_Merchant_MerchantData} so the Redsys notification can find its way back (ADR-017)
 */
public record ChargeCommand(
    OrderId orderId,
    OrderNumber orderNumber,
    BigDecimal amount,
    PaymentMethod method,
    String paymentMethodToken,
    String idempotencyKey) {}
```

- [ ] **Step 2: Create `ChargeResult`**

```java
package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.payment.Payment;
import java.util.Map;

/**
 * The outcome of {@link OrderPaymentPort#charge} — either the customer must be redirected to
 * finish paying ({@code CARD_ONLINE}, ADR-017), or the charge is already settled ({@code MANUAL},
 * payment.md section 3.1).
 */
public sealed interface ChargeResult {

  /**
   * @param redirectUrl the Redsys form-post target
   * @param formFields the signed {@code Ds_*} fields the frontend must post as a form
   */
  record RedirectRequired(String redirectUrl, Map<String, String> formFields) implements ChargeResult {}

  /**
   * @param payment the already-captured payment, not yet persisted — the caller decides when to
   *     save it, inside its own transaction (payment.md, section 3.6)
   */
  record Captured(Payment payment) implements ChargeResult {}
}
```

- [ ] **Step 3: Create `RefundCommand`**

```java
package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;

/**
 * What {@code order} asks {@link OrderPaymentPort#refund} to do — always a full refund of the
 * order's captured payment (payment.md, section 3.3).
 *
 * @param orderId the order whose payment must be refunded
 */
public record RefundCommand(OrderId orderId) {}
```

- [ ] **Step 4: Create `OrderPaymentPort`**

```java
package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.payment.Payment;

/**
 * The frontier between {@code order} and payment capture (order.md, section 8; payment.md, section
 * 1): {@code order} asks "charge this" and "refund this" without knowing whether Redsys, cash, or
 * any other provider is behind it — same principle as {@code ProductInventoryPort} facing {@code
 * inventory}.
 */
public interface OrderPaymentPort {

  /**
   * @param command what to charge and how
   * @return a redirect to complete payment, or an already-settled charge
   */
  ChargeResult charge(ChargeCommand command);

  /**
   * @param command which order's payment to refund
   * @return the refunded payment
   */
  Payment refund(RefundCommand command);
}
```

- [ ] **Step 5: Compile and commit**

Run: `mvn -q compile`

```bash
git add src/main/java/com/floristeriarosy/application/order/port/out/OrderPaymentPort.java \
        src/main/java/com/floristeriarosy/application/order/port/out/ChargeCommand.java \
        src/main/java/com/floristeriarosy/application/order/port/out/ChargeResult.java \
        src/main/java/com/floristeriarosy/application/order/port/out/RefundCommand.java
git commit -m "feat(order): OrderPaymentPort and its command/result types (order.md §8)"
```

---

## Task 3: Payment's own input and output ports

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/payment/port/in/ChargeOrderPaymentUseCase.java`
- Create: `src/main/java/com/floristeriarosy/application/payment/port/in/RefundOrderPaymentUseCase.java`
- Create: `src/main/java/com/floristeriarosy/application/payment/command/ChargeOrderPaymentCommand.java`
- Create: `src/main/java/com/floristeriarosy/application/payment/command/RefundOrderPaymentCommand.java`
- Create: `src/main/java/com/floristeriarosy/application/payment/port/out/PaymentReadPort.java`
- Create: `src/main/java/com/floristeriarosy/application/payment/port/out/PaymentWritePort.java`

**Interfaces:**
- Consumes: `Payment`, `PaymentId` (Phase 1), `OrderId` (Phase 1), types from Task 2.
- Produces: `ChargeOrderPaymentUseCase.execute(ChargeOrderPaymentCommand)` →
  `com.floristeriarosy.application.order.port.out.ChargeResult`, `RefundOrderPaymentUseCase.execute
  (RefundOrderPaymentCommand)` → `Payment`, `PaymentReadPort.findByOrderId(OrderId)` →
  `Optional<Payment>`, `PaymentWritePort.save(Payment)` → `Payment`. Tasks 7-10 depend on these
  exact names.

Payment's own commands mirror `order`'s `ChargeCommand`/`RefundCommand` (Task 2) field-for-field —
two records, not one shared type, because `order`'s port and `payment`'s use case are separate
capabilities per ADR-003 that happen to need the same data today; collapsing them into one type
would couple two modules' contracts that should be free to evolve independently.

No test for this task — pure interface/record declarations.

- [ ] **Step 1: Create the commands**

```java
package com.floristeriarosy.application.payment.command;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;

/**
 * @param orderId the order being charged
 * @param orderNumber the business identifier, becomes {@code Ds_Merchant_Order} for {@code
 *     CARD_ONLINE} (ADR-017)
 * @param amount the amount to charge
 * @param method how the customer pays
 * @param paymentMethodToken the gateway token for a new card, required for {@code CARD_ONLINE}
 * @param idempotencyKey becomes {@code Ds_Merchant_MerchantData} (ADR-017)
 */
public record ChargeOrderPaymentCommand(
    OrderId orderId,
    OrderNumber orderNumber,
    BigDecimal amount,
    PaymentMethod method,
    String paymentMethodToken,
    String idempotencyKey) {}
```

```java
package com.floristeriarosy.application.payment.command;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;

/**
 * @param orderId the order whose captured payment must be refunded in full
 */
public record RefundOrderPaymentCommand(OrderId orderId) {}
```

- [ ] **Step 2: Create the input ports**

```java
package com.floristeriarosy.application.payment.port.in;

import com.floristeriarosy.application.order.port.out.ChargeResult;
import com.floristeriarosy.application.payment.command.ChargeOrderPaymentCommand;

/** Implements {@code OrderPaymentPort.charge} (payment.md, section 7). */
public interface ChargeOrderPaymentUseCase {

  /**
   * @param command what to charge and how
   * @return a redirect to complete payment, or an already-settled charge
   */
  ChargeResult execute(ChargeOrderPaymentCommand command);
}
```

```java
package com.floristeriarosy.application.payment.port.in;

import com.floristeriarosy.application.payment.command.RefundOrderPaymentCommand;
import com.floristeriarosy.domain.model.payment.Payment;

/** Implements {@code OrderPaymentPort.refund} (payment.md, section 7). */
public interface RefundOrderPaymentUseCase {

  /**
   * @param command which order's payment to refund
   * @return the refunded payment
   */
  Payment execute(RefundOrderPaymentCommand command);
}
```

- [ ] **Step 3: Create the output ports**

```java
package com.floristeriarosy.application.payment.port.out;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.util.Optional;

/** Read access to {@code payments} (payment.md, section 8). */
public interface PaymentReadPort {

  /**
   * @param id the payment to load
   * @return the payment, if it exists
   */
  Optional<Payment> findById(PaymentId id);

  /**
   * @param orderId the order whose payment to load
   * @return the order's payment, if one was captured
   */
  Optional<Payment> findByOrderId(OrderId orderId);
}
```

```java
package com.floristeriarosy.application.payment.port.out;

import com.floristeriarosy.domain.model.payment.Payment;

/** Write access to {@code payments} (payment.md, section 8). */
public interface PaymentWritePort {

  /**
   * @param payment the payment to insert or update
   * @return the saved payment, with timestamps populated by the database
   */
  Payment save(Payment payment);
}
```

- [ ] **Step 4: Compile and commit**

Run: `mvn -q compile`

```bash
git add src/main/java/com/floristeriarosy/application/payment/port \
        src/main/java/com/floristeriarosy/application/payment/command
git commit -m "feat(payment): ChargeOrderPaymentUseCase/RefundOrderPaymentUseCase ports (payment.md §7-8)"
```

---

## Task 4: `PaymentGatewayPort` and its request/result types

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/payment/port/out/PaymentGatewayPort.java`
- Create: `src/main/java/com/floristeriarosy/application/payment/port/out/GatewayChargeRequest.java`
- Create: `src/main/java/com/floristeriarosy/application/payment/port/out/RedsysRedirect.java`
- Create: `src/main/java/com/floristeriarosy/application/payment/port/out/GatewayRefundRequest.java`
- Create: `src/main/java/com/floristeriarosy/application/payment/port/out/GatewayRefundResult.java`

**Interfaces:**
- Consumes: `OrderNumber` (Phase 1).
- Produces: `PaymentGatewayPort.charge(GatewayChargeRequest)` → `RedsysRedirect`,
  `PaymentGatewayPort.refund(GatewayRefundRequest)` → `GatewayRefundResult`. Task 6's
  `RedsysPaymentGatewayAdapter` implements this; Task 8's `ChargeOrderPaymentService` and Task 9's
  `RefundOrderPaymentService` call it only for `CARD_ONLINE`/`REDSYS` — `MANUAL` never touches this
  port (payment.md, section 3.1).

`RedsysRedirect` names the type after Redsys deliberately, unlike the provider-agnostic port itself:
today Redsys is the only gateway with a redirect outcome, and the type only needs to be renamed
alongside a real second gateway, if one is ever added — YAGNI against a provider-agnostic name for
a concept ADR-017 ties specifically to this one integration.

No test for this task — pure interface/record declarations.

- [ ] **Step 1: Create the request/result types**

```java
package com.floristeriarosy.application.payment.port.out;

import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.math.BigDecimal;

/**
 * @param orderNumber becomes {@code Ds_Merchant_Order} (ADR-017)
 * @param amount the amount to charge
 * @param paymentMethodToken the gateway token for a new card
 * @param merchantData becomes {@code Ds_Merchant_MerchantData}, carrying the checkout's
 *     {@code Idempotency-Key} back through the notification (ADR-017)
 */
public record GatewayChargeRequest(
    OrderNumber orderNumber, BigDecimal amount, String paymentMethodToken, String merchantData) {}
```

```java
package com.floristeriarosy.application.payment.port.out;

import java.util.Map;

/**
 * @param redirectUrl the Redsys form-post target (test or production, per configuration)
 * @param formFields the signed {@code Ds_*} fields to post as a form
 */
public record RedsysRedirect(String redirectUrl, Map<String, String> formFields) {}
```

```java
package com.floristeriarosy.application.payment.port.out;

import java.math.BigDecimal;

/**
 * @param orderNumber the original charge's order number, needed to diversify the signature
 *     (ADR-017)
 * @param providerPaymentIntentId the original charge's gateway reference
 * @param amount the full amount to refund (payment.md, section 3.3: always total)
 */
public record GatewayRefundRequest(String orderNumber, String providerPaymentIntentId, BigDecimal amount) {}
```

```java
package com.floristeriarosy.application.payment.port.out;

/**
 * @param success whether the gateway confirmed the refund
 * @param providerReference the gateway's refund id, present only when {@code success}
 * @param failureReason why the gateway refused, present only when not {@code success}
 */
public record GatewayRefundResult(boolean success, String providerReference, String failureReason) {}
```

- [ ] **Step 2: Create `PaymentGatewayPort`**

```java
package com.floristeriarosy.application.payment.port.out;

/**
 * The frontier with the real payment gateway (payment.md, section 8): isolates Redsys from the
 * rest of this module. Only ever called for {@code CARD_ONLINE} — {@code MANUAL} methods never
 * reach this port (payment.md, section 3.1).
 */
public interface PaymentGatewayPort {

  /**
   * @param request what to charge
   * @return the signed redirect the customer must be sent to
   */
  RedsysRedirect charge(GatewayChargeRequest request);

  /**
   * @param request what to refund
   * @return whether the gateway confirmed the refund
   */
  GatewayRefundResult refund(GatewayRefundRequest request);
}
```

- [ ] **Step 3: Compile and commit**

Run: `mvn -q compile`

```bash
git add src/main/java/com/floristeriarosy/application/payment/port/out/PaymentGatewayPort.java \
        src/main/java/com/floristeriarosy/application/payment/port/out/GatewayChargeRequest.java \
        src/main/java/com/floristeriarosy/application/payment/port/out/RedsysRedirect.java \
        src/main/java/com/floristeriarosy/application/payment/port/out/GatewayRefundRequest.java \
        src/main/java/com/floristeriarosy/application/payment/port/out/GatewayRefundResult.java
git commit -m "feat(payment): PaymentGatewayPort (payment.md §8, ADR-017)"
```

---

## Task 5: `RedsysSignature` — the signing/verification algorithm

**Files:**
- Create: `src/main/java/com/floristeriarosy/infrastructure/payment/redsys/RedsysSignature.java`
- Test: `src/test/java/com/floristeriarosy/infrastructure/payment/redsys/RedsysSignatureTest.java`

**Interfaces:**
- Consumes: JDK `javax.crypto`/`java.security` only.
- Produces: `RedsysSignature.sign(merchantKeyBase64, orderNumber, merchantParametersBase64)` →
  `String`, `RedsysSignature.verify(merchantKeyBase64, orderNumber, merchantParametersBase64,
  receivedSignatureBase64)` → `boolean`. Task 6's adapter calls `sign`; Phase 3's notification
  handler calls `verify`.

- [ ] **Step 1: Write the failing tests**

```java
package com.floristeriarosy.infrastructure.payment.redsys;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Redsys signature algorithm (ADR-017), verified against the documented sandbox key from
 * {@code GetNet.md}: {@code sq7HjrUOBfKmC576ILgskD5srU870gJ7}.
 */
class RedsysSignatureTest {

  private static final String SANDBOX_KEY = "sq7HjrUOBfKmC576ILgskD5srU870gJ7";

  @Test
  void signIsDeterministicForTheSameInputs() {
    String params = Base64.getEncoder().encodeToString("{\"amount\":\"1000\"}".getBytes());

    String first = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000001", params);
    String second = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000001", params);

    assertThat(first).isEqualTo(second);
  }

  @Test
  void signDiffersWhenTheOrderNumberDiffers() {
    String params = Base64.getEncoder().encodeToString("{\"amount\":\"1000\"}".getBytes());

    String forFirstOrder = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000001", params);
    String forSecondOrder = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000002", params);

    assertThat(forFirstOrder).isNotEqualTo(forSecondOrder);
  }

  @Test
  void signDiffersWhenTheParametersDiffer() {
    String first = RedsysSignature.sign(
        SANDBOX_KEY, "WEB-20260913-000001", Base64.getEncoder().encodeToString("{\"amount\":\"1000\"}".getBytes()));
    String second = RedsysSignature.sign(
        SANDBOX_KEY, "WEB-20260913-000001", Base64.getEncoder().encodeToString("{\"amount\":\"2000\"}".getBytes()));

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void verifyAcceptsASignatureThisSameAlgorithmProduced() {
    String params = Base64.getEncoder().encodeToString("{\"amount\":\"1000\"}".getBytes());
    String signature = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000001", params);

    boolean valid = RedsysSignature.verify(SANDBOX_KEY, "WEB-20260913-000001", params, signature);

    assertThat(valid).isTrue();
  }

  @Test
  void verifyRejectsATamperedSignature() {
    String params = Base64.getEncoder().encodeToString("{\"amount\":\"1000\"}".getBytes());
    String signature = RedsysSignature.sign(SANDBOX_KEY, "WEB-20260913-000001", params);

    boolean valid = RedsysSignature.verify(SANDBOX_KEY, "WEB-20260913-000001", params, signature + "x");

    assertThat(valid).isFalse();
  }

  @Test
  void signHandlesAnOrderNumberWhoseByteLengthIsAlreadyAMultipleOfEight() {
    String params = Base64.getEncoder().encodeToString("{}".getBytes());

    String signature = RedsysSignature.sign(SANDBOX_KEY, "12345678", params);

    assertThat(signature).isNotBlank();
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=RedsysSignatureTest`
Expected: compilation failure — `RedsysSignature` does not exist yet.

- [ ] **Step 3: Implement `RedsysSignature`**

```java
package com.floristeriarosy.infrastructure.payment.redsys;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Redsys TPV Virtual signature algorithm (ADR-017): a per-order 3DES-CBC-derived key, then
 * HMAC-SHA256 over the Base64 {@code Ds_MerchantParameters}. Pure and stateless — the same
 * computation signs an outgoing request and verifies an incoming notification.
 */
public final class RedsysSignature {

  private static final String DESEDE_TRANSFORMATION = "DESede/CBC/NoPadding";
  private static final byte[] ZERO_IV = new byte[8];

  private RedsysSignature() {}

  /**
   * @param merchantKeyBase64 the merchant's secret key, Base64-encoded, as provided by the bank
   * @param orderNumber the order number this signature is diversified by
   * @param merchantParametersBase64 the Base64-encoded {@code Ds_MerchantParameters} to sign
   * @return the Base64-encoded {@code Ds_Signature}
   */
  public static String sign(String merchantKeyBase64, String orderNumber, String merchantParametersBase64) {
    byte[] orderKey = deriveOrderKey(merchantKeyBase64, orderNumber);
    return hmacSha256Base64(orderKey, merchantParametersBase64);
  }

  /**
   * @param merchantKeyBase64 the merchant's secret key, Base64-encoded
   * @param orderNumber the order number the received signature is diversified by
   * @param merchantParametersBase64 the Base64-encoded {@code Ds_MerchantParameters} received
   * @param receivedSignatureBase64 the {@code Ds_Signature} received alongside it
   * @return whether the received signature matches what this merchant key would have produced
   */
  public static boolean verify(
      String merchantKeyBase64,
      String orderNumber,
      String merchantParametersBase64,
      String receivedSignatureBase64) {
    String expected = sign(merchantKeyBase64, orderNumber, merchantParametersBase64);
    return constantTimeEquals(expected, receivedSignatureBase64);
  }

  /**
   * @param merchantKeyBase64 the merchant's secret key, Base64-encoded
   * @param orderNumber the order number to diversify the key by
   * @return the per-order 3DES-derived key
   */
  private static byte[] deriveOrderKey(String merchantKeyBase64, String orderNumber) {
    try {
      byte[] key = Base64.getDecoder().decode(merchantKeyBase64);
      byte[] padded = padToMultipleOf8(orderNumber.getBytes(StandardCharsets.UTF_8));
      Cipher cipher = Cipher.getInstance(DESEDE_TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "DESede"), new IvParameterSpec(ZERO_IV));
      return cipher.doFinal(padded);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not derive the Redsys per-order key", e);
    }
  }

  /**
   * @param key the HMAC key
   * @param value the value to authenticate
   * @return the Base64-encoded HMAC-SHA256 of {@code value} under {@code key}
   */
  private static String hmacSha256Base64(byte[] key, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not compute the Redsys HMAC", e);
    }
  }

  /**
   * @param input the bytes to pad
   * @return {@code input} padded with trailing zero bytes to the next multiple of 8, unchanged if
   *     already a multiple of 8
   */
  private static byte[] padToMultipleOf8(byte[] input) {
    int remainder = input.length % 8;
    return remainder == 0 ? input : Arrays.copyOf(input, input.length + (8 - remainder));
  }

  /**
   * @param a the first value
   * @param b the second value
   * @return whether {@code a} and {@code b} are equal, compared in constant time to avoid a timing
   *     side-channel on signature verification
   */
  private static boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=RedsysSignatureTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/floristeriarosy/infrastructure/payment/redsys/RedsysSignature.java \
        src/test/java/com/floristeriarosy/infrastructure/payment/redsys/RedsysSignatureTest.java
git commit -m "feat(payment): RedsysSignature — 3DES+HMAC-SHA256 (ADR-017)"
```

---

## Task 6: `RedsysProperties` and `RedsysPaymentGatewayAdapter`

**Files:**
- Create: `src/main/java/com/floristeriarosy/infrastructure/payment/redsys/RedsysProperties.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/payment/redsys/RedsysPaymentGatewayAdapter.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/floristeriarosy/infrastructure/payment/redsys/RedsysPaymentGatewayAdapterTest.java`

**Interfaces:**
- Consumes: `PaymentGatewayPort`, `GatewayChargeRequest`, `RedsysRedirect`, `GatewayRefundRequest`,
  `GatewayRefundResult` (Task 4), `RedsysSignature` (Task 5).
- Produces: `RedsysPaymentGatewayAdapter implements PaymentGatewayPort` — the bean Task 8/9's
  services get injected with.

`charge(...)` never makes a network call — Redsys's redirect model means "charging" is signing a
form for the browser to submit (ADR-017); only `refund(...)` calls Redsys's REST endpoint, since a
merchant-initiated refund is a real server-to-server operation.

- [ ] **Step 1: Add configuration to `application.yml`**

Add under the root, alongside other `app.*` blocks:

```yaml
app:
  payment:
    redsys:
      merchant-code: ${REDSYS_MERCHANT_CODE:999008881}
      terminal: ${REDSYS_TERMINAL:001}
      secret-key: ${REDSYS_SECRET_KEY:sq7HjrUOBfKmC576ILgskD5srU870gJ7}
      redirect-url: ${REDSYS_REDIRECT_URL:https://sis-t.redsys.es:25443/sis/realizarPago}
      rest-url: ${REDSYS_REST_URL:https://sis-t.redsys.es:25443/sis/rest/trataPeticionREST}
      notification-url: ${REDSYS_NOTIFICATION_URL:http://localhost:8080/api/v1/payments/redsys/notification}
      url-ok: ${REDSYS_URL_OK:http://localhost:5173/checkout/ok}
      url-ko: ${REDSYS_URL_KO:http://localhost:5173/checkout/ko}
```

`999008881` is Redsys's own published test merchant code for the shared sandbox — a real value,
not a placeholder, same status as the sandbox secret key. All of it is overridable by env var; only
the sandbox defaults are checked in, matching how `PiiCrypto`'s insecure default key is handled.

- [ ] **Step 2: Create `RedsysProperties`**

```java
package com.floristeriarosy.infrastructure.payment.redsys;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redsys TPV Virtual configuration (ADR-017), bound from {@code app.payment.redsys.*}.
 *
 * @param merchantCode the FUC assigned by the bank
 * @param terminal the terminal number within that FUC
 * @param secretKey the merchant's Base64-encoded signing key
 * @param redirectUrl where the browser is sent to pay
 * @param restUrl the REST endpoint used for server-to-server operations (refunds)
 * @param notificationUrl the URL Redsys calls back with the result (ADR-017)
 * @param urlOk where the browser returns to on success
 * @param urlKo where the browser returns to on failure
 */
@ConfigurationProperties(prefix = "app.payment.redsys")
public record RedsysProperties(
    String merchantCode,
    String terminal,
    String secretKey,
    String redirectUrl,
    String restUrl,
    String notificationUrl,
    String urlOk,
    String urlKo) {}
```

Add `@org.springframework.boot.context.properties.EnableConfigurationProperties(RedsysProperties.class)`
to the main `@SpringBootApplication` class (find it via `grep -rl "@SpringBootApplication" src/main/java`)
if this project does not already scan `@ConfigurationProperties` classes by component scan; check
first whether other `*Properties` records already exist and follow whatever registration mechanism
they use instead of introducing a second one.

- [ ] **Step 3: Write the failing test for `charge` (no network involved)**

```java
package com.floristeriarosy.infrastructure.payment.redsys;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.payment.port.out.GatewayChargeRequest;
import com.floristeriarosy.application.payment.port.out.RedsysRedirect;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** {@link RedsysPaymentGatewayAdapter#charge} builds a signed redirect with no network call. */
class RedsysPaymentGatewayAdapterTest {

  private static final RedsysProperties PROPERTIES =
      new RedsysProperties(
          "999008881",
          "001",
          "sq7HjrUOBfKmC576ILgskD5srU870gJ7",
          "https://sis-t.redsys.es:25443/sis/realizarPago",
          "https://sis-t.redsys.es:25443/sis/rest/trataPeticionREST",
          "http://localhost:8080/api/v1/payments/redsys/notification",
          "http://localhost:5173/checkout/ok",
          "http://localhost:5173/checkout/ko");

  private final RedsysPaymentGatewayAdapter adapter =
      new RedsysPaymentGatewayAdapter(PROPERTIES, new ObjectMapper());

  @Test
  void chargeBuildsARedirectWithASignatureThatVerifies() {
    GatewayChargeRequest request =
        new GatewayChargeRequest(
            OrderNumber.of("WEB-20260913-000001"), new BigDecimal("42.00"), "tok_visa", "idem-key-1");

    RedsysRedirect redirect = adapter.charge(request);

    assertThat(redirect.redirectUrl()).isEqualTo(PROPERTIES.redirectUrl());
    assertThat(redirect.formFields()).containsKeys("Ds_SignatureVersion", "Ds_MerchantParameters", "Ds_Signature");
    String params = redirect.formFields().get("Ds_MerchantParameters");
    String signature = redirect.formFields().get("Ds_Signature");
    assertThat(RedsysSignature.verify(PROPERTIES.secretKey(), "WEB-20260913-000001", params, signature)).isTrue();
  }

  @Test
  void chargeEncodesTheAmountInCents() {
    GatewayChargeRequest request =
        new GatewayChargeRequest(
            OrderNumber.of("WEB-20260913-000002"), new BigDecimal("42.50"), "tok_visa", "idem-key-2");

    RedsysRedirect redirect = adapter.charge(request);

    String decodedParams =
        new String(Base64.getDecoder().decode(redirect.formFields().get("Ds_MerchantParameters")));
    assertThat(decodedParams).contains("\"Ds_Merchant_Amount\":\"4250\"");
  }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -q test -Dtest=RedsysPaymentGatewayAdapterTest`
Expected: compilation failure — `RedsysPaymentGatewayAdapter` does not exist yet.

- [ ] **Step 5: Implement `RedsysPaymentGatewayAdapter`**

```java
package com.floristeriarosy.infrastructure.payment.redsys;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floristeriarosy.application.payment.port.out.GatewayChargeRequest;
import com.floristeriarosy.application.payment.port.out.GatewayRefundRequest;
import com.floristeriarosy.application.payment.port.out.GatewayRefundResult;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.application.payment.port.out.RedsysRedirect;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implements {@link PaymentGatewayPort} against Redsys TPV Virtual (ADR-017). {@link #charge} is a
 * pure computation — Redsys's redirect model means the "call" is the browser posting a signed form,
 * this backend never talks to Redsys over the network to start a charge. {@link #refund} does make
 * a real server-to-server call, since a merchant-initiated refund has no redirect step.
 */
@Component
public class RedsysPaymentGatewayAdapter implements PaymentGatewayPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(RedsysPaymentGatewayAdapter.class);
  private static final String SIGNATURE_VERSION = "HMAC_SHA256_V1";
  private static final String CURRENCY_EUR = "978";
  private static final String TRANSACTION_TYPE_AUTHORIZATION = "0";
  private static final String TRANSACTION_TYPE_REFUND = "3";

  private final RedsysProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  /**
   * @param properties Redsys merchant configuration
   * @param objectMapper builds {@code Ds_MerchantParameters}' JSON
   */
  public RedsysPaymentGatewayAdapter(RedsysProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  /**
   * @param request what to charge
   * @return the signed redirect the customer must be sent to
   */
  @Override
  public RedsysRedirect charge(GatewayChargeRequest request) {
    LOGGER.debug("charge orderNumber={} amount={}", request.orderNumber(), request.amount());
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("Ds_Merchant_Amount", toCents(request.amount()));
    params.put("Ds_Merchant_Order", request.orderNumber().value());
    params.put("Ds_Merchant_MerchantCode", properties.merchantCode());
    params.put("Ds_Merchant_Currency", CURRENCY_EUR);
    params.put("Ds_Merchant_TransactionType", TRANSACTION_TYPE_AUTHORIZATION);
    params.put("Ds_Merchant_Terminal", properties.terminal());
    params.put("Ds_Merchant_MerchantURL", properties.notificationUrl());
    params.put("Ds_Merchant_UrlOK", properties.urlOk());
    params.put("Ds_Merchant_UrlKO", properties.urlKo());
    params.put("Ds_Merchant_MerchantData", request.merchantData());
    params.put("Ds_Merchant_Identifier", request.paymentMethodToken());
    String encodedParams = encodeParameters(params);
    String signature = RedsysSignature.sign(properties.secretKey(), request.orderNumber().value(), encodedParams);
    Map<String, String> formFields =
        Map.of(
            "Ds_SignatureVersion", SIGNATURE_VERSION,
            "Ds_MerchantParameters", encodedParams,
            "Ds_Signature", signature);
    LOGGER.debug("charge orderNumber={} -> redirect built", request.orderNumber());
    return new RedsysRedirect(properties.redirectUrl(), formFields);
  }

  /**
   * @param request what to refund
   * @return whether Redsys confirmed the refund
   */
  @Override
  public GatewayRefundResult refund(GatewayRefundRequest request) {
    LOGGER.debug("refund orderNumber={} amount={}", request.orderNumber(), request.amount());
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("Ds_Merchant_Amount", toCents(request.amount()));
    params.put("Ds_Merchant_Order", request.orderNumber());
    params.put("Ds_Merchant_MerchantCode", properties.merchantCode());
    params.put("Ds_Merchant_Currency", CURRENCY_EUR);
    params.put("Ds_Merchant_TransactionType", TRANSACTION_TYPE_REFUND);
    params.put("Ds_Merchant_Terminal", properties.terminal());
    String encodedParams = encodeParameters(params);
    String signature = RedsysSignature.sign(properties.secretKey(), request.orderNumber(), encodedParams);
    String body = buildRestBody(encodedParams, signature);
    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder(URI.create(properties.restUrl()))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      GatewayRefundResult result = parseRefundResponse(response.body());
      LOGGER.debug("refund orderNumber={} -> success={}", request.orderNumber(), result.success());
      return result;
    } catch (java.io.IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.debug("refund orderNumber={} -> gateway call failed: {}", request.orderNumber(), e.getMessage());
      return new GatewayRefundResult(false, null, "Redsys call failed: " + e.getMessage());
    }
  }

  /**
   * @param params the {@code Ds_Merchant_*} fields to encode
   * @return the Base64-encoded {@code Ds_MerchantParameters} JSON
   */
  private String encodeParameters(Map<String, Object> params) {
    try {
      return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(params));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Could not serialize Ds_MerchantParameters", e);
    }
  }

  /**
   * @param encodedParams the already Base64-encoded {@code Ds_MerchantParameters}
   * @param signature the computed {@code Ds_Signature}
   * @return the JSON body Redsys's REST endpoint expects
   */
  private String buildRestBody(String encodedParams, String signature) {
    try {
      Map<String, String> body =
          Map.of(
              "Ds_SignatureVersion", SIGNATURE_VERSION,
              "Ds_MerchantParameters", encodedParams,
              "Ds_Signature", signature);
      return objectMapper.writeValueAsString(body);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Could not serialize the REST request body", e);
    }
  }

  /**
   * @param responseBody the raw REST response body
   * @return the parsed refund outcome; a response Redsys does not consider a success (a {@code
   *     Ds_Response} outside 0000-0099) is reported as {@code success=false}
   */
  private GatewayRefundResult parseRefundResponse(String responseBody) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, String> response = objectMapper.readValue(responseBody, Map.class);
      String encodedParams = response.get("Ds_MerchantParameters");
      @SuppressWarnings("unchecked")
      Map<String, String> decodedParams =
          objectMapper.readValue(Base64.getDecoder().decode(encodedParams), Map.class);
      String responseCode = decodedParams.get("Ds_Response");
      boolean success = responseCode != null && responseCode.matches("0\\d{3}") && Integer.parseInt(responseCode) <= 99;
      return success
          ? new GatewayRefundResult(true, decodedParams.get("Ds_AuthorisationCode"), null)
          : new GatewayRefundResult(false, null, "Ds_Response=" + responseCode);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return new GatewayRefundResult(false, null, "Could not parse Redsys response: " + e.getMessage());
    }
  }

  /**
   * @param amount the amount in euros
   * @return the amount in cents, as Redsys's integer-string format requires
   */
  private String toCents(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).toBigInteger().toString();
  }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q test -Dtest=RedsysPaymentGatewayAdapterTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/floristeriarosy/infrastructure/payment/redsys/RedsysProperties.java \
        src/main/java/com/floristeriarosy/infrastructure/payment/redsys/RedsysPaymentGatewayAdapter.java \
        src/main/resources/application.yml \
        src/test/java/com/floristeriarosy/infrastructure/payment/redsys/RedsysPaymentGatewayAdapterTest.java
git commit -m "feat(payment): RedsysPaymentGatewayAdapter (ADR-017)"
```

**Note for the implementer:** `refund`'s real network call cannot be exercised against Redsys's
actual sandbox inside this test (no live credentials in CI). If a REVIEWER wants an end-to-end
sanity check against the real Redsys UAT endpoint, that is a manual, one-off verification with
real sandbox credentials — not part of this automated suite.

---

## Task 7: Payment persistence

**Files:**
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/entity/payment/PaymentEntity.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/mapper/payment/PaymentPersistenceMapper.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/jpa/payment/repository/PaymentJpaRepository.java`
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/payment/PaymentPersistenceAdapter.java`
- Test: `src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/payment/PaymentPersistenceAdapterTest.java`

**Interfaces:**
- Consumes: `Payment` (Phase 1), `PaymentReadPort`/`PaymentWritePort` (Task 3).
- Produces: `PaymentPersistenceAdapter implements PaymentReadPort, PaymentWritePort` — Task 8/9's
  services get injected with this.

Follows `AdminUserEntity`/`AdminPersistenceMapper`/`AdminPersistenceAdapter` exactly (JPA only, no
JDBC — payment.md section 8 confirms no paginated listing lives in this module).

- [ ] **Step 1: Create `PaymentEntity`**

```java
package com.floristeriarosy.infrastructure.persistence.entity.payment;

import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.payment.PaymentProvider;
import com.floristeriarosy.domain.model.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping of the {@code payments} table (payment.md, section 2). No {@code @Version}: a
 * payment is written once (capture) and updated at most once more (refund), both driven by this
 * module's own use cases, never concurrently from two requests for the same payment. */
@Entity
@Table(name = "payments")
public class PaymentEntity {

  @Id private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentMethod method;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PaymentProvider provider;

  @Column(name = "provider_payment_intent_id", length = 255)
  private String providerPaymentIntentId;

  @Column(name = "provider_reference", length = 255)
  private String providerReference;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  @Column(name = "refunded_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal refundedAmount;

  @Column(name = "authorized_at")
  private Instant authorizedAt;

  @Column(name = "captured_at")
  private Instant capturedAt;

  @Column(name = "canceled_at")
  private Instant canceledAt;

  @Column(name = "refunded_at")
  private Instant refundedAt;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Column(name = "refund_reason", length = 500)
  private String refundReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA; not for application use. */
  protected PaymentEntity() {}

  /**
   * @param id the primary key
   * @param orderId the order this payment belongs to
   * @param method how the customer pays
   * @param provider who actually captures the charge
   * @param providerPaymentIntentId the gateway's charge id, or {@code null}
   * @param providerReference the gateway's refund id, or {@code null}
   * @param amount the amount to charge
   * @param status the lifecycle status
   * @param refundedAmount the amount refunded so far
   * @param authorizedAt when the charge was authorized, or {@code null}
   * @param capturedAt when the charge was captured, or {@code null}
   * @param canceledAt when the charge was canceled, or {@code null}
   * @param refundedAt when it was last refunded, or {@code null}
   * @param failureReason why the charge failed, or {@code null}
   * @param refundReason the manual refund's reason, or {@code null}
   * @param createdAt when the row was created, or {@code null} for a not-yet-persisted payment
   * @param updatedAt when the row was last updated, or {@code null} for a not-yet-persisted payment
   */
  public PaymentEntity(
      UUID id,
      UUID orderId,
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
    this.id = id;
    this.orderId = orderId;
    this.method = method;
    this.provider = provider;
    this.providerPaymentIntentId = providerPaymentIntentId;
    this.providerReference = providerReference;
    this.amount = amount;
    this.status = status;
    this.refundedAmount = refundedAmount;
    this.authorizedAt = authorizedAt;
    this.capturedAt = capturedAt;
    this.canceledAt = canceledAt;
    this.refundedAt = refundedAt;
    this.failureReason = failureReason;
    this.refundReason = refundReason;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** Sets {@code createdAt}/{@code updatedAt} in application code (same reasoning as
   * {@code AdminUserEntity}: {@code V1} only gives {@code created_at} a DB default). */
  @jakarta.persistence.PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  /** Refreshes {@code updatedAt} on every update; see {@link #onCreate()}. */
  @jakarta.persistence.PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  /** @return the primary key */
  public UUID getId() {
    return id;
  }

  /** @return the order this payment belongs to */
  public UUID getOrderId() {
    return orderId;
  }

  /** @return how the customer pays */
  public PaymentMethod getMethod() {
    return method;
  }

  /** @return who actually captures the charge */
  public PaymentProvider getProvider() {
    return provider;
  }

  /** @return the gateway's charge id, or {@code null} */
  public String getProviderPaymentIntentId() {
    return providerPaymentIntentId;
  }

  /** @return the gateway's refund id, or {@code null} */
  public String getProviderReference() {
    return providerReference;
  }

  /** @return the amount to charge */
  public BigDecimal getAmount() {
    return amount;
  }

  /** @return the lifecycle status */
  public PaymentStatus getStatus() {
    return status;
  }

  /** @return the amount refunded so far */
  public BigDecimal getRefundedAmount() {
    return refundedAmount;
  }

  /** @return when the charge was authorized, or {@code null} */
  public Instant getAuthorizedAt() {
    return authorizedAt;
  }

  /** @return when the charge was captured, or {@code null} */
  public Instant getCapturedAt() {
    return capturedAt;
  }

  /** @return when the charge was canceled, or {@code null} */
  public Instant getCanceledAt() {
    return canceledAt;
  }

  /** @return when it was last refunded, or {@code null} */
  public Instant getRefundedAt() {
    return refundedAt;
  }

  /** @return why the charge failed, or {@code null} */
  public String getFailureReason() {
    return failureReason;
  }

  /** @return the manual refund's reason, or {@code null} */
  public String getRefundReason() {
    return refundReason;
  }

  /** @return when the row was created */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** @return when the row was last updated */
  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
```

- [ ] **Step 2: Create `PaymentPersistenceMapper`**

```java
package com.floristeriarosy.infrastructure.persistence.mapper.payment;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import com.floristeriarosy.infrastructure.persistence.entity.payment.PaymentEntity;
import org.springframework.stereotype.Component;

/** Domain ↔ JPA entity conversions (ADR-002: Persistence Mapper). */
@Component
public class PaymentPersistenceMapper {

  /**
   * @param payment the domain payment to persist
   * @return its JPA entity shape
   */
  public PaymentEntity toEntity(Payment payment) {
    return new PaymentEntity(
        payment.id().value(),
        payment.orderId().value(),
        payment.method(),
        payment.provider(),
        payment.providerPaymentIntentId(),
        payment.providerReference(),
        payment.amount(),
        payment.status(),
        payment.refundedAmount(),
        payment.authorizedAt(),
        payment.capturedAt(),
        payment.canceledAt(),
        payment.refundedAt(),
        payment.failureReason(),
        payment.refundReason(),
        payment.createdAt(),
        payment.updatedAt());
  }

  /**
   * @param entity the persisted JPA entity
   * @return the rebuilt domain payment ({@link Payment#reconstitute})
   */
  public Payment toDomain(PaymentEntity entity) {
    return Payment.reconstitute(
        PaymentId.of(entity.getId()),
        OrderId.of(entity.getOrderId()),
        entity.getMethod(),
        entity.getProvider(),
        entity.getProviderPaymentIntentId(),
        entity.getProviderReference(),
        entity.getAmount(),
        entity.getStatus(),
        entity.getRefundedAmount(),
        entity.getAuthorizedAt(),
        entity.getCapturedAt(),
        entity.getCanceledAt(),
        entity.getRefundedAt(),
        entity.getFailureReason(),
        entity.getRefundReason(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
```

- [ ] **Step 3: Create `PaymentJpaRepository`**

```java
package com.floristeriarosy.infrastructure.persistence.jpa.payment.repository;

import com.floristeriarosy.infrastructure.persistence.entity.payment.PaymentEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@code payments} (ADR-002). */
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

  /**
   * @param orderId the order to look up
   * @return the order's payment, if one was captured
   */
  Optional<PaymentEntity> findByOrderId(UUID orderId);
}
```

- [ ] **Step 4: Write the failing adapter test**

```java
package com.floristeriarosy.infrastructure.persistence.adapter.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.payment.PaymentProvider;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Round-trips {@link Payment} through Postgres, same pattern as the other adapter tests. */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PaymentPersistenceAdapterTest {

  @Autowired private PaymentPersistenceAdapter adapter;

  @Test
  void savingThenFindingByIdRoundTripsAllFields() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CASH, PaymentProvider.MANUAL,
            new BigDecimal("15.00"));
    payment.capture(null, Instant.now());

    Payment saved = adapter.save(payment);
    Payment found = adapter.findById(saved.id()).orElseThrow();

    assertThat(found.status()).isEqualTo(com.floristeriarosy.domain.model.payment.PaymentStatus.CAPTURED);
    assertThat(found.amount()).isEqualByComparingTo("15.00");
  }

  @Test
  void findByOrderIdLocatesTheCapturedPayment() {
    OrderId orderId = OrderId.newId();
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), orderId, PaymentMethod.CASH, PaymentProvider.MANUAL, new BigDecimal("10.00"));
    payment.capture(null, Instant.now());
    adapter.save(payment);

    assertThat(adapter.findByOrderId(orderId)).isPresent();
  }
}
```

Check an existing test such as `AdminPersistenceAdapterTest` for the exact
Testcontainers/`@SpringBootTest` annotations and base class this project actually uses (the
snippet above is illustrative of intent; match the project's real fixture instead of inventing a
new one).

- [ ] **Step 5: Run test to verify it fails**

Run: `mvn -q test -Dtest=PaymentPersistenceAdapterTest`
Expected: compilation failure — `PaymentPersistenceAdapter` does not exist yet.

- [ ] **Step 6: Implement `PaymentPersistenceAdapter`**

```java
package com.floristeriarosy.infrastructure.persistence.adapter.payment;

import com.floristeriarosy.application.payment.port.out.PaymentReadPort;
import com.floristeriarosy.application.payment.port.out.PaymentWritePort;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import com.floristeriarosy.infrastructure.persistence.entity.payment.PaymentEntity;
import com.floristeriarosy.infrastructure.persistence.jpa.payment.repository.PaymentJpaRepository;
import com.floristeriarosy.infrastructure.persistence.mapper.payment.PaymentPersistenceMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Implements the payment output ports (ADR-003) with plain JPA (ADR-002, payment.md section 8). */
@Repository
public class PaymentPersistenceAdapter implements PaymentReadPort, PaymentWritePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(PaymentPersistenceAdapter.class);

  private final PaymentJpaRepository jpaRepository;
  private final PaymentPersistenceMapper mapper;

  /**
   * @param jpaRepository writes and simple lookups
   * @param mapper converts between the domain {@link Payment} and the JPA {@link PaymentEntity}
   */
  public PaymentPersistenceAdapter(PaymentJpaRepository jpaRepository, PaymentPersistenceMapper mapper) {
    this.jpaRepository = jpaRepository;
    this.mapper = mapper;
  }

  /**
   * @param id the payment to load
   * @return the payment, if it exists
   */
  @Override
  public Optional<Payment> findById(PaymentId id) {
    LOGGER.debug("findById id={}", id);
    Optional<Payment> result = jpaRepository.findById(id.value()).map(mapper::toDomain);
    LOGGER.debug("findById id={} -> found={}", id, result.isPresent());
    return result;
  }

  /**
   * @param orderId the order whose payment to load
   * @return the order's payment, if one was captured
   */
  @Override
  public Optional<Payment> findByOrderId(OrderId orderId) {
    LOGGER.debug("findByOrderId orderId={}", orderId);
    Optional<Payment> result = jpaRepository.findByOrderId(orderId.value()).map(mapper::toDomain);
    LOGGER.debug("findByOrderId orderId={} -> found={}", orderId, result.isPresent());
    return result;
  }

  /**
   * @param payment the payment to insert or update
   * @return the saved payment, with timestamps populated by the database
   */
  @Override
  public Payment save(Payment payment) {
    LOGGER.debug("save id={} status={}", payment.id(), payment.status());
    PaymentEntity entity = jpaRepository.findById(payment.id().value()).orElse(null);
    PaymentEntity toSave = entity != null ? applyChanges(entity, payment) : mapper.toEntity(payment);
    Payment result = mapper.toDomain(jpaRepository.saveAndFlush(toSave));
    LOGGER.debug("save id={} -> saved", result.id());
    return result;
  }

  /**
   * Reuses the managed entity instance rather than a fresh detached one, same reasoning as
   * {@code ProductPersistenceAdapter} — irrelevant here since {@code payments} carries no
   * {@code @Version}, but keeps the write path consistent with the rest of this codebase.
   *
   * @param entity the managed entity loaded from the database
   * @param payment the domain state to apply onto it
   * @return {@code entity}, mutated in place
   */
  private PaymentEntity applyChanges(PaymentEntity entity, Payment payment) {
    return mapper.toEntity(payment);
  }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn -q test -Dtest=PaymentPersistenceAdapterTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/floristeriarosy/infrastructure/persistence/entity/payment \
        src/main/java/com/floristeriarosy/infrastructure/persistence/mapper/payment \
        src/main/java/com/floristeriarosy/infrastructure/persistence/jpa/payment \
        src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/payment \
        src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/payment
git commit -m "feat(payment): Payment JPA persistence (payment.md §8)"
```

---

## Task 8: `ChargeOrderPaymentService`

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/payment/service/ChargeOrderPaymentService.java`
- Test: `src/test/java/com/floristeriarosy/application/payment/service/ChargeOrderPaymentServiceTest.java`

**Interfaces:**
- Consumes: `ChargeOrderPaymentUseCase`, `ChargeOrderPaymentCommand` (Task 3), `PaymentGatewayPort`
  (Task 4), `PaymentMethod`/`PaymentProvider`/`Payment` (Phase 1), `ChargeResult` (Task 2).
- Produces: `ChargeOrderPaymentService implements ChargeOrderPaymentUseCase` — Task 10's
  `OrderPaymentAdapter` calls this.

- [ ] **Step 1: Write the failing tests**

```java
package com.floristeriarosy.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.port.out.ChargeResult;
import com.floristeriarosy.application.payment.command.ChargeOrderPaymentCommand;
import com.floristeriarosy.application.payment.port.out.GatewayChargeRequest;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.application.payment.port.out.RedsysRedirect;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link ChargeOrderPaymentService} branches by {@link PaymentMethod} (payment.md, rule 3.2). */
class ChargeOrderPaymentServiceTest {

  private final PaymentGatewayPort gatewayPort = mock(PaymentGatewayPort.class);
  private final ChargeOrderPaymentService service = new ChargeOrderPaymentService(gatewayPort);

  @Test
  void cardOnlineDelegatesToTheGatewayAndReturnsARedirect() {
    OrderId orderId = OrderId.newId();
    OrderNumber orderNumber = OrderNumber.of("WEB-20260913-000001");
    RedsysRedirect redirect = new RedsysRedirect("https://sis-t.redsys.es/sis/realizarPago", Map.of("k", "v"));
    when(gatewayPort.charge(new GatewayChargeRequest(orderNumber, new BigDecimal("42.00"), "tok_visa", "idem-1")))
        .thenReturn(redirect);

    ChargeResult result =
        service.execute(
            new ChargeOrderPaymentCommand(
                orderId, orderNumber, new BigDecimal("42.00"), PaymentMethod.CARD_ONLINE, "tok_visa", "idem-1"));

    assertThat(result).isInstanceOf(ChargeResult.RedirectRequired.class);
    assertThat(((ChargeResult.RedirectRequired) result).redirectUrl()).isEqualTo(redirect.redirectUrl());
  }

  @Test
  void cashCapturesDirectlyWithoutCallingTheGateway() {
    OrderId orderId = OrderId.newId();
    OrderNumber orderNumber = OrderNumber.of("STORE-20260913-000001");

    ChargeResult result =
        service.execute(
            new ChargeOrderPaymentCommand(
                orderId, orderNumber, new BigDecimal("15.00"), PaymentMethod.CASH, null, "idem-2"));

    assertThat(result).isInstanceOf(ChargeResult.Captured.class);
    assertThat(((ChargeResult.Captured) result).payment().status())
        .isEqualTo(com.floristeriarosy.domain.model.payment.PaymentStatus.CAPTURED);
    org.mockito.Mockito.verifyNoInteractions(gatewayPort);
  }

  @Test
  void interfloraCapturesDirectlyWithoutCallingTheGateway() {
    ChargeResult result =
        service.execute(
            new ChargeOrderPaymentCommand(
                OrderId.newId(), OrderNumber.of("INTERFLORA-20260913-000001"), new BigDecimal("30.00"),
                PaymentMethod.INTERFLORA, null, "idem-3"));

    assertThat(result).isInstanceOf(ChargeResult.Captured.class);
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=ChargeOrderPaymentServiceTest`
Expected: compilation failure — `ChargeOrderPaymentService` does not exist yet.

- [ ] **Step 3: Implement `ChargeOrderPaymentService`**

```java
package com.floristeriarosy.application.payment.service;

import com.floristeriarosy.application.order.port.out.ChargeResult;
import com.floristeriarosy.application.payment.command.ChargeOrderPaymentCommand;
import com.floristeriarosy.application.payment.port.in.ChargeOrderPaymentUseCase;
import com.floristeriarosy.application.payment.port.out.GatewayChargeRequest;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.application.payment.port.out.RedsysRedirect;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentProvider;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link ChargeOrderPaymentUseCase} (payment.md, rule 3.2): a single method that
 * branches by {@code method} — {@code CARD_ONLINE} defers to Redsys via a redirect, everything
 * else captures directly, no external call. Writes nothing to {@code payments} itself; the
 * returned {@link ChargeResult.Captured} payment is unsaved — the caller persists it inside its
 * own transaction (Fase 1+3a fused for counter sales, payment.md rule 3.6).
 */
@Service
public class ChargeOrderPaymentService implements ChargeOrderPaymentUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChargeOrderPaymentService.class);

  private final PaymentGatewayPort gatewayPort;

  /**
   * @param gatewayPort the Redsys frontier, used only for {@code CARD_ONLINE}
   */
  public ChargeOrderPaymentService(PaymentGatewayPort gatewayPort) {
    this.gatewayPort = gatewayPort;
  }

  /**
   * @param command what to charge and how
   * @return a redirect to complete payment ({@code CARD_ONLINE}), or an already-captured, unsaved
   *     payment (every other method)
   */
  @Override
  public ChargeResult execute(ChargeOrderPaymentCommand command) {
    LOGGER.debug(
        "chargeOrderPayment orderId={} method={} amount={}", command.orderId(), command.method(),
        command.amount());
    ChargeResult result =
        switch (command.method()) {
          case CARD_ONLINE -> chargeViaRedsys(command);
          case CASH, DATAPHONE, INTERFLORA -> captureDirectly(command);
        };
    LOGGER.debug("chargeOrderPayment orderId={} -> {}", command.orderId(), result.getClass().getSimpleName());
    return result;
  }

  /**
   * @param command the charge to build a redirect for
   * @return the redirect the customer must be sent to
   */
  private ChargeResult chargeViaRedsys(ChargeOrderPaymentCommand command) {
    RedsysRedirect redirect =
        gatewayPort.charge(
            new GatewayChargeRequest(
                command.orderNumber(), command.amount(), command.paymentMethodToken(), command.idempotencyKey()));
    return new ChargeResult.RedirectRequired(redirect.redirectUrl(), redirect.formFields());
  }

  /**
   * @param command the charge to capture directly, no gateway involved
   * @return the already-captured, unsaved payment
   */
  private ChargeResult captureDirectly(ChargeOrderPaymentCommand command) {
    Payment payment =
        Payment.initiate(PaymentId.newId(), command.orderId(), command.method(), PaymentProvider.MANUAL, command.amount());
    payment.capture(null, Instant.now());
    return new ChargeResult.Captured(payment);
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=ChargeOrderPaymentServiceTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/floristeriarosy/application/payment/service/ChargeOrderPaymentService.java \
        src/test/java/com/floristeriarosy/application/payment/service/ChargeOrderPaymentServiceTest.java
git commit -m "feat(payment): ChargeOrderPaymentService (payment.md §3.2)"
```

---

## Task 9: `RefundOrderPaymentService`

**Files:**
- Create: `src/main/java/com/floristeriarosy/application/payment/service/RefundOrderPaymentService.java`
- Test: `src/test/java/com/floristeriarosy/application/payment/service/RefundOrderPaymentServiceTest.java`

**Interfaces:**
- Consumes: `RefundOrderPaymentUseCase`, `RefundOrderPaymentCommand` (Task 3), `PaymentReadPort`/
  `PaymentWritePort` (Task 3), `PaymentGatewayPort` (Task 4).
- Produces: `RefundOrderPaymentService implements RefundOrderPaymentUseCase` — Task 10's
  `OrderPaymentAdapter` calls this.

- [ ] **Step 1: Write the failing tests**

```java
package com.floristeriarosy.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.payment.command.RefundOrderPaymentCommand;
import com.floristeriarosy.application.payment.port.out.GatewayRefundResult;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.application.payment.port.out.PaymentReadPort;
import com.floristeriarosy.application.payment.port.out.PaymentWritePort;
import com.floristeriarosy.domain.exception.payment.PaymentNotFoundException;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.payment.PaymentProvider;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@link RefundOrderPaymentService} — always a full, automatic refund (payment.md, rule 3.3). */
class RefundOrderPaymentServiceTest {

  private final PaymentReadPort readPort = mock(PaymentReadPort.class);
  private final PaymentWritePort writePort = mock(PaymentWritePort.class);
  private final PaymentGatewayPort gatewayPort = mock(PaymentGatewayPort.class);
  private final RefundOrderPaymentService service = new RefundOrderPaymentService(readPort, writePort, gatewayPort);

  @Test
  void cardOnlineCallsTheGatewayThenSavesTheRefund() {
    OrderId orderId = OrderId.newId();
    Payment captured =
        Payment.initiate(PaymentId.newId(), orderId, PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS, new BigDecimal("42.00"));
    captured.capture("redsys-auth-1", Instant.now());
    when(readPort.findByOrderId(orderId)).thenReturn(Optional.of(captured));
    when(gatewayPort.refund(any())).thenReturn(new GatewayRefundResult(true, "redsys-refund-1", null));
    when(writePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Payment result = service.execute(new RefundOrderPaymentCommand(orderId));

    assertThat(result.refundedAmount()).isEqualByComparingTo("42.00");
    assertThat(result.providerReference()).isEqualTo("redsys-refund-1");
  }

  @Test
  void cashSettlesDirectlyWithoutCallingTheGateway() {
    OrderId orderId = OrderId.newId();
    Payment captured =
        Payment.initiate(PaymentId.newId(), orderId, PaymentMethod.CASH, PaymentProvider.MANUAL, new BigDecimal("15.00"));
    captured.capture(null, Instant.now());
    when(readPort.findByOrderId(orderId)).thenReturn(Optional.of(captured));
    when(writePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Payment result = service.execute(new RefundOrderPaymentCommand(orderId));

    assertThat(result.refundedAmount()).isEqualByComparingTo("15.00");
    verifyNoInteractions(gatewayPort);
  }

  @Test
  void throwsWhenTheOrderHasNoCapturedPayment() {
    OrderId orderId = OrderId.newId();
    when(readPort.findByOrderId(orderId)).thenReturn(Optional.empty());

    org.assertj.core.api.Assertions.assertThatExceptionOfType(PaymentNotFoundException.class)
        .isThrownBy(() -> service.execute(new RefundOrderPaymentCommand(orderId)));
  }
}
```

This is the first payment error condition in the codebase, so it introduces `PaymentErrorCode` and
`PaymentNotFoundException` now — the deferral rule from Phase 1 ("no error code without a real
thrower") is satisfied, not violated: this task *is* that first thrower.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=RefundOrderPaymentServiceTest`
Expected: compilation failure — `RefundOrderPaymentService`, `PaymentErrorCode`,
`PaymentNotFoundException` do not exist yet.

- [ ] **Step 3: Create `PaymentErrorCode`**

```java
package com.floristeriarosy.domain.exception.payment;

/** Business error codes published by the payment module (ADR-012, payment.md §9). */
public enum PaymentErrorCode {
  PAYMENT_NOT_FOUND
}
```

- [ ] **Step 4: Create `PaymentNotFoundException`**

```java
package com.floristeriarosy.domain.exception.payment;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/** The order has no payment associated (payment.md, §9). */
public final class PaymentNotFoundException extends NotFoundException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public PaymentNotFoundException(String message) {
    super(message);
  }

  /**
   * @return {@link PaymentErrorCode#PAYMENT_NOT_FOUND}
   */
  @Override
  public String errorCode() {
    return PaymentErrorCode.PAYMENT_NOT_FOUND.name();
  }
}
```

Check `domain/exception/NotFoundException.java`'s exact constructor shape (a `protected` ctor
taking a `String message`, per Phase 1's research on `CartInsufficientStockException`) and match it
— do not guess a different shape.

- [ ] **Step 5: Implement `RefundOrderPaymentService`**

```java
package com.floristeriarosy.application.payment.service;

import com.floristeriarosy.application.payment.command.RefundOrderPaymentCommand;
import com.floristeriarosy.application.payment.port.in.RefundOrderPaymentUseCase;
import com.floristeriarosy.application.payment.port.out.GatewayRefundRequest;
import com.floristeriarosy.application.payment.port.out.GatewayRefundResult;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.application.payment.port.out.PaymentReadPort;
import com.floristeriarosy.application.payment.port.out.PaymentWritePort;
import com.floristeriarosy.domain.exception.payment.PaymentNotFoundException;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link RefundOrderPaymentUseCase} (payment.md, rule 3.3): always a full, automatic
 * refund — no decision to make, no partial amount. {@code CARD_ONLINE} calls Redsys synchronously
 * (a refund has no redirect step, unlike a charge, ADR-017); every other method settles directly.
 */
@Service
@Transactional
public class RefundOrderPaymentService implements RefundOrderPaymentUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(RefundOrderPaymentService.class);

  private final PaymentReadPort readPort;
  private final PaymentWritePort writePort;
  private final PaymentGatewayPort gatewayPort;

  /**
   * @param readPort locates the order's captured payment
   * @param writePort persists the refund
   * @param gatewayPort the Redsys frontier, used only for {@code CARD_ONLINE}
   */
  public RefundOrderPaymentService(PaymentReadPort readPort, PaymentWritePort writePort, PaymentGatewayPort gatewayPort) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.gatewayPort = gatewayPort;
  }

  /**
   * @param command which order's payment to refund
   * @return the refunded, saved payment
   * @throws PaymentNotFoundException the order has no payment associated
   */
  @Override
  public Payment execute(RefundOrderPaymentCommand command) {
    LOGGER.debug("refundOrderPayment orderId={}", command.orderId());
    Payment payment =
        readPort
            .findByOrderId(command.orderId())
            .orElseThrow(() -> new PaymentNotFoundException("Order " + command.orderId() + " has no payment"));
    String providerReference = payment.method() == PaymentMethod.CARD_ONLINE ? refundViaRedsys(payment) : null;
    payment.refundInFull(providerReference, Instant.now());
    Payment result = writePort.save(payment);
    LOGGER.debug("refundOrderPayment orderId={} -> refunded", command.orderId());
    return result;
  }

  /**
   * @param payment the captured {@code CARD_ONLINE} payment to refund
   * @return the gateway's refund reference
   */
  private String refundViaRedsys(Payment payment) {
    GatewayRefundResult result =
        gatewayPort.refund(
            new GatewayRefundRequest(
                payment.orderId().value().toString(), payment.providerPaymentIntentId(), payment.amount()));
    if (!result.success()) {
      throw new IllegalStateException("Redsys refused the refund: " + result.failureReason());
    }
    return result.providerReference();
  }
}
```

Note the `refundViaRedsys` call passes `payment.orderId().value().toString()` as a stand-in for the
order number: `Payment` does not carry `OrderNumber`, only `OrderId` — check whether
`PaymentReadPort`/the persisted row can resolve the real order number (needed to diversify the
Redsys signature correctly, ADR-017), e.g. by also injecting `OrderReadPort` here once it exists
(Phase 3), or by having `GatewayRefundRequest` accept `OrderId` and let the adapter be told about it
differently. **Flag this as an open point for the implementer to resolve** rather than silently
shipping a wrong signature input — the UUID-as-string is not the actual order number Redsys expects
and this must be fixed before this task is considered done; do not treat the code above as
authoritative on this one detail.

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -q test -Dtest=RefundOrderPaymentServiceTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/floristeriarosy/domain/exception/payment \
        src/main/java/com/floristeriarosy/application/payment/service/RefundOrderPaymentService.java \
        src/test/java/com/floristeriarosy/application/payment/service/RefundOrderPaymentServiceTest.java
git commit -m "feat(payment): RefundOrderPaymentService, PaymentErrorCode (payment.md §3.3, §9)"
```

---

## Task 10: `OrderPaymentAdapter`

**Files:**
- Create: `src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderPaymentAdapter.java`
- Test: `src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderPaymentAdapterTest.java`

**Interfaces:**
- Consumes: `OrderPaymentPort`, `ChargeCommand`, `RefundCommand` (Task 2), `ChargeOrderPaymentUseCase`,
  `RefundOrderPaymentUseCase`, `ChargeOrderPaymentCommand`, `RefundOrderPaymentCommand` (Task 3).
- Produces: `OrderPaymentAdapter implements OrderPaymentPort` — this is the bean Phase 3's
  `CheckoutService`/`ConfirmCheckoutService` and Phase 4's `RejectOrderService`/`CancelOrderService`
  get injected with when they ask for `OrderPaymentPort`.

Mirrors `ProductInventoryPersistenceAdapter`: a thin translation between `order`'s command/result
shapes and `payment`'s, with zero business logic of its own — same reasoning as
`ProductInventoryPersistenceAdapter`'s own Javadoc ("`product` no escribe en `stock_movements` por
su cuenta"), applied to money instead of stock.

- [ ] **Step 1: Write the failing test**

```java
package com.floristeriarosy.infrastructure.persistence.adapter.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.port.out.ChargeCommand;
import com.floristeriarosy.application.order.port.out.ChargeResult;
import com.floristeriarosy.application.order.port.out.RefundCommand;
import com.floristeriarosy.application.payment.port.in.ChargeOrderPaymentUseCase;
import com.floristeriarosy.application.payment.port.in.RefundOrderPaymentUseCase;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.payment.PaymentProvider;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** {@link OrderPaymentAdapter} translates order's port into payment's use cases, nothing else. */
class OrderPaymentAdapterTest {

  private final ChargeOrderPaymentUseCase chargeUseCase = mock(ChargeOrderPaymentUseCase.class);
  private final RefundOrderPaymentUseCase refundUseCase = mock(RefundOrderPaymentUseCase.class);
  private final OrderPaymentAdapter adapter = new OrderPaymentAdapter(chargeUseCase, refundUseCase);

  @Test
  void chargeDelegatesToChargeOrderPaymentUseCase() {
    OrderId orderId = OrderId.newId();
    OrderNumber orderNumber = OrderNumber.of("WEB-20260913-000001");
    Payment captured =
        Payment.initiate(PaymentId.newId(), orderId, PaymentMethod.CASH, PaymentProvider.MANUAL, new BigDecimal("10.00"));
    captured.capture(null, Instant.now());
    when(chargeUseCase.execute(any())).thenReturn(new ChargeResult.Captured(captured));

    ChargeResult result =
        adapter.charge(new ChargeCommand(orderId, orderNumber, new BigDecimal("10.00"), PaymentMethod.CASH, null, "idem-1"));

    assertThat(result).isInstanceOf(ChargeResult.Captured.class);
  }

  @Test
  void refundDelegatesToRefundOrderPaymentUseCase() {
    OrderId orderId = OrderId.newId();
    Payment refunded =
        Payment.initiate(PaymentId.newId(), orderId, PaymentMethod.CASH, PaymentProvider.MANUAL, new BigDecimal("10.00"));
    refunded.capture(null, Instant.now());
    refunded.refundInFull(null, Instant.now());
    when(refundUseCase.execute(any())).thenReturn(refunded);

    Payment result = adapter.refund(new RefundCommand(orderId));

    assertThat(result.refundedAmount()).isEqualByComparingTo("10.00");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OrderPaymentAdapterTest`
Expected: compilation failure — `OrderPaymentAdapter` does not exist yet.

- [ ] **Step 3: Implement `OrderPaymentAdapter`**

```java
package com.floristeriarosy.infrastructure.persistence.adapter.order;

import com.floristeriarosy.application.order.port.out.ChargeCommand;
import com.floristeriarosy.application.order.port.out.ChargeResult;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.application.order.port.out.RefundCommand;
import com.floristeriarosy.application.payment.command.ChargeOrderPaymentCommand;
import com.floristeriarosy.application.payment.command.RefundOrderPaymentCommand;
import com.floristeriarosy.application.payment.port.in.ChargeOrderPaymentUseCase;
import com.floristeriarosy.application.payment.port.in.RefundOrderPaymentUseCase;
import com.floristeriarosy.domain.model.payment.Payment;
import org.springframework.stereotype.Component;

/**
 * Implements {@link OrderPaymentPort} (ADR-003) by delegating to {@code payment}'s own use cases —
 * same pattern as {@code ProductInventoryPersistenceAdapter} delegating to {@code inventory}'s
 * {@code RegisterStockMovementUseCase}. Carries no logic of its own beyond translating between
 * {@code order}'s command/result shapes and {@code payment}'s.
 */
@Component
public class OrderPaymentAdapter implements OrderPaymentPort {

  private final ChargeOrderPaymentUseCase chargeUseCase;
  private final RefundOrderPaymentUseCase refundUseCase;

  /**
   * @param chargeUseCase payment's real charging logic
   * @param refundUseCase payment's real refunding logic
   */
  public OrderPaymentAdapter(ChargeOrderPaymentUseCase chargeUseCase, RefundOrderPaymentUseCase refundUseCase) {
    this.chargeUseCase = chargeUseCase;
    this.refundUseCase = refundUseCase;
  }

  /**
   * @param command what to charge and how
   * @return a redirect to complete payment, or an already-settled charge
   */
  @Override
  public ChargeResult charge(ChargeCommand command) {
    return chargeUseCase.execute(
        new ChargeOrderPaymentCommand(
            command.orderId(), command.orderNumber(), command.amount(), command.method(),
            command.paymentMethodToken(), command.idempotencyKey()));
  }

  /**
   * @param command which order's payment to refund
   * @return the refunded payment
   */
  @Override
  public Payment refund(RefundCommand command) {
    return refundUseCase.execute(new RefundOrderPaymentCommand(command.orderId()));
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OrderPaymentAdapterTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full build**

Run: `mvn -q verify`
Expected: `BUILD SUCCESS` — Checkstyle, all tests (including ArchUnit — `order` must still not
depend on `payment`'s internals, only on `OrderPaymentPort`/`ChargeCommand`/`ChargeResult`/
`RefundCommand`, all of which live in `order`'s own package), and SpotBugs all green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderPaymentAdapter.java \
        src/test/java/com/floristeriarosy/infrastructure/persistence/adapter/order/OrderPaymentAdapterTest.java
git commit -m "feat(order): OrderPaymentAdapter bridges OrderPaymentPort to payment (ADR-003)"
```

---

## Self-Review

**Spec coverage:** `payment.md` §3.1-3.2 (branch by method) → Tasks 1, 8. §3.3 (automatic full
refund) → Tasks 1, 9. §7 (use cases) → Tasks 3, 8, 9. §8 (ports) → Tasks 2, 3, 4, 7. §9 (first error
code) → Task 9. `order.md` §8 (`OrderPaymentPort`) → Tasks 2, 10. ADR-017 (signature, redirect
model, `Ds_Merchant_MerchantData` correlation) → Tasks 4, 5, 6.

**Deliberately not covered here:** the `POST /api/v1/payments/redsys/notification` controller and
`ReceiveRedsysNotificationUseCase` (needs `ConfirmCheckoutUseCase` from `order`, which is Phase 3);
saved cards / `attachPaymentMethod`/`detachPaymentMethod` (Phase 6, per `payment.md` §3.5 and the
original phase plan); `ManualRefundUseCase`'s partial-refund logic (Phase 6 — deliberately a
different method name than this phase's `refundInFull`, see Task 1).

**Open point the implementer must resolve, not silently paper over:** Task 9's
`refundViaRedsys` needs the order's `OrderNumber`, not just its `OrderId` — `Payment` does not carry
`OrderNumber`. Resolve by either giving `PaymentReadPort` a way to return it (a join, or storing it
denormalized on `payments` — check whether the schema already has anywhere to put it before adding
a column) or by having the caller (`OrderPaymentAdapter`, which does have `OrderNumber` from
`RefundCommand` if that record is extended to carry it) pass it through instead. Pick one, document
the choice, do not ship the UUID-string placeholder from Task 9's draft code.

**Placeholder scan:** clean except the one flagged open point above, called out explicitly rather
than hidden.

**Type consistency:** `OrderPaymentPort`, `ChargeCommand`, `ChargeResult`, `RefundCommand` (Task 2)
match `OrderPaymentAdapter`'s usage (Task 10) exactly. `ChargeOrderPaymentUseCase`/
`RefundOrderPaymentUseCase`/their commands (Task 3) match `ChargeOrderPaymentService`/
`RefundOrderPaymentService` (Tasks 8-9) and `OrderPaymentAdapter` (Task 10) exactly.
`PaymentGatewayPort`/`GatewayChargeRequest`/`RedsysRedirect`/`GatewayRefundRequest`/
`GatewayRefundResult` (Task 4) match `RedsysPaymentGatewayAdapter` (Task 6) and
`ChargeOrderPaymentService`/`RefundOrderPaymentService` (Tasks 8-9) exactly.

---

Plan complete and saved to `docs/superpowers/plans/2026-09-13-order-payment-phase2-gateway.md`.
Same three execution options as Phase 1: subagent-driven, inline, or hand off to IMPLEMENTER (with
REVIEWER checking after) — which approach?
