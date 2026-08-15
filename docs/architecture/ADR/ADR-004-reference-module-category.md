# ADR-004: Category as the Reference Module

- **Status:** Accepted
- **Date:** 2026-08-02
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

Before implementing the business logic of Rosy Floristas, the project requires a complete reference implementation.

This reference module must define:

- project structure
- package organization
- naming conventions
- communication between layers
- persistence strategy
- REST conventions
- validation strategy
- testing strategy

The objective is to avoid making architectural decisions repeatedly for every new functionality.

---

# Decision

The **Category** module is designated as the project's reference implementation.

It is the first business functionality to be developed.

Every subsequent module must follow the same architectural conventions unless a new Architectural Decision Record (ADR) explicitly states otherwise.

Examples:

- Product
- Customer
- Order
- User
- Address
- Shopping Cart

All of them will replicate the same architectural structure.

---

# Goals

Category is not only a functional module.

It is also responsible for validating the architecture before the project grows.

The implementation must demonstrate:

- DDD
- Hexagonal Architecture
- Use Case First
- Combined Persistence (JPA + JDBC)
- Capability-Based Output Ports
- REST conventions
- Validation rules
- Testing strategy

---

# Development Order

Every module follows exactly the same implementation sequence.

## 1. REST API

Design endpoints.

Example:

```
POST    /api/v1/categories

GET     /api/v1/categories

GET     /api/v1/categories/{id}

PUT     /api/v1/categories/{id}

PATCH   /api/v1/categories/{id}/status

DELETE  /api/v1/categories/{id}
```

---

## 2. Requests

Create request objects.

Example:

```
CreateCategoryRequest

UpdateCategoryRequest

ChangeCategoryStatusRequest
```

---

## 3. Responses

Create response objects.

Example:

```
CategoryResponse

CategorySummaryResponse
```

---

## 4. Commands

Create commands representing business actions.

Example:

```
CreateCategoryCommand

UpdateCategoryCommand

ChangeCategoryStatusCommand
```

---

## 5. Queries

Create query objects.

Example:

```
GetCategoryQuery

GetCategoriesQuery
```

---

## 6. Use Cases

Define interfaces.

Example:

```
CreateCategoryUseCase
```

---

## 7. Services

Implement business orchestration.

Example:

```
CreateCategoryService
```

---

## 8. Output Ports

Define persistence capabilities.

Example:

```
CategoryReadPort

CategoryWritePort

CategoryExistencePort
```

---

## 9. Domain

Implement:

- Entity
- Value Objects
- Domain Rules
- Domain Exceptions

The Domain must remain independent from all frameworks.

---

## 10. Persistence

Implement:

- Repository Adapter
- Persistence Mapper
- JPA Repository
- JDBC Repository
- Projection
- RowMapper
- Entity

---

## 11. Testing

Finally:

- Unit Tests
- Integration Tests
- Architecture Tests

---

# Package Structure

Every module follows the same package layout.

```
application
└── category
    ├── command
    ├── query
    ├── dto
    ├── mapper
    ├── port
    │   ├── in
    │   └── out
    └── service

domain
└── model
    └── category

infrastructure
├── web
├── persistence
└── ...
```

No module may introduce a different organization without a documented ADR.

---

# REST Conventions

Every module follows the same REST rules.

Examples:

```
POST

GET

PUT

PATCH

DELETE
```

PATCH is reserved for business state transitions.

DELETE permanently removes the resource.

---

# Validation Strategy

Validation responsibilities remain identical in every module.

Web

↓

Application

↓

Domain

↓

Persistence

Validation must never be duplicated.

---

# Persistence Strategy

Every module follows ADR-002.

Simple writes

↓

JPA

Complex reads

↓

JDBC

The Application layer never knows which technology is used.

---

# Naming Conventions

Every module follows ADR-003.

Examples:

```
CreateProductUseCase

CreateProductService

ProductReadPort

ProductWritePort
```

No module may introduce alternative naming conventions.

---

# Testing Strategy

Every module must include:

- Unit Tests
- Integration Tests
- Architecture Tests (when applicable)

Quality rules are enforced through:

- Checkstyle
- SpotBugs
- PMD (future)
- ArchUnit
- CodeQL
- Dependabot

---

# Benefits

Using Category as the reference module provides:

## Architectural Consistency

Every module follows the same rules.

---

## Faster Development

Developers replicate an existing pattern instead of inventing new ones.

---

## Better Onboarding

New contributors only need to understand one module.

---

## Easier Maintenance

The project evolves with a single architectural style.

---

## AI-Friendly Development

AI assistants can generate new modules using the Category module as a template.

This minimizes architectural drift.

---

# Forbidden Practices

It is not allowed to:

❌ Create a new module with a different package structure.

❌ Introduce different naming conventions.

❌ Skip architectural layers.

❌ Access Infrastructure directly from Controllers.

❌ Bypass Output Ports.

❌ Duplicate business logic between modules.

---

# ArchUnit Enforcement

The following rules protect this decision.

- Every module must respect the global package structure.
- Controllers depend only on Use Cases.
- Services implement Use Cases.
- Output Ports are interfaces.
- Infrastructure implements Output Ports.
- Domain never depends on Infrastructure.
- Domain never depends on Spring.

Any violation must fail the build.

---

# Future Evolution

Future modules must evolve from the reference implementation.

If a module requires a different architecture:

1. Create a new ADR.
2. Review the architectural impact.
3. Update the documentation.
4. Implement the change.

Architecture evolves intentionally.

---

# Consequences

## Positive

- Uniform architecture.
- Predictable codebase.
- Easier reviews.
- Easier testing.
- Better documentation.
- Better AI code generation.
- Reduced onboarding time.

## Negative

- The first module requires more design effort.
- Architectural mistakes in the reference module would propagate.

For this reason, Category must be reviewed carefully before implementing additional business modules.

---

# References

- ADR-001: Use Case First Architecture
- ADR-002: Combined Persistence Strategy (JPA + JDBC)
- ADR-003: Capability-Based Output Ports
- Project Principles
