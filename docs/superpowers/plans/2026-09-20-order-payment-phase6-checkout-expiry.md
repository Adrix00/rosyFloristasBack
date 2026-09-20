# Phase 6 — Abandoned-checkout expiry (ADR-017, scheduled-tasks.md §3.3)

Branch: `feature/orderPayments`, after Phase 5 and the cart retry fix. Problem: a customer who leaves
Redsys without paying keeps their Fase 1 reservation (stock `SALE`, discount units) forever, because
nothing resolves the `PENDING` `idempotency_keys` row. Spec: a `PENDING` row of `POST /checkout` older
than 10 minutes (`expires_at`, already written at insert) is abandoned: release the reservation, mark
the row `FAILED`, clear `checkout_payload_encrypted`.

## A hole the spec does not close (decision included here)

ADR-017 says a notification arriving after expiry "answers with the already-settled outcome". That is
right for a late **decline**. For a late **approval** (customer paid at minute 11) it is a money bug:
the reservation is released, no order exists, and Redsys already charged the card. Decision (ARCHITECT):
a signature-verified **approved** notification that finds the row `FAILED` (expired) triggers an
automatic full refund at the gateway using the data in the notification itself (the 12-digit `Ds_Order`
and `Ds_Amount`; there is no `payments` row because no order was created), and logs at ERROR with the
idempotency key and the 12-digit order id (no PII) so an operator sees it. If that refund also fails,
the ERROR log is the alert; do not throw (Redsys must still get its 200 so it stops retrying).
Amend ADR-017's "Reservation expiry" paragraph accordingly. A late approval for a row that is
`COMPLETED` (normal Redsys retry) stays a plain 200 no-op.

## Tasks (11-step workflow inside each; commits per task)

1. **Extract the release logic.** `ConfirmCheckoutService.compensate()` (releaseStock: `ADJUSTMENT`
   with `InventoryNotManagedException` swallowed, releaseDiscount) now has a second caller. Move it
   to a plain `@Component` `CheckoutReservationReleaser` in `application.order.support` (same package
   as `OrderReversalComponent`, same reasoning: not a use case, no port). `ConfirmCheckoutService`
   uses it; behavior unchanged, its tests stay green.
2. **Row selection and claim.** Extend `CheckoutIdempotencyPort` (JDBC per ADR-002, it is a filtered
   query): `findExpiredPending(Instant now, int limit)` returning keys of `POST /checkout` rows with
   `status = 'PENDING' AND expires_at < now`, oldest first, `FOR UPDATE SKIP LOCKED` inside the caller's
   transaction; and make the read used by `ConfirmCheckoutService` take the row lock
   (`SELECT ... FOR UPDATE`), so a notification and the cleanup can never both resolve the same row:
   whichever gets the lock first wins, the other sees the resolved state. Verify `markFailed` already
   clears `checkout_payload_encrypted`; if not, make it do so.
3. **The use case.** `ReleaseExpiredCheckoutsUseCase` / `ReleaseExpiredCheckoutsService` (no
   controller, no `@PreAuthorize`, it is internal like `RegisterStockMovementUseCase`). One transaction
   PER row (a failing row must not block or roll back the others; use a `TransactionTemplate`, do NOT
   rely on a self-invoked `@Transactional` method, that is the proxy pitfall this codebase already hit).
   Per row: lock, re-check it is still `PENDING` and expired, decrypt the payload, call the releaser,
   `markFailed`. A row whose payload cannot be decrypted or parsed: log ERROR with the key, mark it
   `FAILED` without releasing (nothing safe to release) so it does not retry forever. Process at most a
   fixed batch per run (e.g. 100), log the count released.
4. **The scheduler.** Same pattern as `infrastructure/scheduler/InventoryAlertScheduler` (scheduling is
   already enabled in `AppApplication`): a fixed delay of 60 s calling the use case. Copy that class's
   structure rather than inventing one; a scheduled call must never throw out of the scheduler thread.
5. **Late approval handling.** In the notification path (`ReceiveRedsysNotificationService` /
   `ConfirmCheckoutService`, wherever the `FAILED`/missing-lock branch is today): approved + row
   `FAILED` -> refund through `PaymentGatewayPort` using `RedsysOrderId`-style 12-digit `Ds_Order` and
   `Ds_Amount` from the verified notification, log ERROR, answer 200. Check the current
   `PaymentGatewayPort.refund` signature; if it needs an `OrderNumber` it cannot have here, add the
   smallest overload or a dedicated method and say so. Declined + `FAILED` and approved + `COMPLETED`
   stay plain no-ops.
6. **Tests.** (a) Unit tests with mocks for the service and the releaser. (b) Real-DB
   Testcontainers test, transactional services, no persistence mocks: seed one expired and one
   non-expired `PENDING` row with a real encrypted payload (managed-stock line, a discounted line, and
   an unmanaged-stock line, since that exact case broke Phase 4); run the use case; assert stock and
   `stock_movements` (`ADJUSTMENT` reversing the sale), discount `quantity_sold`, the expired row
   `FAILED` with null payload, the other row untouched. (c) A test that a notification approved after
   expiry calls the gateway refund once and does not create an order; and that running the cleanup twice
   releases only once. (d) Lock ordering: a notification confirming a row and the cleanup racing must
   end with exactly one outcome; if a deterministic test of the race is not practical, at least test
   both sequential orders.
7. **Docs.** After it works: update `scheduled-tasks.md` §3.3 (implemented, batch size, frequency) and
   the ADR-017 paragraph above (ARCHITECT will do the two docs; IMPLEMENTER only reports the batch size
   and delay chosen).

## Out of scope

Cleanup of the other tables in §3.3 (`refresh_tokens`, `verification_tokens`, other
`idempotency_keys` endpoints, `notifications`, `carts`): undesigned, separate decision. No new
configuration property for the 10 minutes (already fixed at insert by `CheckoutService`).

## Done when

`mvn clean verify` green with Docker up and no network, every new class logged/Javadoc'd per CLAUDE.md,
REVIEWER's pass clean.
