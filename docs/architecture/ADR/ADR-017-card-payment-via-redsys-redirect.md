# ADR-017: Card Payment via Redirect (Redsys TPV Virtual)

- **Status:** Accepted
- **Date:** 2026-09-13
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

`payment.md` was originally written against a synchronous card gateway (Stripe or equivalent): the
backend calls the pasarela inside the checkout request and gets an immediate approve/decline, with
no webhook surface at all (`payment.md` §1, §11 explicitly discard webhooks as unneeded). The
merchant's actual contracted channel is different: Redsys TPV Virtual under Santander/GetNet
branding (`docs/pasarelaGetNet/GetNet.md`, kept local-only, not versioned — see `.gitignore`) — the
classic Spanish redirect model. The customer is sent to Redsys's hosted payment page, authenticates
there (3DS), and Redsys confirms the result **asynchronously**, via a server-to-server notification
to a URL the merchant exposes, independently of whether the customer's browser ever returns. The
synchronous, webhook-free assumption in `order.md` §3.2/§3.3 and `payment.md` §1/§3.2/§3.3/§11 does
not hold for this gateway.

Extending the same TPV contract already used for the physical terminal (`DATAPHONE`) to the online
channel is the lowest-friction, lowest-cost path for the business — a separate product such as
Global Getnet's own REST API would mean a new commercial relationship the business does not have.
This ADR designs around the Redsys TPV Virtual contract as given, not a hypothetical alternative
gateway.

---

# Decision

## Checkout splits into an initial reservation step and an asynchronous resolution

`POST /checkout` now executes only Fase 1 (reserve stock/discount, `order.md` §3.2) in its own
transaction and, on success, computes and signs the Redsys redirect parameters
(`Ds_MerchantParameters`, `Ds_Signature`, `Ds_SignatureVersion`). It returns those to the frontend —
not an `OrderResponse` — for an auto-submitting redirect. **It does not create a row in `orders`**,
preserving `order.md` §1's invariant that an order which is never paid leaves no trace in `orders`.

A new public endpoint, `POST /api/v1/payments/redsys/notification`, receives Redsys's asynchronous
result. This — not the browser's return to `Ds_Merchant_UrlOK`/`Ds_Merchant_UrlKO` — is what actually
executes Fase 3a (approved) or Fase 3b (declined). The browser redirect is UX only: it lands the
customer back on the site, which then polls checkout status (below). It must never be trusted to
know the outcome, since the customer can close the tab before returning.

## Holding the reservation: `idempotency_keys`, not a premature `orders` row

Fase 1 already inserts the `idempotency_keys` row for `/checkout` per ADR-011. This ADR adds one
encrypted column, `checkout_payload_encrypted` (`idempotency_keys`, migration `V18`), holding exactly
what Fase 3a will need to materialize the order: priced/snapshotted line items (product id, name,
attributes, unit price, discount price, discount id, quantity), buyer data (guest form or profile
snapshot), delivery/pickup data, and the chosen payment method. It is encrypted with the same
`PiiCryptoPort` (ADR-005) as `orders`/`order_deliveries` use, and only exists for the lifetime of the
pending checkout — cleared the moment the row leaves `PENDING` (Fase 3a or 3b), never a second
indefinite copy.

The order's id and `order_number` (from `order_number_seq`, `order.md` §3.1) are minted at Fase 1
time and stored as `idempotency_keys.resource_id` immediately. `Ds_Merchant_Order` sent to Redsys is
**not** the business order number: Redsys accepts at most 12 positions (longer returns `SIS0014`,
unless the terminal enables extended numbers) with a numeric prefix, and `WEB-20260826-000001` is
neither. It is the number's sequence part zero-padded to 12 digits (`000000000001`), derived by
`RedsysOrderId.from(OrderNumber)` in `infrastructure/payment/redsys`, with no new column: the sequence
is globally unique and never reset, so uniqueness is inherited. The same value feeds the per-order
3DES key diversification (`RedsysSignature`) for charge, refund and notification verification. The
business order number travels back in the encrypted Fase-1 payload (`PendingCheckoutPayload.orderNumber`),
never in `Ds_Order`. `Ds_Merchant_MerchantData` carries the `Idempotency-Key` value itself, so the
notification handler finds its row directly, with no lookup table of its own; a notification with no
`PENDING` row for that key is a no-op if it is a decline or the row is `COMPLETED`, and a late payment
(refund plus ERROR log, see "Reservation expiry") if it is an approval. Notifications may arrive with URL-safe Base64 (`-_`) in
`Ds_MerchantParameters`/`Ds_Signature`; both alphabets are accepted, signature verification stays
constant-time and first. A `resource_id`
reserved before the resource exists is new *usage* of that column, not a new column — the row moves
from "reserved" (`PENDING`, no `orders` row yet) to "fulfilled" (`COMPLETED`, `orders.id =
resource_id` now exists) exactly like every other idempotent operation in this codebase, just with a
longer gap in the middle.

