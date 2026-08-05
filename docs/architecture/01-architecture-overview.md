# Architecture Overview

## Architecture

Rosy Floristas follows a Domain-Driven Design (DDD) architecture combined with Hexagonal Architecture (Ports & Adapters).

The project is implemented as a modular monolith.

## Main Layers

- Application
- Domain
- Infrastructure
- Shared

Dependencies are always directed towards the Domain.

Infrastructure depends on Application.

Application depends on Domain.

Domain never depends on any other project layer.

## Main Principles

- Business logic belongs to the Domain.
- Use Cases orchestrate business operations.
- Infrastructure contains technical implementations.
- Controllers never contain business logic.
- Repositories are hidden behind Output Ports.
- Every use case has a single responsibility.
- Communication between layers is performed using DTOs, Commands and Queries.
