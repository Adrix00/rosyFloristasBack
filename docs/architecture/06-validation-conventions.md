# Validation Conventions

How validation is performed. **What** is validated in this domain — the concrete business rules —
lives in `docs/features/00-security-validation-integrity.md`.

The error format that a failed validation produces is defined in
[ADR-012](ADR/ADR-012-api-error-contract.md).

---

## Two layers, two responsibilities

| Layer | Validates | Example |
|---|---|---|
| Request DTO | Shape | The email is syntactically an email |
| Service (use case) | Business rule | That email is not already registered |

The controller never contains a business rule, and the service never re-checks what the DTO already
guarantees. A rule that needs to consult the database, another aggregate or the current state of a
resource belongs to the service, always.

---

## Request DTOs

Jakarta Bean Validation, with `@Valid` on the controller parameter.

```java
public record CreateCategoryRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 2000) String description) {}
```

Rules:

- Every text field carries `@Size` with the same limit as its database column. A mismatch turns a
  clean 422 into a 500 raised by PostgreSQL.
- Every collection carries a maximum size.
- Every paginated query carries a maximum page size.
- Nullability is explicit: `@NotNull` where the field is required, nothing where it is optional. An
  unannotated required field is a bug, not a shortcut.

---

## Custom validators

When a rule cannot be expressed with a standard annotation and depends only on the value itself, it
becomes an annotation of its own in `shared/validation`:

```java
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface PhoneNumber { ... }
```

A validator that needs a port, a repository or the current state of an aggregate is **not** a custom
annotation: it is a business rule, and it belongs to the service. A Bean Validation constraint that
queries the database hides an I/O call inside what looks like an annotation.

---

## Domain validation

Value objects validate themselves on construction and are impossible to build in an invalid state:

```java
public record Slug(String value) {
  public Slug {
    Objects.requireNonNull(value);
    if (!PATTERN.matcher(value).matches()) {
      throw new InvalidSlugException(value);
    }
  }
}
```

This is the last line of defence, not the first: it protects the invariant no matter which path
reaches it, including a use case invoked from a scheduled task with no HTTP involved.

---

## Normalization comes before validation

Email and phone are normalized — lower-cased, trimmed, international prefix applied — **before** they
are validated, encrypted, or hashed with HMAC. Skipping this makes `Ana@X.com` and `ana@x.com` two
different HMACs, and `uq_customers_email_hash` stops preventing the duplicate it exists to prevent.

---

## What is never trusted

Validation happens on the server, always. A check in the frontend is a convenience for the user, not
a guarantee for the backend.

Never trusted, whatever the client says: identifiers of resources belonging to someone else, prices,
totals, discounts, stock, roles, and the S3 key of an upload. Money is recalculated in the backend
from the catalog; the client's number is only used to detect a mismatch and reject the request.

---

## Database constraints

The database is the only layer that cannot be bypassed. Anything expressible as a `CHECK`, `UNIQUE`,
`EXCLUDE` or foreign key lives there, and Java validation exists to produce a decent error message,
never to replace the constraint.

Constraint violations are translated to a business code (ADR-012); the constraint's name never
reaches the client.

Invariants the database cannot express — those spanning several tables or rows — are listed in
`docs/features/00-security-validation-integrity.md`, each with the single transactional write path
that upholds it.
