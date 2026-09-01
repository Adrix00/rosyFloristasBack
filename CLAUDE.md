# CLAUDE.md

## Project

Rosy Floristas Backend

Java 21 + Spring Boot

This repository follows a strict DDD + Hexagonal Architecture.

Before modifying the code, always read the relevant architectural documentation located at:

docs/architecture/

Mandatory documents:

- docs/architecture/00-project-principles.md
- docs/architecture/ADR/ADR-001-use-case-first.md
- docs/architecture/ADR/ADR-002-jpa-and-jdbc.md
- docs/architecture/ADR/ADR-003-capability-based-ports.md
- docs/architecture/ADR/ADR-004-reference-module-category.md
- docs/architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md
- docs/architecture/ADR/ADR-006-postgres-search-instead-of-elasticsearch.md
- docs/architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md
- docs/architecture/ADR/ADR-008-refresh-token-rotation.md
- docs/architecture/ADR/ADR-009-optimistic-locking.md
- docs/architecture/ADR/ADR-010-admin-audit-log.md
- docs/architecture/ADR/ADR-011-idempotent-money-operations.md
- docs/architecture/ADR/ADR-012-api-error-contract.md
- docs/architecture/ADR/ADR-013-inventory-alerts.md
- docs/architecture/ADR/ADR-014-purchase-receiving-and-reversal.md
- docs/architecture/ADR/ADR-015-transactional-outbox-for-notifications.md

Read the ADR that governs the area being touched, not only the list above:
authentication and sessions (ADR-008), concurrent writes (ADR-009), admin actions
(ADR-010), checkout and payments (ADR-011), any personal data (ADR-005), product or
customer deactivation (ADR-007), catalog search (ADR-006), any API error response
(ADR-012), inventory alerts and scheduled jobs (ADR-013), purchase receiving and
reversal (ADR-014), anything that sends an email (ADR-015).

---

## Rules

Never ignore the ADRs.

Never introduce architectural changes without explaining why.

If an implementation conflicts with an ADR:

STOP.

Explain the conflict before writing code.

---

## Development workflow

Always implement new functionality in this order:

1. REST API
2. Request DTOs
3. Response DTOs
4. Commands
5. Queries
6. Input Ports (UseCases)
7. Services
8. Output Ports
9. Domain
10. Persistence
11. Tests

Never skip steps.

---

## Coding Style

- Java 21
- Spring Boot
- Constructor Injection only
- No field injection
- Small classes
- Small methods
- Expressive naming
- No wildcard imports
- No commented code
- No dead code
- No utility classes unless explicitly justified

---

## Logging

Every method with real logic (a decision, a branch, an I/O call, a state transition) logs its
entry and its exit, via SLF4J (`LoggerFactory.getLogger(Xxx.class)`), at `DEBUG`:

- Entry: every input parameter — `log.debug("createCategory name={} imageId={} position={}",
  command.name(), command.imageId(), command.position())`.
- Exit: the return value (or "void" / the outcome) — `log.debug("createCategory -> id={}",
  category.id())`.

**Never log a field encrypted or hashed under ADR-005**: email, phone, name, surnames, addresses,
delivery recipient, card message, payment tokens, password hashes, session/verification tokens.
Log the identifier (UUID) instead, never the value. Everything else — names, statuses, slugs,
prices, quantities, non-PII business fields — logs freely, no redaction needed.

Applies to: Services (one `execute()` per use case), Controllers (one per endpoint), Persistence
Adapters and JDBC Repositories (DB I/O), domain methods that mutate or create state, and
validators. Log a complex branch or a non-obvious decision inside a method body too, not only at
entry/exit.

