# ADR-014: Purchase Receiving and Reversal

- **Status:** Accepted
- **Date:** 2026-08-26
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

`purchases` in V1 had no status. A purchase row and its `purchase_items` were, by the schema alone,
just an accounting document — nothing distinguished "we ordered this from the supplier" from "this
is now in the shop". Feeding `stock_movements` straight from creation would conflate the two: a
typo in a not-yet-delivered order would move stock that physically isn't there yet, and there would
be no way to log an order before the goods arrive.

Separately, mistakes happen when receiving a purchase — wrong quantity typed, wrong product picked —
and by then stock has already moved. Correcting that by hand with a raw `ADJUSTMENT` works but gives
the administrator no guided way to say "this specific receipt was wrong, undo it."

---

# Decision

## Three states, one direction

`purchases.status`: `ORDERED` → `RECEIVED` → `REVERTED`. `V8` adds the column plus
`received_at`, `reverted_at` and `revert_reason`, each governed by a `CHECK` tying its presence to
the state — the same pattern `payments.refunded_at`/`refunded_amount` already uses.

- **`ORDERED`** — the purchase exists as a document; no stock movement yet. Editable and deletable
  freely, because nothing downstream depends on it.
- **`RECEIVED`** — marking it received generates one `PURCHASE` stock movement per line that carries
  a `product_id` (bulk material, `product_id IS NULL`, never touches `stock_movements`). From here
  the purchase is immutable — its quantities and costs are now a record of what actually happened.
- **`REVERTED`** — the administrator undoes a wrong receipt. Generates one `ADJUSTMENT` movement per
  affected line, negating the `PURCHASE` quantity, with `revert_reason` mandatory. Terminal: a
  reverted purchase is not reopened, a corrected one is entered as a new purchase.

## Reversal generates `ADJUSTMENT`, not a deleted `PURCHASE`

`stock_movements` rows are never deleted or edited (`docs/database/README.md` already establishes
this for every movement type). Undoing a receipt is therefore a new movement in the opposite
direction, not the removal of the original — the audit trail keeps both the mistaken receipt and its
correction, which is the entire point of an append-only ledger.

## Reversal can fail if the stock is no longer there

If some of the received units were already sold before the revert is requested, the reversing
`ADJUSTMENT` would drive `products.stock` negative. It is rejected outright (409), not partially
applied: a partial reversal would leave the purchase in an ambiguous state — neither fully received
nor fully undone — for a case simple enough (someone already bought the flowers) that the
administrator needs to know about it and decide by hand, not have the system silently do half a job.

## `invoice_number` becomes unique per supplier

`uq_purchases_supplier_invoice`, `UNIQUE (supplier_id, invoice_number)`. `NULL` values coexist
without limit — a purchase entered without an invoice number yet does not block another one from the
same supplier — the same convention `customers.email_hash` already uses for archived customers. This
catches the most common data-entry mistake in manual bookkeeping: loading the same invoice twice.

---

# Alternatives considered

**No status; stock moves at creation.** Rejected: cannot represent "ordered but not yet arrived",
and a typo at entry time immediately corrupts real stock with no correction path beyond a raw manual
`ADJUSTMENT`.

**Editable while `RECEIVED`, immutable only after some later step.** Rejected: it reintroduces an
implicit fourth state ("received but not yet finalized") that the schema does not name and the admin
cannot reason about. `RECEIVED` already means the goods are in the shop; editing it after the fact
should look like what it is — a correction — not a silent edit.

**Partial reversal, undoing only what remains in stock.** Rejected for the ambiguity described above;
may be revisited if it turns out to be common enough to be worth the extra complexity.

---

# Consequences

- Positive: a purchase can be logged before goods arrive, and a wrong receipt has a guided, audited
  way to be undone instead of a manual `ADJUSTMENT` with no link back to the purchase that caused it.
- Positive: `stock_movements` stays append-only; nothing about this decision touches existing rows.
- Negative: reverting a receipt that was partially sold is a hard stop, not a convenience — the
  administrator must resolve it manually (a `WASTE`/`ADJUSTMENT` combination, out of this document's
  scope) if a genuine correction is still needed.
- Negative: one more status field, one more set of transition rules to enforce in the service layer,
  since none of `ORDERED → RECEIVED → REVERTED` is enforceable by a `CHECK` alone (it depends on the
  current row, which a `CHECK` cannot compare against).

---

# References

- `docs/features/inventory.md` — `RegisterStockMovementUseCase`, the single write path this ADR's
  `PURCHASE` and `ADJUSTMENT` movements both go through.
- ADR-007 — historical integrity: purchases join orders and payments as a record that is corrected by
  addition, never by rewriting history.
