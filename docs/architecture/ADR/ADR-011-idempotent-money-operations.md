# ADR-011: Idempotent Money Operations

- **Status:** Accepted
- **Date:** 2026-08-22
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

A checkout request that times out on the network is indistinguishable, from the client's side, from
one that failed. The customer retries, or double-clicks, and the request arrives twice. V1 protects
half of this: `ux_payments_captured_per_order` makes a second `CAPTURED` payment for the same order
impossible. It does not protect the other half — two requests that each create their own `orders`
row, each with its own `order_number` and its own payment, are two perfectly valid orders as far as
the database is concerned. The customer is charged twice for two legitimate-looking orders.

---

# Decision

Operations that create money-moving resources — `POST` of a checkout and of a payment — require an
`Idempotency-Key` header: a client-generated UUID, unique per logical operation, reused verbatim on
every retry of that operation.

State lives in `idempotency_keys`, unique on `(idempotency_key, endpoint)`.

**Flow.** The row is inserted as `PENDING` *before* the operation runs. The unique constraint is the
lock: a concurrent duplicate collides on insert and is answered "operation in progress" (HTTP 409)
rather than executing the charge a second time. On success the row moves to `COMPLETED`, storing the
HTTP status and the `resource_id`. A later retry with the same key finds `COMPLETED` and is answered
from that.

**No response body is stored.** A checkout response contains the recipient's name, address and phone
— PII that ADR-005 keeps encrypted in `orders` and `order_deliveries`. Persisting the rendered
response would put a plaintext copy in a table with its own expiry, outside the reach of the ADR-007
retention purge. Instead the retry is answered by re-reading the resource by its `resource_id`,
through the same access control as a normal read. The replayed response is therefore semantically
equivalent, not byte-identical — acceptable, since the client's question is "did my order go
through, and which one is it".

**`request_fingerprint`** (SHA-256 of the request body) distinguishes a retry from a mistake. The same
key with a different body is a client bug — a key reused across two genuinely different operations —
and is rejected (HTTP 422) rather than being answered with the first operation's result, which would
silently discard the second request. Checkout is the one exception: its body carries no line items —
the order is built from the server-side cart — so its fingerprint also hashes the cart's contents at
request time, not the body alone (`docs/features/payment.md`, section 3.8).

**Expiry.** Rows carry `expires_at` and are removed by a cleanup task, not by the database. The
retention window only needs to outlive plausible client retries, not the order itself.

---

# Alternatives considered

**Rely only on `ux_payments_captured_per_order`.** Rejected: it prevents the double *capture*, not the
duplicate *order*. The customer would still end up with two orders, one of them unpayable, and
support would have to clean it up by hand.

**Deduplicate by request content alone, with no client-supplied key.** Rejected: two genuinely
separate orders with identical contents (same customer ordering the same bouquet twice for two
different recipients — or the same one, twice on purpose) are legitimate and would be silently
collapsed into one.

**Store the full response body for byte-identical replay.** Rejected on the PII grounds above. The
gain — an identical byte stream instead of an equivalent one — does not justify a second plaintext
copy of buyer and recipient data.

**Redis instead of a table.** Rejected for the same reason as in ADR-008: the project runs a single
PostgreSQL instance and has no other use for Redis yet, and idempotency state must survive a restart
to be worth having.

---

# Consequences

- Positive: a network retry or a double-click cannot produce a duplicate order or a duplicate charge.
- Negative: the frontend must generate and persist the key for the duration of the operation —
  regenerating it on retry defeats the whole mechanism. This is a hard requirement on the client, not
  a suggestion.
- Negative: a concurrent duplicate receives 409 "in progress" and must poll or retry, rather than
  blocking until the first request finishes.
- Operational: `idempotency_keys` needs a cleanup job for expired rows. Without it the table grows
  without bound; nothing breaks functionally, but it is unmanaged growth.

---

# References

- ADR-005 — PII protection: why the response body is not persisted.
- ADR-007 — Historical integrity and data lifecycle: the retention purge this decision avoids
  complicating.
- ADR-008 — Refresh token rotation: same reasoning for choosing PostgreSQL over Redis.
