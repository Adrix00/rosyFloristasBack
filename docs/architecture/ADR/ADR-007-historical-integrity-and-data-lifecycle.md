# ADR-007: Historical Integrity and Data Lifecycle

- **Status:** Accepted
- **Date:** 2026-08-16
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

Products and customers are not permanent: products get discontinued, customers ask to close their
account. Orders, payments and stock movements must survive both events, because they are legal and
accounting records, not because the product or the customer still exists. This ADR fixes what a
product's lifecycle and a customer's deletion actually do to the rows that reference them — the
question ADR-004 and the persistence ADRs (ADR-002, ADR-003) do not answer, because it is a data
lifecycle decision, not a persistence-technology one.

---

# Decision

## Product lifecycle

`products.status` is a single field with three values, not a status plus a separate `deleted_at`:

- **ACTIVE** — visible in the catalog, purchasable.
- **INACTIVE** — hidden temporarily (out of season, no supply). Reversible.
- **DISCONTINUED** — permanently withdrawn. Never returns to the catalog; the row stays for history.

A fourth soft-delete field was rejected: it would be a second way to say "discontinued" and the two
would eventually disagree.

Physical deletion is bounded by foreign keys, not by application code:

| Referencing table | ON DELETE | Reason |
|---|---|---|
| `order_items.product_id` | RESTRICT | Sales history |
| `stock_movements.product_id` | RESTRICT | Inventory audit trail |
| `purchase_items.product_id` | RESTRICT | Accounting document |
| `cart_items.product_id` | CASCADE | The cart is not historical |
| `product_categories`, `product_images`, `product_suggestions`, `product_discounts` | CASCADE | Accessories of the product, no independent history |

`DELETE FROM products` only succeeds for a product that was never sold, never had a stock movement,
and never appeared in a purchase.

## Customer deactivation

Chosen: **archive in place** (`customers.status = 'ARCHIVED'`), not a separate `customer_history`
table. A moved-row design was considered and rejected: it would force `orders.customer_id` to point
at a second table — a polymorphic foreign key, which PostgreSQL cannot enforce referentially — and it
would duplicate PII across two places that then both need purging.

The principle instead: **legal traceability belongs to the order, not to the customer.** An order is
a self-contained commercial document, the same way `order_items` carries `product_name` rather than
depending on `products` staying unchanged. `orders` therefore carries an encrypted snapshot of the
buyer (`buyer_first_name_encrypted`, `buyer_last_name_encrypted`, `buyer_email_encrypted`,
`buyer_email_hash`, `buyer_phone_encrypted`, `buyer_phone_hash`). This fits DDD: `Order` is an
aggregate with its own invariants, not a pointer into `Customer`.

Deactivation, executed as one transaction in the use case:

1. `customers.status = 'ARCHIVED'`, `anonymized_at = now()`.
2. PII set to `NULL`: both encrypted values and their HMAC hashes, and `password_hash`. Without
   `password_hash` the customer cannot authenticate; without `email_hash` the login flow cannot even
   find the row.
3. `ON DELETE CASCADE` removes `customer_addresses`, `customer_payment_methods`, `carts`,
   `verification_tokens` and `refresh_tokens` for that customer.
4. The use case additionally revokes the `payment_method_token` at the gateway — that value lives
   outside PostgreSQL and this migration cannot reach it.
5. `orders` is not touched. `orders.customer_id` keeps pointing at the now-anonymized row, which
   itself carries no PII (`ON DELETE SET NULL` on that foreign key only fires if the customer row is
   later physically deleted, which no normal use case does).

No constraint requires a WEB order to have `customer_id`. Creating a WEB order without a customer is
prevented by the use case (`PlaceOrderService`), not by a `CHECK`: it is a rule about how a row may be
created, not an invariant the row must hold forever — and the row legitimately loses that customer
link later in its life.

## Retention and purge of order PII

`orders.retention_until DATE` is set when the order closes, by adding a configurable period
(`app.retention.orders-period`) to `placed_at`. **No period is hard-coded anywhere in the schema or
this ADR** — the applicable legal retention period is an external configuration value, determined by
the legal requirement in force, not a number this codebase invents.

`PurgeExpiredOrderPersonalDataService` (application-layer, outside this migration) sets to `NULL`
both the encrypted buyer fields and their HMAC hashes on `orders`, and the encrypted fields of
`order_deliveries`, for every order past its `retention_until`, and stamps
`personal_data_purged_at`. Amounts, dates, channel and line items survive: accounting and metrics
stay intact, the order stops being PII.

