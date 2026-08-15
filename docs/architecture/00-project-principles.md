# Project Principles

> This document defines the immutable architectural principles of Rosy Floristas.
>
> Every new feature must comply with these rules.
>
> If any of these principles need to change, the architecture must be reviewed before implementing new functionality.

---

# 1. Architecture

The project follows:

- Domain-Driven Design (DDD)
- Hexagonal Architecture (Ports & Adapters)
- Modular Monolith

The Domain is the center of the application.

---

# 2. Dependency Direction

Dependencies always point towards the Domain.

```
Infrastructure
        │
        ▼
Application
        │
        ▼
Domain
```

The Domain never depends on any other project layer.

---

# 3. Single Responsibility

Every class has one responsibility.

Large service classes are not allowed.

Each business action is represented by an independent Use Case.

---

# 4. One Use Case = One Business Action

Examples:

- CreateCategory
- UpdateCategory
- DeleteCategory
- ChangeCategoryStatus
- GetCategory

Business actions must never be grouped into generic services.

---

# 5. Controllers

Controllers only:

- receive HTTP requests
- validate request format
- invoke a Use Case
- return HTTP responses

Controllers never contain business logic.

---

# 6. Domain

The Domain contains:

- business rules
- entities
- value objects
- domain services
- domain exceptions

The Domain never depends on:

- Spring
- JPA
- JDBC
- HTTP
- Infrastructure

---

# 7. Use Cases

Each Use Case consists of:

- one interface
- one implementation

Example:

CreateCategoryUseCase

↓

CreateCategoryService

---

# 8. Ports

Communication with external systems always occurs through Ports.

Output Ports are named by capability.

Examples:

SaveCategoryPort

FindCategoryByIdPort

ExistsCategoryByNamePort

Generic repositories must be avoided.

---

# 9. Persistence

The project combines JPA and JDBC.

JPA is used for:

- insert
- update
- delete
- aggregate persistence
- simple lookups

JDBC is used for:

- complex queries
- joins
- projections
- pagination
- optimized reads

The Application layer must never know which technology is used.

---

# 10. Mappers

Three mapper types exist.

Web Mapper

Request/Response ↔ Command/DTO

Application Mapper

Command/DTO ↔ Domain

Persistence Mapper

Domain ↔ Entity

Each mapper has a single responsibility.

---

# 11. Validation

Validation is performed in layers.

Web

Request format.

Application

Use Case validations.

Domain

Business rules.

Persistence

Database integrity.

Validation logic must never be duplicated.

---

# 12. Transactions

Transactions belong exclusively to the Application layer.

Writing Use Cases are transactional.

Reading Use Cases are not.

Infrastructure never starts transactions.

---

# 13. REST

Resources use plural nouns.

Examples:

/categories

/products

/orders

PATCH is used for business state changes.

DELETE permanently removes a resource.

Endpoints are versioned.

Example:

/api/v1/categories

---

# 14. Domain Events

Domain Events are only created when there is a real business consumer.

Events are not created "just in case".

---

# 15. ArchUnit

The architecture is protected through ArchUnit.

Architectural violations must fail the build.

Architecture rules are considered part of the source code.

---

# 16. Testing

Every new feature must include:

- Unit tests
- Architecture tests (when applicable)
- Integration tests (when persistence is involved)

---

# 17. Reference Implementation

Category is the reference implementation.

Every new module (Product, Order, Customer, etc.) must follow the same architectural conventions unless a documented Architectural Decision Record (ADR) explicitly states otherwise.

---

# 18. Evolution

When a new requirement cannot be implemented following these principles:

DO NOT introduce exceptions.

Instead:

1. Review the architecture.
2. Document the decision.
3. Update the architectural documentation.
4. Only then implement the change.

The architecture evolves intentionally, never accidentally.