**Does not apply to** — logging here is pure noise, not signal: Commands/Queries/DTOs/Requests/
Responses (data carriers, no logic), Web/Persistence Mappers and RowMappers (pure 1:1 field
mapping, called once per row or per request — can't fail independently of their input), and plain
accessors. These still get Javadoc.

A caught, expected domain exception (404/409/422) logs at `DEBUG` in the handler that catches it,
never at the throw site (double-logs the same event otherwise). An unexpected exception logs at
`ERROR` with the full exception before the generic response is built
(`infrastructure/web/advice`).

A security-relevant startup decision (e.g. a permissive placeholder `SecurityConfig`) logs once at
`WARN` on startup — cheap, and it is the one signal an operator has that the posture is
provisional.

**Sanitizing request-controlled text before it reaches a log call (CodeQL `java/log-injection`,
CWE-117).** A `@PathVariable String` (e.g. `idOrSlug`) or `request.getRequestURI()` is
user-controlled and must never reach `LOGGER.debug`/`LOGGER.error` raw — wrap it with
`org.owasp.encoder.Encode.forJava(value)` **inline, at the log call itself**:

```java
LOGGER.debug("GET /products/{}", Encode.forJava(idOrSlug));
```

`shared.util.LogSanitizer.sanitize(...)` (also backed by `Encode.forJava`) is the right choice for
every other case — a request-body field like `name` or `description` logged inside a Service — but
CodeQL's log-injection sanitizer recognition does not trace taint through a helper method call, only
through a literal call on the tainted expression at the log site itself. Confirmed empirically on
`feature/category` (PR #8): a CR/LF-stripping regex, `Matcher#replaceAll`, and `LogSanitizer` as a
wrapper were all tried first and none cleared the alert; only inlining `Encode.forJava` directly at
each flagged log call did. A UUID, an int or an enum never needs this — it cannot carry a newline or
a control character.

A UUID/enum-typed `@PathVariable` (`id`) is not tainted the same way and needs no wrapping.

**CSRF is enabled**, `SecurityConfig` stays `SessionCreationPolicy.STATELESS` (JWT Bearer auth,
ADR-008 — no `HttpSession`, so the CSRF token cannot live server-side). It uses
`CookieCsrfTokenRepository.withHttpOnlyFalse()`: the token travels in a JS-readable `XSRF-TOKEN`
cookie the SPA reads and echoes back as an `X-XSRF-TOKEN` header on every mutating request (axios
does this automatically; a hand-rolled `fetch` client must read the cookie and set the header
itself). `CsrfCookieFilter` (nested in `SecurityConfig`) forces the token to be read — and its
cookie written — on every request, not only ones that happen to touch it. `GET`/`HEAD`/`OPTIONS`
are exempt by Spring's own default; every `POST`/`PUT`/`PATCH`/`DELETE` needs the header.

Real value today is defense-in-depth (nothing currently rides a cookie — Bearer-only — so no
concrete exploit closes), but it becomes load-bearing once `auth.md`'s `SameSite=Strict` refresh
token cookie exists, and it satisfies CodeQL's `java/spring-disabled-csrf-protection` rule
directly instead of carrying it as a documented false positive.

**Every `@WebMvcTest` controller test's mutating `MockMvc` request needs `.with(csrf())`**
(`org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf`)
or it 403s against the real `SecurityConfig` bean the test `@Import`s — `GET` requests are exempt
and need nothing.

---

## Javadoc

Every method — interface and implementation, public and private — gets a Javadoc comment: what it
does, `@param` per parameter, `@return` if non-void, `@throws` for a checked or a documented
business exception. Records document their fields via `@param` on the record's own Javadoc instead
of on the (generated) accessors.

---

## Architecture

DDD

Hexagonal Architecture

Use Case First

Capability-Based Output Ports

JPA + JDBC

Modular Monolith

---

## Project Documentation

The `docs/` directory is an Obsidian vault. `docs/architecture/` and `docs/releases/` are versioned in Git; `docs/database/`, `docs/features/` and `docs/api/` are excluded by `.gitignore` and live only in the local vault. All of them are equally authoritative — being untracked does not make a document optional.

The documentation in `docs/` is the source of truth for:

- Backend architecture
- Database structure and design
- API contracts
- Backend features
- Frontend-facing functionality
- Domain decisions
- Development specifications
- Architectural decisions

Before implementing or modifying functionality, consult the relevant documentation in `docs/`.

### Documentation structure

- `docs/architecture/` — architectural rules and conventions
- `docs/architecture/ADR/` — Architecture Decision Records
- `docs/database/` — database structure and design
- `docs/domain/` — domain concepts and business rules
- `docs/features/` — feature specifications and functional requirements
- `docs/api/` — API contracts and endpoint definitions
- `docs/releases/` — release and development notes

### Documentation rules

Documentation must be updated when a code change modifies or establishes:

- Architecture
- Database structure
- API contracts
- Domain behaviour
- Business rules
- Feature requirements
- Important technical decisions

Do not modify documentation for trivial implementation changes that do not affect the documented design.

When a change contradicts existing documentation:

STOP.

Explain the conflict before modifying the code or documentation.

When a new architectural decision is required, create or update the appropriate ADR before implementing the change.

Documentation changes must reflect the final implemented behaviour, not planned behaviour that has not been implemented.

Never invent requirements or architectural decisions that are not present in the documentation or explicitly requested by the user.

---

## Persistence

JPA

- insert
- update
- delete
- simple queries

JDBC

- filters
- joins
- pagination
- projections
- reporting

---

## Domain

The Domain must never depend on:

- Spring
- JPA
- JDBC
- Infrastructure
- HTTP

---

## Controllers

Controllers only:

- receive HTTP requests
- validate requests
- invoke UseCases
- return responses

Never implement business logic.

---

## Services

Each Service implements one UseCase.

Never create generic services.

---

## Ports

Ports describe business capabilities.

Do not create generic repositories.

---

## Testing

Generated code must satisfy:

- Checkstyle
- SpotBugs
- ArchUnit
- CodeQL

Compilation must succeed without warnings whenever possible.

---

## If you are unsure

Do not guess.

Explain the alternatives and wait for confirmation.

---

## Build & Verification

`mvn verify` runs the full validation chain and must pass before considering any change done:

- Checkstyle (`validate` phase)
- Tests, including ArchUnit rules (`test` phase)
- SpotBugs (`verify` phase)

Individual checks:

- `mvn checkstyle:check` — style only
- `mvn test` — unit/ArchUnit tests only
- `mvn spotbugs:check` — static analysis only (requires prior compile)

Never skip `mvn verify` before declaring a task complete.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
