# ADR-008: Refresh Token Rotation and Revocation

- **Status:** Accepted
- **Date:** 2026-08-22
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

Authentication issues two kinds of token: a JWT access token, stateless and never persisted, and a
refresh token, persisted in `refresh_tokens` as `token_hash` (SHA-256, see ADR-005) so it can be
looked up and revoked. `refresh_tokens.expires_at` already exists, but V1 does not say how a refresh
token is renewed, what happens to the row it replaces, or how a stolen refresh token gets detected
and shut down. Two subjects share the table (`customer_id` xor `admin_user_id`, enforced by
`chk_refresh_tokens_subject`), and the admin panel carries materially higher risk per session than a
customer's cart, which argues against a single rotation policy for both.

---

# Decision

## Access token lifetime

15 minutes for a customer, 5 minutes for an admin. Short enough that a leaked access token (it is
never revocable, being stateless) is worthless within minutes; the refresh token is what carries the
actual session.

## Rotation: single use

Every use of a refresh token issues a new access/refresh pair and immediately revokes
(`revoked_at = now()`) the refresh token that was just presented. A refresh token is never valid a
second time.

## Reuse detection: family_id

Every refresh token descended from the same login shares a `family_id` (added in
`V2__refresh_token_family.sql`). If a refresh token is presented whose `revoked_at` is already set,
that is not an expired-token error — it means either the legitimate client retried after a dropped
response, or an attacker replayed a stolen token after the legitimate client already rotated past it.
This project cannot distinguish the two cases, and treats both the same, favouring the compromised
case: every row sharing that `family_id` is revoked, ending the entire session and forcing full
reauthentication.

## Family maximum lifetime

The family carries an absolute cap from creation, not a sliding window:

- **30 days** for a customer — prioritises not logging out an active shopper.
- **12 hours** for an admin — forces at least daily reauthentication (TOTP included) on the panel
  that can change orders, stock and prices.

Each rotation copies the family's original `expires_at` onto the new row instead of extending it.
Without this, a session refreshed just before every expiry would never end, defeating the point of a
maximum lifetime. Nothing in the database enforces that the copy happened correctly — like
`products.stock` (see `docs/database/README.md`), this is an application invariant, upheld by the
single write path (the future token-rotation use case), not by a `CHECK` or a trigger. A `CHECK`
cannot compare a row against its siblings, and this project already avoids triggers for
cross-row invariants.

---

# Alternatives considered

**No rotation, long-lived refresh token reused until `expires_at`.** Rejected: a stolen refresh token
would be indistinguishable from the legitimate one for its entire remaining lifetime, with no signal
that theft occurred.

**Reuse detection via a Redis blocklist instead of `family_id` in PostgreSQL.** Rejected: this project
already persists refresh tokens relationally (ADR-002), reuse detection is a lookup against rows that
already exist, and adding Redis for this alone is infrastructure the project has no other use for
yet.

**Single TTL for both customer and admin refresh tokens.** Rejected: the admin panel can alter stock,
prices and order status; a 30-day admin session sitting on a stolen laptop is a materially worse
outcome than a 30-day customer session on an abandoned cart.

---

# Consequences

- Positive: a replayed refresh token is detected and shuts down the whole session, not just the one
  token; admin sessions cannot silently outlive a working day.
- Negative: an admin using the panel continuously still needs to fully reauthenticate (TOTP included)
  every 12 hours — accepted as the cost of bounding the blast radius of a stolen admin session.
- Negative: the client (web/app) must treat "refresh rejected because reused" as a distinct signal
  from "refresh rejected because expired" and force a full logout with a visible message, not a
  silent retry — an output port for revoking a token family (`RevokeTokenFamilyPort` or equivalent)
  will be introduced when the corresponding use case is implemented, following ADR-003.
- No schema change beyond `family_id` and its index: `expires_at`, `revoked_at` and `token_hash`
  already covered the rest.

---

# References

- ADR-002 — JPA and JDBC: `refresh_tokens` writes/reads follow the same hybrid persistence rules.
- ADR-003 — Capability-based ports: the future rotation use case depends on a narrow output port, not
  a generic repository.
- ADR-005 — PII protection and payment tokenization: `token_hash` stays SHA-256, unchanged by this
  decision.
