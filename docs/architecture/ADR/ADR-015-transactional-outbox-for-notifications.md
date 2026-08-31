# ADR-015: Transactional Outbox for Notifications

- **Status:** Accepted
- **Date:** 2026-08-31
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

Several use cases now owe someone an email: a confirmed order owes the buyer a receipt and the shop an
alert, a refund owes the customer a confirmation, a rejected order owes an explanation, a delivered
order owes a notice. Sending one is an outbound network call, which puts it in the same category as
the payment gateway in ADR-011 — it cannot live inside a database transaction.

The two obvious placements are both wrong. Sending *inside* the transaction means an email can go out
describing an order that then rolls back, and it means the mail provider's latency and availability
become the checkout's. Sending *after* commit, fire and forget, means a provider hiccup silently
destroys a receipt the customer is entitled to, with nothing left but a log line nobody reads.

There is a second problem specific to this project. ADR-005 encrypts customer and buyer PII precisely
so a database dump does not expose it, and ADR-011 refused to persist rendered HTTP responses for
exactly that reason: a rendered order confirmation contains the recipient's name, address and phone.
Any queue or table holding rendered email bodies would be a second, plaintext population of the same
PII, outside the reach of the ADR-007 retention purge.

---

# Decision

A **transactional outbox** in PostgreSQL: table `notifications`, written inside the same transaction
as the business change that owes the email, and drained by a scheduled sender.

**What the row holds is a reference, never content.** `type` plus `order_id` and/or `customer_id`, and
nothing else about the message. The sender renders the email at send time by reading the source
tables, through the same decryption path as any other read. No recipient address, no subject, no body,
no rendered HTML is ever stored — the same reasoning ADR-011 used to refuse persisting response
bodies, applied to the same data.

**Atomicity comes free.** The row and the business change share one transaction: an order that is not
confirmed cannot leave a notification behind, and an order that *is* confirmed cannot fail to leave
one. No distributed transaction, no two-phase commit, no reconciliation between a database and a
broker.

**Retries with backoff.** `attempts` and `next_attempt_at` on the row; the sender takes what is due,
tries, and on failure schedules the next attempt further out. After the last attempt the row is
`FAILED` and stops being retried.

**A failure is visible, not silent.** `FAILED` rows are listed in the admin panel. They are not
announced by email, which would be circular — the one thing known to be broken is email.

## Token-bearing emails are the exception, and cannot be otherwise

Email verification and password reset carry a single-use token whose plaintext exists only in the
request that generated it: `verification_tokens` stores a SHA-256 hash (ADR-005), by design. Such a
message is **not reconstructible from the database**, so no scheduled sender can ever retry it.

Those notifications still get an outbox row — so a failure is recorded and surfaces in the same panel
list — but they are sent by the originating request, right after commit, and a failure is terminal.
The remedy already exists at the user's level: both flows have a "send it again" endpoint, which
issues a fresh token.

Storing the plaintext token to make them retryable was rejected outright. It would put a working
credential in a table, undoing the hashing that ADR-005 chose deliberately, to save a user one click.

---

# Alternatives considered

**A message broker (RabbitMQ or equivalent).** Rejected, for the reason ADR-008 and ADR-011 rejected
Redis, and for one more. First: the project runs a single PostgreSQL instance and has no other use for
a broker, whose operation, monitoring and failure modes are a permanent cost against a handful of
emails a day. Second, and decisive: **a broker does not solve the problem the outbox solves.**
Publishing to a broker from inside a database transaction is the same dual-write it was meant to fix —
the publish can succeed and the transaction roll back, or the reverse. The usual remedy is to put an
outbox in front of the broker, at which point the outbox is doing the work and the broker is a
delivery detail this project does not need.

**Send after commit, fire and forget.** Rejected: a provider outage lasting seconds destroys receipts
with no record and no recovery. The whole point of the requirement is that a committed order's email
eventually goes out.

**Store the rendered message in the outbox row.** Rejected on the PII grounds above. It would also
freeze the message at write time, so a template fix would not reach messages already queued.

**`LISTEN`/`NOTIFY` to dispatch immediately instead of polling.** Rejected as premature: it would cut
seconds off delivery for emails nobody is timing, and a `NOTIFY` is lost if no listener is connected,
so the polling loop is needed as a fallback anyway. The table and the scheduled drain are the whole
mechanism; this can be added later without changing either.

---

# Consequences

- Positive: a committed business change can never lose the email it owes, and an email can never
  describe a change that did not commit.
- Positive: no PII leaves the tables that already encrypt it, so the ADR-007 purge is unaffected and
  needs no knowledge of this table.
- Negative: delivery is delayed by up to one polling interval. Accepted — no notification here is
  time-critical to the second.
- Negative: rendering at send time means a message reflects the state *then*, not at the moment the
  event happened. For the notifications defined, that is either identical or an improvement.
- Negative: token-bearing emails have no automatic retry, by construction. Mitigated by the existing
  resend endpoints, and stated plainly rather than papered over.
- Operational: `notifications` grows and needs its `SENT` rows cleaned up, joining the cleanup task
  already pending for `refresh_tokens`, `verification_tokens` and `idempotency_keys`.

---

# References

- ADR-005 — PII protection: why the row holds references instead of content, and why the token cannot
  be stored.
- ADR-007 — Historical integrity and data lifecycle: the retention purge this decision keeps out of
  scope.
- ADR-011 — Idempotent money operations: the same refusal to persist rendered output, and the same
  rejection of extra infrastructure for a single use.
- `docs/features/notification.md` — the notification catalogue and the sender's behaviour.