Consequence: `order_number_seq` gains a gap for every abandoned checkout. Already tolerated —
`order.md` §3.1 states the sequence never resets and the date in the number is informational only.

## Reservation expiry: 10 minutes, no notification

If no notification arrives within **10 minutes** of the `idempotency_keys` row being created for a
`/checkout` entry, the cleanup task designed in `scheduled-tasks.md` §3.3 (previously left open for
this table) treats it as abandoned: releases the Fase 1 reservation (`ADJUSTMENT` reversing the
`SALE`, `DiscountReservationPort.release()`), marks the row `FAILED`, and clears
`checkout_payload_encrypted`. Ten minutes is deliberately short — comfortably longer than a real 3DS
challenge (typically seconds) and short enough that an abandoned reservation does not hold stock
hostage for long. This is a business tuning knob, not a security boundary; changing it later needs no
schema change.

A notification that arrives after the row already expired and was released depends on its outcome. A late
**decline**, or any notification for a row already `COMPLETED`, is a plain no-op answered with `200 OK`
(the row is already settled). A late **approval** is different: the reservation is gone, no order exists
and the card was charged. The handler then refunds the charge at the gateway using the 12-digit
`Ds_Order` and `Ds_Amount` of the verified notification (there is no `payments` row to refund through),
logs at ERROR with the idempotency key and the 12-digit id (no personal data), never throws, and still
answers `200 OK` so Redsys stops retrying. If that refund fails, the ERROR log is the alert. The same
holds for an approval whose row no longer exists at all (purged), and for one whose `Ds_Order` belongs to a
previous attempt of the same key. The same also holds for an approval that follows a **decline** of the same order (a customer retrying the card on
the payment page after a denial): the decline already released the reservation and left the row `FAILED`,
so a later approval for that `Ds_Order` is refunded rather than turned into an order. Known edge, accepted:
refunding is the safe outcome (no order without reserved stock, no charge kept), at the cost of a customer
seeing a charge that is reversed; whether Redsys allows that retry at all on the same order is not verified
against the docs. Holding the reservation until expiry after a decline was rejected because it keeps stock
locked and leaves the status endpoint saying `PENDING` for up to 10 minutes after a failed payment. To close the
race between the cleanup and a notification, both take a row lock on the `idempotency_keys` row
(`FOR UPDATE`; the cleanup uses `SKIP LOCKED` and lets the notification win), so exactly one of them
resolves a row. The cleanup runs every 60 s after the previous run ends, at most 100 rows per run, one
transaction per row.

Forward constraint: "an approved notification with no idempotency row is refunded" is correct only while
`POST /checkout` is the sole flow that receives Redsys notifications. Any future flow that also produces
notifications (payment link, saved-card charge, Bizum, an admin-initiated charge) must mark its operations,
for example with a `Ds_Merchant_MerchantData` prefix, and the handler must branch on that marker before this
refund rule, or it would refund legitimate payments of that other flow.

## Notification endpoint: verify, then idempotent by construction

`POST /api/v1/payments/redsys/notification` is unauthenticated (Redsys is not a logged-in user) but
not unverified: it recomputes `Ds_Signature` from the merchant's SHA-256 key over the received
`Ds_MerchantParameters` and rejects anything that does not match, before touching any state. Redsys
retries the notification until it receives `HTTP 200` with body `"OK"` — so the handler must answer
`200 OK` for a state it has already resolved (a `COMPLETED` or `FAILED` `idempotency_keys` row for
that key) rather than reprocess it. This is the same `PENDING`→`COMPLETED`/`FAILED` lock ADR-011
already defines; this endpoint is simply the second possible caller of that transition (the first
being a synchronous gateway callback, which this endpoint replaces for `CARD_ONLINE`).

## Client-side resolution after the redirect back

The browser returning to `Ds_Merchant_UrlOK`/`Ds_Merchant_UrlKO` cannot itself carry the outcome —
the notification may not have landed yet. `GET /checkout/{idempotencyKey}/status` (new, public, keyed
by the same `Idempotency-Key` header value sent on the original `POST /checkout`) answers `PENDING`
(frontend keeps polling), `COMPLETED` (with the `orderNumber`, to route to `GET
/orders/{orderNumber}`), or `FAILED` (with the reason, if any). This is a thin read over the same
`idempotency_keys` row — no new use case beyond an idempotency-status lookup, described in `order.md`
§4/§7.

