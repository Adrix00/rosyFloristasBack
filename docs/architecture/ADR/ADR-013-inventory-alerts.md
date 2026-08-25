# ADR-013: Inventory Alerts

- **Status:** Accepted
- **Date:** 2026-08-22
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

Two inventory conditions matter and neither was surfaced anywhere: a managed product running low on
stock (a business concern — restock before it sells out), and `products.stock` disagreeing with the
sum of its own `stock_movements` (an integrity concern — the single-write-path guarantee described in
`docs/database/README.md` broke somewhere). Both existed only as something an administrator could
check by hand: a product listing for the first, the `GET /inventory/reconciliation` report for the
second. Neither is checked unless someone remembers to look.

---

# Decision

## One alert table for both conditions

`inventory_alerts` (`V7`), with `type` distinguishing `LOW_STOCK` from
`RECONCILIATION_MISMATCH`. `observed_value` and `expected_value` are generic on purpose:
for `LOW_STOCK` they are the current stock and the configured threshold; for
`RECONCILIATION_MISMATCH` they are `products.stock` and the sum of its movements. A second table for
the second condition would duplicate the same lifecycle (open, resolve, dismiss, history) for no
benefit — the two conditions are different in cause, identical in how an administrator handles them.

## Per-product threshold, not global

`products.low_stock_threshold`, nullable. `NULL` means no alert is configured, the same convention
`products.stock = NULL` already uses for "no inventory management". A global number is wrong for this
catalog: five units is a lot for an expensive vase and almost nothing for loose-stem roses.

## A daily scheduled job, not a live check

Both conditions are evaluated once a day by a scheduled task
(`infrastructure/scheduler`, already part of the project's package structure), not on every request.
Neither condition is time-critical in the way a sale's stock check is (ADR already covered by the
conditional `UPDATE` in `docs/features/inventory.md`, section 3.1): a product crossing its threshold
or a mismatch appearing does not need to be caught within seconds, and checking on every read would
cost far more than it is worth.

## No duplicate open alerts

`ux_inventory_alerts_open`, a unique partial index on `(product_id, type) WHERE status = 'OPEN'`.
The daily job re-running finds the existing `OPEN` row and leaves it alone instead of creating a
second one for the same ongoing problem. The database enforces this, not application logic that could
be bypassed by a bug in the very job this system exists to catch.

## Three outcomes, one of them is doing nothing

An administrator resolves an alert (the underlying problem was fixed — restocked, corrected with an
`ADJUSTMENT`), dismisses it (acknowledged, no action needed — a threshold that was set too
conservatively), or takes no action at all, which is not a fourth state: it is simply staying `OPEN`.
Both `resolve` and `dismiss` are terminal; nothing reopens a closed alert automatically, because
reopening would need the same duplicate-detection this design already handles for creation, and the
job creates a fresh alert instead if the condition recurs after being addressed.

## Resolution is manual, not automatic

The daily job never closes an alert on its own, even if the condition that caused it has gone away by
the next run — stock was replenished, a movement was corrected. This was a deliberate simplification:
auto-resolution needs the job to distinguish "fixed by an intentional action" from "coincidentally
no longer true this exact minute", which is a real design question on its own. Until it is answered,
an alert stays until an administrator closes it, even if the underlying number has already recovered.

---

# Alternatives considered

**Two separate tables, `low_stock_alerts` and `reconciliation_alerts`.** Rejected: identical
lifecycle, identical admin actions, and a shared history view would need a `UNION` between them
anyway.

**A global low-stock threshold instead of per-product.** Rejected: the catalog spans products with
wildly different natural stock levels, and one number would be wrong for most of them.

**Live evaluation on every request instead of a daily job.** Rejected for cost with no matching
benefit: neither condition is urgent enough to justify checking it on every catalog read or every
stock write.

**Auto-resolving an alert once its condition disappears.** Rejected for now, not because it is a bad
idea, but because it hides a real question — whether "fixed" and "temporarily not true" should be
treated the same — that deserves its own decision instead of a default. See `inventory.md`,
open item.

---

# Consequences

- Positive: both conditions are now visible without anyone remembering to check; the admin panel gets
  one history view, one set of actions, for two different problems.
- Positive: the unique partial index makes duplicate-alert prevention a database guarantee, not
  something the job's code has to get right every run.
- Negative: detection is up to a day late by design — acceptable, since neither condition is
  time-critical.
- Negative: a fixed problem still shows as `OPEN` until someone closes it by hand. Accepted as a
  known simplification, not an oversight.

---

# References

- `docs/features/inventory.md` — section 3.1 (live stock check, unrelated to this) and the
  reconciliation queries this alert type is built on.
- ADR-007 — historical integrity: the invariant this ADR's `RECONCILIATION_MISMATCH` alert exists to
  catch when it breaks.
