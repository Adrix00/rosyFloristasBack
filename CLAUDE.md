# CLAUDE.md

## Project

Rosy Floristas Backend

Java 21 + Spring Boot

This repository follows a strict DDD + Hexagonal Architecture.

Before modifying the code, always read the architectural documentation located at:

docs/architecture/

Mandatory documents:

- 00-project-principles.md
- ADR-001-use-case-first.md
- ADR-002-jpa-and-jdbc.md
- ADR-003-capability-based-ports.md
- ADR-004-reference-module-category.md

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
