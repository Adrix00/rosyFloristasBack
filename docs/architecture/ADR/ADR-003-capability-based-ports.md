# ADR-003: Capability-Based Output Ports

- **Status:** Accepted
- **Date:** 2026-08-02
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

La Arquitectura Hexagonal establece que la capa Application no debe depender de tecnologías externas.

Toda comunicación con sistemas externos debe realizarse mediante Ports (interfaces), siendo la Infrastructure la encargada de proporcionar las implementaciones.

Sin embargo, existen diferentes estrategias para definir dichos Ports.

Las más habituales son:

- Un único Repository por agregado.
- Un Port por cada operación.
- Agrupar varias capacidades relacionadas en un mismo Port.

El proyecto necesita una estrategia que mantenga un bajo acoplamiento sin generar una proliferación innecesaria de interfaces.

---

# Decision

Rosy Floristas utilizará **Capability-Based Output Ports**.

Cada Output Port representará una capacidad funcional del dominio.

No existirán repositorios genéricos con decenas de métodos.

Tampoco existirá una interfaz distinta para cada método.

Las capacidades relacionadas se agruparán en un único contrato.

---

# Design Principles

Los Ports describen **qué necesita el caso de uso**.

Nunca describen **cómo se implementa**.

Los nombres deben pertenecer al lenguaje del dominio.

Correcto:

```
CategoryReadPort

CategoryWritePort

CategoryExistencePort
```

Incorrecto:

```
CategoryRepository

JpaCategoryRepository

JdbcCategoryRepository

CategoryGateway

CategoryPersistenceService
```

Los detalles técnicos pertenecen exclusivamente a Infrastructure.

---

# Port Responsibilities

## Read Port

Responsable de obtener información.

Ejemplo:

```java
public interface CategoryReadPort {

    Optional<Category> findById(CategoryId id);

    List<Category> findAll();

}
```

Puede ser implementado mediante:

- JPA
- JDBC
- ambos

Application nunca lo conoce.

---

## Write Port

Responsable de persistir cambios.

Ejemplo:

```java
public interface CategoryWritePort {

    Category save(Category category);

    void delete(CategoryId id);

}
```

---

## Existence Port

Responsable de comprobar reglas de existencia.

Ejemplo:

```java
public interface CategoryExistencePort {

    boolean existsById(CategoryId id);

    boolean existsByName(CategoryName name);

}
```

---

# Why Not Repository?

Los repositorios genéricos suelen crecer con el tiempo.

Ejemplo:

```java
CategoryRepository

save()

update()

delete()

findAll()

findById()

findBySlug()

findByParent()

existsByName()

existsBySlug()

search()

findTree()

...
```

Este enfoque genera:

- baja cohesión
- alto acoplamiento
- interfaces difíciles de mantener

El proyecto evita este patrón.

---

# Why Not One Interface Per Method?

La alternativa extrema sería:

```
SaveCategoryPort

DeleteCategoryPort

FindCategoryByIdPort

ExistsCategoryByNamePort
```

Aunque respeta SRP, genera una cantidad excesiva de interfaces y dificulta la navegación del proyecto.

El equipo considera más equilibrado agrupar capacidades relacionadas.

---

# Package Structure

Los Output Ports se ubican en:

```
application
└── category
    └── port
        └── out
```

Ejemplo:

```
CategoryReadPort

CategoryWritePort

CategoryExistencePort
```

---

# Infrastructure

Infrastructure implementa estos contratos.

Ejemplo:

```
CategoryRepositoryAdapter
```

Puede depender internamente de:

```
JpaCategoryRepository

JdbcCategoryRepository
```

Application nunca conoce esta decisión.

---

# Communication Flow

```
Use Case

↓

Output Port

↓

Repository Adapter

↓

JPA / JDBC
```

Los casos de uso nunca conocen la tecnología utilizada.

---

# Benefits

## Explicit Dependencies

Cada Use Case depende únicamente de las capacidades que necesita.

---

## Better Testability

Los Ports son fáciles de simular mediante mocks.

---

## Lower Coupling

Las operaciones de lectura y escritura evolucionan de forma independiente.

---

## Better Readability

Las dependencias expresan claramente las necesidades del caso de uso.

Ejemplo:

```
CreateCategoryService

↓

CategoryWritePort

CategoryExistencePort
```

Sin necesidad de depender de un repositorio completo.

---

## Easier Maintenance

Las interfaces permanecen pequeñas y fáciles de entender.

---

# Trade-offs

Esta estrategia implica:

- más interfaces que un Repository tradicional
- necesidad de definir correctamente las capacidades

El equipo considera que esta complejidad adicional queda compensada por una arquitectura más limpia y mantenible.

---

# Forbidden Practices

No está permitido:

❌ Crear repositorios genéricos por agregado.

❌ Acceder directamente a JpaRepository desde Application.

❌ Acceder directamente a JdbcTemplate desde Application.

❌ Utilizar nombres tecnológicos en los Ports.

❌ Mezclar capacidades no relacionadas dentro del mismo Port.

---

# ArchUnit Enforcement

Las siguientes reglas protegerán esta decisión.

- Todos los Output Ports deben ser interfaces.
- Todos los Output Ports deben estar ubicados en `application/**/port/out`.
- Ningún Service puede depender directamente de clases de Infrastructure.
- Ningún Controller puede acceder a un Output Port.
- Todos los Adapters deben implementar al menos un Output Port.

---

# Consequences

## Positive

- Bajo acoplamiento.
- Alta cohesión.
- Interfaces pequeñas.
- Casos de uso más expresivos.
- Independencia tecnológica.
- Mejor mantenibilidad.

## Negative

- Mayor número de interfaces.
- Necesidad de definir correctamente las capacidades del dominio.

El equipo considera que estas desventajas son asumibles y contribuyen a una arquitectura más escalable.

---

# References

- Domain-Driven Design — Eric Evans
- Implementing Domain-Driven Design — Vaughn Vernon
- Clean Architecture — Robert C. Martin
- Hexagonal Architecture — Alistair Cockburn
