# ADR-001: Use Case First Architecture

- **Status:** Accepted
- **Date:** 2026-08-02
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

Rosy Floristas sigue una arquitectura basada en Domain-Driven Design (DDD) y Arquitectura Hexagonal.

Uno de los problemas más habituales en proyectos Spring Boot es la aparición de clases `Service` muy grandes que acumulan decenas de operaciones de negocio:

```java
CategoryService

- createCategory()
- updateCategory()
- deleteCategory()
- activateCategory()
- deactivateCategory()
- findCategory()
- findAllCategories()
- ...
```

Con el paso del tiempo estas clases terminan incumpliendo el Principio de Responsabilidad Única (SRP), dificultan el mantenimiento y aumentan el acoplamiento entre funcionalidades.

El objetivo del proyecto es que cada operación de negocio sea completamente independiente del resto.

---

# Decision

El proyecto adopta una arquitectura **Use Case First**.

Cada acción de negocio será modelada como un caso de uso independiente.

Cada caso de uso estará compuesto por:

- una interfaz (`UseCase`)
- una implementación (`Service`)

Ejemplo:

```
CreateCategoryUseCase
CreateCategoryService
```

```
UpdateCategoryUseCase
UpdateCategoryService
```

```
DeleteCategoryUseCase
DeleteCategoryService
```

Cada implementación será responsable exclusivamente de una única operación de negocio.

---

# Package Structure

Los casos de uso se agrupan por funcionalidad.

Ejemplo:

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
```

Las interfaces de entrada (`UseCase`) estarán ubicadas en:

```
application/category/port/in
```

Las implementaciones estarán ubicadas en:

```
application/category/service
```

---

# Responsibilities

## UseCase

Define el contrato de la operación.

No contiene implementación.

Ejemplo:

```java
public interface CreateCategoryUseCase {

    CategoryDto execute(CreateCategoryCommand command);

}
```

---

## Service

Implementa el caso de uso.

Puede:

- coordinar repositorios
- validar reglas de aplicación
- abrir transacciones
- publicar eventos
- invocar el dominio

No debe contener lógica técnica relacionada con HTTP o persistencia.

---

## Domain

El dominio contiene únicamente reglas de negocio.

Nunca conoce:

- Spring
- HTTP
- Controllers
- Repositories
- JPA
- JDBC

---

# Benefits

Esta arquitectura proporciona las siguientes ventajas.

## Single Responsibility

Cada clase tiene una única responsabilidad.

---

## Low Coupling

Los casos de uso no dependen entre sí.

Cada operación puede evolucionar de forma independiente.

---

## High Cohesion

Todo el código relacionado con una operación concreta permanece unido.

---

## Easier Testing

Cada caso de uso puede probarse de forma completamente aislada.

---

## Better Readability

La estructura del proyecto refleja directamente el lenguaje del negocio.

Ejemplo:

```
CreateCategory

↓

CreateCategoryUseCase

↓

CreateCategoryService
```

No existe ambigüedad sobre dónde implementar una nueva funcionalidad.

---

## Easier Refactoring

Modificar una operación no afecta al resto del sistema.

---

# Design Rules

Cada Use Case debe cumplir las siguientes reglas.

- Representa una única acción de negocio.
- Tiene una única implementación.
- No depende de Controllers.
- No depende de Infrastructure.
- No conoce Entity JPA.
- No utiliza JdbcTemplate.
- Solo interactúa con el exterior mediante Output Ports.

---

# Transactions

Las transacciones pertenecen al caso de uso.

Ejemplo:

```java
@Transactional
public class CreateCategoryService implements CreateCategoryUseCase
```

Nunca se abrirán transacciones en:

- Controllers
- Repositories
- Adapters

---

# Communication Flow

Todas las operaciones seguirán el mismo flujo.

```
HTTP Request
        │
        ▼
Controller
        │
        ▼
Request
        │
        ▼
Command / Query
        │
        ▼
UseCase
        │
        ▼
Service
        │
        ▼
Output Port
        │
        ▼
Infrastructure
        │
        ▼
Persistence
```

La respuesta sigue el camino inverso.

```
Persistence

↓

Domain

↓

DTO

↓

Response

↓

HTTP Response
```

---

# Architectural Constraints

Quedan prohibidos los siguientes patrones.

## Generic Services

Incorrecto

```
CategoryService

- create()

- update()

- delete()

- activate()

- deactivate()

- list()

- search()
```

---

## Business Logic in Controllers

Los Controllers únicamente:

- reciben peticiones
- validan el formato del Request
- invocan un Use Case
- devuelven un Response

---

## Direct Repository Access

Los Controllers nunca acceden directamente a la persistencia.

Toda operación debe pasar por un Use Case.

---

# ArchUnit Enforcement

Las siguientes reglas de ArchUnit protegerán esta decisión.

- Todo `*UseCase` debe ser una interfaz.
- Todo `*Service` debe implementar un `*UseCase`.
- Ningún Controller puede depender de un Repository.
- Ningún Service puede depender de un Controller.
- Ningún Service puede acceder directamente a Entity JPA.

Estas reglas forman parte de la arquitectura del proyecto y cualquier incumplimiento provocará el fallo de la compilación.

---

# Consequences

## Positive

- Arquitectura consistente.
- Clases pequeñas.
- Bajo acoplamiento.
- Alta cohesión.
- Mayor facilidad para escribir pruebas.
- Escalabilidad del proyecto.
- Facilita el trabajo de nuevos desarrolladores y asistentes de IA.

## Negative

- Mayor número de clases.
- Más interfaces que en una arquitectura tradicional.

El equipo considera que este incremento en el número de clases es un coste asumible frente a las ventajas obtenidas en mantenibilidad y claridad arquitectónica.

---

# References

- Domain-Driven Design — Eric Evans
- Implementing Domain-Driven Design — Vaughn Vernon
- Clean Architecture — Robert C. Martin
- Ports & Adapters (Hexagonal Architecture) — Alistair Cockburn