## `provider = 'REDSYS'`

`payments.provider` (`payment.md` §2) has no `CHECK` constraint (free `VARCHAR(30)`) — `'REDSYS'`
needs no migration. `PaymentGatewayPort` (`payment.md` §8) is implemented by a Redsys adapter instead
of a Stripe one; its shape (`charge`, `refund`, `attachPaymentMethod`, `detachPaymentMethod`) does not
change, only what happens inside `charge` for `CARD_ONLINE`, which now produces "redirect required"
data instead of a settled result. `attachPaymentMethod` (saved cards, `payment.md` §3.5) uses
Redsys's own "Pago por Referencia" (OneClick) token — the two-step, setup-token-then-metadata flow
`payment.md` §3.5 already describes maps onto it directly, since it was written against a generic
token/reference abstraction, not literally Stripe's API shape.

---

# Alternatives considered

**Pre-create the `orders` row in a new `AWAITING_PAYMENT` status, delete it on abandonment.**
Rejected: it would reuse existing encrypted columns with zero new schema, but it breaks `order.md`
§1's stated invariant in the reader's face — an admin order listing would show rows appear and vanish
for orders that were never real, and every admin/report query would need to filter out a status that
means "not actually an order yet." Keeping `orders` meaning "was charged," with no exception, at the
cost of one new encrypted column elsewhere, was judged the smaller conceptual surface.

**Storing the pending payload unencrypted, relying on the 10-minute window as protection.** Rejected
outright — ADR-005 has no "short-lived, so plaintext is fine" exception, and there is no reason to
open one here.

**Switching to Global Getnet's REST API instead of Redsys TPV Virtual.** Rejected for this iteration:
it is a separate commercial product from the TPV contract the business already holds for its physical
terminal — a new merchant relationship to negotiate, not a drop-in technical alternative. Revisit only
if the business separately contracts it.

**A generic `payment_sessions` table instead of extending `idempotency_keys`.** Rejected:
`idempotency_keys` already has the exact lifecycle this needs (`PENDING`/`COMPLETED`/`FAILED`, a
`resource_id`, an `expires_at`, a cleanup task) — a parallel table with the same three states plus its
own cleanup job would duplicate ADR-011 wholesale for one extra column's worth of need.

---

# Consequences

- Positive: closes the `idempotency_keys` portion of `scheduled-tasks.md` §3.3 — that cleanup task now
  has a fully specified behaviour for `/checkout` rows, not just "something removes expired rows
  eventually."
- Positive: `POST /orders/counter` and `CASH`/`DATAPHONE`/`INTERFLORA` are entirely unaffected — none
  of them call an external gateway (`payment.md` §3.1), so none of this applies to them.
- Negative: checkout is now two HTTP round-trips from the frontend's perspective (the initial `POST`
  plus polling `GET .../status`) instead of one; the frontend must implement the redirect-then-poll
  flow instead of a single request/response.
- Negative: an abandoned checkout still consumes an `order_number` and briefly reserves stock/discount
  for up to 10 minutes — repeated abandoned attempts can transiently reduce availability for others.
  Not addressed here; revisit if it proves to be a real abuse vector (candidate: fold into ADR-016's
  rate limiting).
- The 10-minute expiry and the notification signature key live in configuration
  (`app.payment.redsys.*`), not in this ADR — changing them is an operations decision.

---

# References

- `order.md` §1, §3.1, §3.2, §3.3, §3.9 — the checkout phases and invariants this ADR revises.
- `payment.md` §1, §3.2, §3.3, §3.5, §8, §11 — the synchronous/no-webhook assumptions this ADR
  reverses.
- ADR-005 — PII protection: why `checkout_payload_encrypted` is encrypted, never plaintext.
- ADR-007 — Historical integrity: why keeping `orders` "always paid" was preferred over a new status.
- ADR-009 — Optimistic locking: unaffected, `orders` still gets its `version` column at creation, same
  as today.
- ADR-011 — Idempotent money operations: this ADR is the second caller of the
  `PENDING`→`COMPLETED`/`FAILED` transition it defines, and closes the `idempotency_keys` portion of
  `scheduled-tasks.md` §3.3.
- `scheduled-tasks.md` §3.3 — where the 10-minute abandoned-checkout release is specified.
