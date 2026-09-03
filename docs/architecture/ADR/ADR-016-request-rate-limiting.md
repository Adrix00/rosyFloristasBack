# ADR-016: Request Rate Limiting

- **Status:** Accepted
- **Date:** 2026-09-02
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

`00-security-validation-integrity.md` section 7 already decided the mechanism (Bucket4j, dual
IP + identifier key, `CF-Connecting-IP` as the real client address) and the list of endpoints that
must carry a limit. Section 12 left three things open, and forbade deducing them: the concrete
limits, the windows, and — implicitly — where the counters live and what a rejected request looks
like on the wire.

`auth.md` is the first module that cannot ship without them. Its four entry points (`/auth/login`,
`/auth/admin/login`, `/auth/admin/mfa`, `/auth/refresh`) are the ones an attacker hits: a password
guess, a 6-digit TOTP code with a 10^6 search space, and a stolen refresh token that can be renewed
in a loop at no cost. Shipping them without a limit means shipping the brute-force surface first and
the defence later.

The related decision this ADR does **not** reopen: `INVALID_CREDENTIALS` covers four distinct causes
on purpose (00-security, rule 7). A rate limit must not undo that by answering differently to a
throttled existing account than to a throttled unknown one.

---

# Decision

## Limits and windows

Two buckets per request, one keyed by identifier and one keyed by client IP. **The more restrictive
of the two decides**; both are consumed on every attempt.

| Endpoint | Per identifier | Per IP |
|---|---|---|
| `POST /auth/login` (customer) | 10 / 15 min, keyed by email HMAC | 60 / 15 min |
| `POST /auth/admin/login` | 5 / 15 min, keyed by email HMAC | 20 / 15 min |
| `POST /auth/admin/mfa` | 5 / 5 min, keyed by the admin id in the `mfaToken` | 20 / 15 min |
| `POST /auth/refresh` | 30 / 1 h, keyed by the SHA-256 of the presented cookie | 60 / 1 h |
| `POST /customers/password-reset` (request) | 5 / 1 h, keyed by email HMAC | 20 / 1 h |
| `POST /customers/verify-email` | 10 / 1 h, keyed by email HMAC | 40 / 1 h |
| `POST /customers/resend-verification` | 5 / 1 h, keyed by email HMAC | 20 / 1 h |

The `/customers/*` rows are decided here and applied when `customer.md` is implemented; they are
listed now so the three email-driven endpoints are not each re-decided in isolation later.

Refill is greedy over the whole window (Bucket4j `refillGreedy(capacity, window)`), not a hard reset
at the boundary: a legitimate user who exhausted the bucket recovers gradually instead of waiting
for a cliff, and an attacker gains nothing, since the sustained rate is what the capacity/window
ratio says.

`/auth/admin/mfa` gets the tightest identifier bucket of all: 5 attempts per 5 minutes against a
10^6 code space, with `totp_last_used_step` (`V11`) already blocking replay of a code that did work.

Every limit lives in `application.yml` under `app.rate-limit.*` with these values as the defaults.
Changing a number is an operations decision; changing the shape of the table is a change to this ADR.

## Client IP resolution

`CF-Connecting-IP` is read **only** when the socket address of the request falls inside
`app.rate-limit.trusted-proxies` (a list of CIDRs, Cloudflare's published ranges in production).
The list is **empty by default**, and an empty list means the header is ignored entirely and the
socket address is used. A deployment that is not yet behind Cloudflare therefore cannot be tricked
into trusting a forged header — the header only becomes meaningful once someone has configured the
proxies it can legitimately come from.

## Counter storage

In-memory, per instance (Bucket4j's local `ConcurrentHashMap`-backed buckets), with an eviction of
idle keys so the map cannot grow without bound.

This project runs a single instance today and has no Redis (ADR-008 rejected adding one for reuse
detection on the same grounds). The consequence is explicit: with N instances behind a load
balancer, the effective limit is N times the configured one. Bucket4j's distributed backends
(Redis, or PostgreSQL via `bucket4j-postgresql`) are a drop-in replacement for the bucket resolver
when a second instance is deployed; nothing above changes.

## Bucket keys never carry personal data

The identifier key is the HMAC of the normalized email (`PiiCryptoPort#hmac`, ADR-005), never the
email itself, and never appears in a log line — the same rule that applies to a database column
applies to an in-memory map that a heap dump would expose.

## Rejected request

HTTP **429** with an ADR-012 `ProblemDetail` body, error code `RATE_LIMIT_EXCEEDED`, plus a
`Retry-After` header in seconds computed from the bucket's refill.

The response is identical whether the identifier exists or not — a 429 that only appeared for real
accounts would be exactly the account enumerator that 00-security rule 7 forbids. The rejection
happens in a servlet filter, before the use case runs, so a throttled login costs no Argon2id
verification.

---

# Alternatives considered

**Limiting at the edge (Cloudflare rules or nginx) instead of in the application.** Rejected as the
only defence: the identifier half of the key is a request-body field the edge does not parse, and a
limit that only counts IPs is the case 00-security rule 7 explicitly called insufficient. An edge
limit remains welcome as an additional, coarser layer.

**Spring Security's own `RequestRateLimiter` / Spring Cloud Gateway.** Rejected: this project is a
modular monolith with no gateway in its stack, and adding Spring Cloud to obtain one filter is
infrastructure with no other use here.

**Redis-backed counters from day one.** Rejected for the same reason ADR-008 rejected a Redis
blocklist: a new piece of infrastructure to run, monitor and secure, bought for a single feature on
a single-instance deployment. The upgrade path stays open and is one bean deep.

**Blocking the account after N failures instead of throttling.** Rejected: it converts a brute-force
attempt into a denial of service against the real owner, who cannot unlock themselves —
particularly bad for `OWNER`, the only role that can restore other administrators.

---

# Consequences

- Positive: password and TOTP brute force are bounded before the first credential-checking endpoint
  ships, not after; a stolen refresh token cannot be renewed in a loop at no cost.
- Positive: 00-security section 12, point 3 is closed. Points 1, 2, 4 and 5 stay open.
- Negative: the limit is per instance. Horizontal scaling multiplies it until a distributed bucket
  store is wired — a known, documented ceiling, not an accident.
- Negative: an office or a school behind a single NAT address shares the IP bucket. The IP capacities
  are set well above the identifier ones (4x to 6x) precisely so a shared address stays usable while
  a distributed attack still meets a wall.
- The filter is the single place where a limit is applied. A use case must never re-check one:
  duplicating the knowledge is how the two copies drift.

---

# References

- `00-security-validation-integrity.md` section 7 — the mechanism and the endpoint list this ADR
  gives numbers to; section 12 point 3 — the decision this ADR closes.
- ADR-005 — PII protection: bucket keys use the same HMAC as the database columns.
- ADR-008 — Refresh token rotation: `/auth/refresh` is limited because rotation alone does not make a
  stolen token expensive to use.
- ADR-012 — API error contract: `RATE_LIMIT_EXCEEDED` is published as a `ProblemDetail` like every
  other error.
