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

Read the ADR that governs the area being touched, not only the list above:
authentication and sessions (ADR-008), concurrent writes (ADR-009), admin actions
(ADR-010), checkout and payments (ADR-011), any personal data (ADR-005), product or
customer deactivation (ADR-007), catalog search (ADR-006).

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
