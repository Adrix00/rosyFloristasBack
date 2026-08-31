# ADR-012: API Error Contract

- **Status:** Accepted
- **Date:** 2026-08-22
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

`docs/architecture/04-rest-conventions.md` fixes resource naming, HTTP methods and versioning, but
says nothing about what a failed request returns. `infrastructure/web/advice` is empty, and the three
domain exceptions that exist (`CategoryNotFoundException`, `CategoryAlreadyExistsException`,
`CategoryInUseException`) carry only a message.

Two questions have to be answered before the feature specifications can describe any endpoint, because
every one of them has an error section:

1. What shape does an error response have?
2. How does a client tell two different failures with the same HTTP status apart? A `409` on checkout
   can mean "not enough stock", "the discount ran out of units", or "someone else changed this order"
   — three different things for the user interface to say, all sharing one status code.

---

# Decision

## Response format: RFC 7807

Errors are returned as `application/problem+json`, per RFC 7807. Spring Boot 3 implements it
natively (`ProblemDetail`), so this costs no wrapper code of our own.

Standard members: `type`, `title`, `status`, `detail`, `instance`. Two project extensions:

- `code` — the business error code (see below).
- `errors` — for validation failures, one entry per rejected field: `{ "field", "code" }`.

```json
{
  "type": "https://api.rosyfloristas.com/errors/validation",
  "title": "Validation failed",
  "status": 422,
  "detail": "El pedido no se puede crear",
  "instance": "/api/v1/orders",
  "code": "ORDER_VALIDATION_FAILED",
  "errors": [
    { "field": "deliveryDate", "code": "DATE_IN_PAST" }
  ]
}
```

`detail` is a message for a person and may be translated. `code` is for the client program and never
changes once published: renaming one is a breaking API change.

## Business error codes: one enum per module

Each module owns an enum of its own codes, next to its domain exceptions:

```
domain/exception/category/CategoryErrorCode
domain/exception/order/OrderErrorCode
```

Codes derive from the exception that raises them — `CategoryNotFoundException` carries
`CATEGORY_NOT_FOUND` — extending the naming already in use in `domain/exception/category/`. Every
domain exception exposes its code; an exception without one cannot be mapped and is a compile-time
omission rather than a runtime surprise.

A single shared enum listing every code in the application was rejected: it would couple all modules
to one class that every feature has to edit, which is the same problem ADR-003 avoids for ports.

**The enum lives in the domain and carries no HTTP.** A code is a plain identifier, so the domain
keeps its independence (`00-project-principles`, ADR-001). The mapping from exception to HTTP status
lives in `infrastructure/web/advice`, the only place that knows what a 404 is.

## Mapping

One `@RestControllerAdvice` translates:

| Origin | Status | `code` |
|---|---|---|
| Bean Validation failure | 422 | `<MODULE>_VALIDATION_FAILED` + `errors[]` |
| Domain exception "not found" | 404 | The exception's code |
| Domain exception "conflicting state" | 409 | The exception's code |
| Optimistic locking conflict (ADR-009) | 409 | `RESOURCE_MODIFIED` |
| Idempotency: operation in progress (ADR-011) | 409 | `OPERATION_IN_PROGRESS` |
| Idempotency: same key, different body (ADR-011) | 422 | `IDEMPOTENCY_KEY_REUSED` |
| Refresh token reuse detected (ADR-008) | 401 | `SESSION_REVOKED` |
| Expired token | 401 | `TOKEN_EXPIRED` |
| Unhandled exception | 500 | `INTERNAL_ERROR` |

Nothing internal leaks: no stack trace, no SQL, no constraint name. A violated
`uq_customers_email_hash` becomes `EMAIL_ALREADY_REGISTERED`, never the constraint's name — which
would disclose the schema and, worse, confirm that the address is registered.

A resource that exists but does not belong to the caller returns **404**, not 403: a 403 would
confirm the identifier exists. See `docs/features/00-security-validation-integrity.md`.

---

# Alternatives considered

**A wrapper of our own, `{ "error": { "code", "message", "details" } }`.** Rejected: it is what RFC
7807 already is, minus the interoperability, plus the serializers, the documentation and the tests we
would have to write ourselves. Spring Boot 3 ships `ProblemDetail` out of the box.

**One central `ErrorCode` enum in `shared/constant`.** Rejected: every feature would edit the same
file, and a module would be able to raise another module's codes.

**HTTP status only, with no business code.** Rejected: three unrelated `409`s on checkout would be
indistinguishable to the frontend.

**Error codes in `infrastructure`, out of the domain.** Rejected: the exception and its code are the
same fact, and separating them lets an exception exist with no mapping until something fails in
production.

---

# Consequences

- Positive: the frontend can react to a specific cause without parsing prose, and messages can be
  translated without breaking any client.
- Positive: codes come out of enums, so an unmapped exception is a compilation problem, not a 500.
- Negative: a published `code` is part of the API contract. Renaming one breaks clients; it needs the
  same care as changing a URL.
- Operational: the catalogue of published codes lives in `docs/api/`, generated from the enums, never
  maintained as a separate hand-written list that would drift.

---

# References

- `docs/architecture/04-rest-conventions.md` — naming, methods, versioning.
- `docs/architecture/06-validation-conventions.md` — how validation errors reach this format.
- ADR-001 — use case first: the domain does not know HTTP.
- ADR-003 — capability-based ports: the same reason there is no single shared enum.
- ADR-008, ADR-009, ADR-011 — the specific codes in the mapping table above.
