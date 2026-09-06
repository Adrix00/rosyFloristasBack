# ADR-009: Optimistic Locking on Aggregate Roots

- **Status:** Accepted
- **Date:** 2026-08-22
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

The V1 schema solves concurrency exactly where money and stock are at stake, and nowhere else:
`products.stock` is decremented with a conditional `UPDATE ... WHERE stock >= :quantity`,
`product_discounts.quantity_sold` with a conditional `UPDATE` against `quantity_limit`, and
`ux_payments_captured_per_order` forbids a second captured payment. Everything else is last-write-wins.

That leaves real races unaddressed. Two admins editing the same product from the panel silently lose
one of the two edits. Worse, a customer deactivation (ADR-007 — set `status = 'ARCHIVED'`, `NULL` out
the PII) running concurrently with a profile update can end with the profile update's `UPDATE`
writing the PII back onto an already-archived row: silent resurrection of personal data the customer
asked to have removed, with no error anywhere.

---

# Decision

A `version BIGINT NOT NULL DEFAULT 0` column (JPA `@Version`) on the aggregate roots where concurrent
writes are plausible:

- `products` — several admins editing the catalog.
- `orders` — panel state changes racing with customer-side actions.
- `customers` — profile updates racing with GDPR deactivation (see above).
- `customer_payment_methods` — default-card switching from two devices.
- `admin_users` — role and TOTP changes from the owner panel.

Child entities are deliberately excluded: `customer_addresses`, `order_items`, `order_deliveries`,
`product_images`, `product_categories` and the rest are only ever written through their aggregate
root, whose `version` already serialises the change. Giving a child its own version would enforce a
boundary that the aggregate already enforces, and would let a caller mutate a child without touching
the root — the thing DDD aggregates exist to prevent.

Optimistic locking **does not replace** the conditional `UPDATE`s. They solve different problems:
`@Version` detects that a row changed between read and write; the conditional `UPDATE` on
`products.stock` never reads first at all, and enforces a value predicate (`stock >= :quantity`) that
a version number cannot express. Both mechanisms coexist on the same row — a sale updates `stock`
conditionally while an admin edit of the same product's name is protected by `version`.

A version conflict surfaces to the caller as HTTP 409, never as a silent retry: the second writer
must re-read and decide, because the application cannot know whether the two edits were compatible.

---

# Alternatives considered

**Pessimistic locking (`SELECT ... FOR UPDATE`).** Rejected: it holds row locks for the duration of a
user's edit — in the admin panel that means the length of a form-filling session — and turns a rare
conflict into a routine blocking wait.

**Last-write-wins everywhere, relying only on the V1 conditional updates.** Rejected because of the
customer-deactivation case: silently rewriting purged PII is a data-protection failure, not merely a
lost edit.

**A version column on every table, children included.** Rejected: children have no independent write
path, so their version would never detect anything the root's version did not already detect.

---

# Consequences

- Positive: concurrent edits fail loudly instead of silently discarding one of them; PII purged by a
  deactivation cannot be resurrected by an in-flight update.
- Negative: callers of the admin panel must handle 409 and re-read; the frontend needs a visible
  "someone else changed this" path rather than a blind retry.
- Neutral: the column is invisible to the domain layer — it lives on the JPA entity in
  Infrastructure, per ADR-002, and never appears in a domain object or a DTO.

---

# References

- ADR-002 — JPA and JDBC: `@Version` is a JPA concern and stays inside Infrastructure.
- ADR-007 — Historical integrity and data lifecycle: the customer deactivation flow this ADR protects.
- `docs/database/README.md` — the conditional `UPDATE` patterns for stock and discounts.

---

# Amendment (2026-09-06): `carts`

The original decision left `carts` unaddressed — neither listed above nor explicitly excluded. In
`feature/cart`, `CartEntity` shipped without `@Version` and a Javadoc comment claiming this ADR
excluded it "same as `customer_addresses`, `product_images`" (a child-entity exclusion this ADR does
grant to child tables, but a cart is an aggregate root of its own, not `products`' child). Neither
this ADR nor `cart.md` ever recorded that exclusion; the code review that caught the discrepancy is
the reason this amendment exists.

**`carts` is added to the list of aggregate roots with `version` (JPA `@Version`).** A customer's own
cart is reachable from two devices at once — add-to-cart on a phone and a laptop tab open on the same
account — and cart.md's rule 3.1 already resolves a signed-in customer's cart the same way regardless
of device, by `customer_id`, not by cookie. Two such writes racing is the same shape of problem this
ADR protects `customer_payment_methods` from: last-write-wins would silently discard one device's
line additions, with no error on either side.

**The line quantity write stays a plain overwrite, not itself conditional**, unlike
`products.stock`: cart.md's rule 3.3 has no invariant like `stock >= 0` for a cart line to protect
mid-write, so there is nothing for a conditional predicate to check. `@Version` alone is the right
tool here — it is `products.stock` that additionally needs the conditional `UPDATE`, not every
aggregate this ADR covers.

**`version` stays invisible on the wire, unlike every other aggregate this ADR lists.** The admin
panel's `PUT /products/{id}` and friends round-trip a `version` field the client must send back —
workable because an admin is looking at a form, editing one row at a time. Cart writes are
one-line-at-a-time increments (`POST /cart/items`, `PATCH /cart/items/{id}`) triggered by ordinary
shopping, not form edits; requiring the SPA to track and resend a `version` for every add-to-cart
click would be a contract change with no admin-panel precedent to justify it, for an aggregate no
human is staring at while editing.

Resolution: a version conflict on a cart write is retried once, transparently, by re-reading the
cart and reapplying the same use-case-level operation (add/set/remove the one line the request named)
against the fresh version — never surfaced to the client as 409. This is safe specifically because a
cart write commutes with itself: "add 2 units of product X" produces the same end state whether it is
the first or second attempt to apply it against the latest row, unlike an admin's arbitrary field
edit, which is why every other `@Version` aggregate in this ADR answers 409 instead of retrying.

**`carts.touch()` (renews `expires_at` on every write, cart.md rule 3.7) bumps `version` too.** It is
a real `UPDATE` to the row like any other, currently issued as a standalone JPQL statement
(`CartJpaRepository.touch`) that bypasses the JPA-managed entity's own `@Version` check. Left alone,
that statement would let two concurrent line-writes both renew the expiry unconditionally after one
of them lost the retry above, masking exactly the race this amendment exists to close — the same gap
ADR-009's original text does not call out for the JDBC stock writes, since those are guarded by their
own value predicate instead. `touch` has no such predicate to fall back on, so it must go through the
versioned entity path (a `findById` + field assignment + `save`) rather than a bare `UPDATE`.

See `cart.md`, section 2 and 3.3, for the resulting concurrency model as documented for that module.