The process is idempotent by construction:
`WHERE retention_until <= CURRENT_DATE AND personal_data_purged_at IS NULL` — an order already purged
never matches again, and an order still open has `retention_until IS NULL` and never matches at all.

**Orders are never deleted** by any normal use case, and retention does not delete them either — it
only removes their PII. The `ON DELETE CASCADE` on `order_items`, `order_deliveries` and
`order_status_history` exists to preserve referential integrity in the event of an exceptional,
administrative deletion, not as part of the ordinary lifecycle. `payments.order_id` is `RESTRICT`
specifically so that such an exceptional deletion cannot silently remove an accounting record.

## Inventory: what the database guarantees, and what it does not

`products.stock = NULL` means "this product has no managed inventory" (made-to-order bouquets) — not
"stock unknown". Sales for such a product never check availability and never produce a
`stock_movements` row. `products.stock >= 0` means managed inventory: every sale checks availability
and every change produces a movement.

**The rule "an unmanaged product cannot have movements" is an application invariant, not a database
constraint.** A `CHECK` cannot query another table, and both a synchronizing trigger and an artificial
composite foreign key were considered and rejected — the former hides business logic outside the use
case (contradicting ADR-001), the latter buys a guarantee at the cost of two extra columns for a rule
this project chooses to enforce in code instead. It is stated here as exactly that: an invariant of
`RegisterStockMovementService`, not of PostgreSQL.

Every managed product's stock originates from one `INITIAL` movement, enforced unique per product by
a partial unique index. Selling under concurrency uses a single conditional `UPDATE`, never a
`SELECT` followed by an `UPDATE`:

```sql
UPDATE products SET stock = stock - :quantity
WHERE id = :productId AND stock IS NOT NULL AND stock >= :quantity
RETURNING stock;
```

Zero rows affected means insufficient stock; the returned value feeds `resulting_stock` on the
movement row inserted in the same transaction. `resulting_stock` is itself only an audit value — the
database cannot guarantee it matches `products.stock` without a trigger, and no trigger is
introduced. Reconciliation is a query (documented in `docs/database/README.md`), run periodically or
on demand, not a database-enforced invariant.

## Discounts: reservation under concurrency

`product_discounts.quantity_sold` is reserved and released with the same conditional-`UPDATE`
pattern as stock, never a read-then-write:

```sql
UPDATE product_discounts SET quantity_sold = quantity_sold + :quantity
WHERE id = :discountId AND (quantity_limit IS NULL OR quantity_sold + :quantity <= quantity_limit);
```

The reservation and the order creation happen in the same transaction, so a failure after the
reservation rolls both back together. If an order is later cancelled, rejected, fails outright, or a
card pre-authorization expires before capture, the reserved units are released through the same
mechanism, in its own transaction.

---

# Consequences

- A product can be discontinued freely; its historical orders remain fully readable through their own
  snapshot, independent of the product row's current state.
- A customer can request deletion without the platform losing its accounting or legal trail — the
  trail lives in the order, not the customer.
- No hard-coded retention period exists to become wrong when the legal requirement changes;
  `app.retention.orders-period` is the single place that value is configured.
- Stock and discount correctness under concurrent requests depends on every write going through the
  same conditional-`UPDATE` pattern. A future write path that bypasses it (a bulk script, a direct
  SQL update) reintroduces the race this ADR closes.
- Reconciliation queries are the operational safety net for what the schema cannot enforce by itself;
  they must be run and alerted on, not written once and forgotten.

---

# Alternatives considered

**`customer_history` table, row physically moved on deactivation.** Rejected: forces a polymorphic FK
from `orders`, or a data migration of `orders.customer_id` on every deactivation, and duplicates PII
across two tables that must then both be purged in step.

**Trigger-synchronized `products.stock`.** Rejected: ADR-001 puts business logic in the use case: a
trigger firing invisibly on every `INSERT` into `stock_movements` would surprise anyone reading
`RegisterStockMovementService` and finding no mention of how `products.stock` actually changes.

**Composite FK (`stock_managed` flag) to enforce "no movements without managed stock" in the
database.** Rejected: buys a real guarantee, but at the cost of two additional columns
(`products.stock_managed`, `stock_movements.product_stock_managed`) for a single invariant this
project accepts enforcing at the application layer instead. Revisit if inventory bugs in production
suggest the application-level guarantee is not holding.

**Hard-code a retention period (e.g., 6 years) directly in the migration.** Rejected outright: the
applicable period is a legal determination outside this codebase's authority to invent, and
hard-coding one would need a schema change every time the legal requirement is reviewed.
