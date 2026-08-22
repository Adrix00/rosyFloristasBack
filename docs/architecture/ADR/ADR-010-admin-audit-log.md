# ADR-010: Admin Audit Log

- **Status:** Accepted
- **Date:** 2026-08-22
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

The V1 schema audits two specific things and nothing else: `order_status_history` records who changed
an order's status, and `stock_movements` records who moved stock. Everything else an administrator
does — editing a price, creating a discount, deactivating a product, changing another admin's role —
leaves no trace. When a price is wrong or a product disappears from the catalog, there is no way to
answer "who did this and when".

The obvious design, a generic audit table holding the before/after values of every change, collides
with two decisions already taken. ADR-005 encrypts customer and buyer PII (AES-256-GCM) precisely so
that a database dump does not expose it; a plaintext before/after copy of `customers.email` in an
audit table would defeat that at zero cost to an attacker. ADR-007 defines a retention purge that
`NULL`s out specific PII columns; values buried inside arbitrary JSONB would be PII the purge does
not know about and therefore never erases — a GDPR failure that appears years later, silently.

---

# Decision

One table, `audit_log`, recording administrative actions not already covered by
`order_status_history` or `stock_movements`. Those two remain the source of truth for their own
changes and are **not** duplicated here.

Every row records the actor (`admin_user_id`, `ON DELETE SET NULL` so the trail outlives the admin),
the `action`, the `entity_type`/`entity_id`, and `changed_fields` — the names of the fields that
changed.

Before/after **values** (`changes JSONB`) are recorded **only for entities that carry no personal
data**: products, categories, discounts, images, suggestions, attribute definitions, shipping rates,
suppliers, purchases and purchase items. For entities that do carry PII — `customers`, `orders`,
`order_deliveries`, `customer_addresses`, `customer_payment_methods`, `admin_users` — only the field
names are recorded, never their contents.

The allow-list is enforced by a `CHECK` constraint in the migration, not only by application code:

```sql
CONSTRAINT chk_audit_log_changes_pii_free CHECK (
    changes IS NULL OR entity_type IN ('product', 'category', ...)
)
```

The failure mode this guards against is not a malicious developer but a forgetful one: a new entity
audited a year from now does not silently inherit permission to store its values. Adding it requires
an explicit migration, which is exactly the moment someone asks whether it holds PII.

---

# Alternatives considered

**No audit log; rely on what V1 already records.** Rejected: it answers "who changed this order's
status" and "who moved this stock", but not "who changed this price", which is the question that
actually gets asked when something looks wrong in the catalog.

**Before/after values for every entity, encrypted.** Rejected: it creates a second population of
encrypted PII with its own lifecycle, which the retention purge (ADR-007) would have to traverse
inside JSONB. The cost is real and recurring; the benefit — reconstructing a customer's old phone
number — is not something the business has asked for.

**Before/after values for every entity, in plaintext.** Rejected outright: directly contradicts
ADR-005.

**Application-code allow-list instead of a `CHECK`.** Rejected: the constraint costs one line and
removes the class of bug where a new entity type is audited without anyone considering its PII.

---

# Consequences

- Positive: catalog and pricing changes become traceable; no new PII enters the database, so the
  ADR-007 purge is unaffected.
- Negative: for PII-bearing entities the log answers "who touched what" but not "what did it say
  before". Accepted — the historical record that matters legally lives in `orders`, which carries its
  own immutable snapshot.
- Operational: `audit_log` grows monotonically and is never purged by the retention job (it holds no
  PII). If volume becomes a problem, time-based partitioning or archival is the answer, not deletion
  of recent rows.

---

# References

- ADR-005 — PII protection: the reason values are excluded for PII-bearing entities.
- ADR-007 — Historical integrity and data lifecycle: the retention purge this decision keeps simple.
- ADR-009 — Optimistic locking: `version` changes are not themselves audited.
