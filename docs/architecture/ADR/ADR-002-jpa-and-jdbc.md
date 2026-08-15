# ADR-002: Combined Persistence Strategy (JPA + JDBC)

- **Status:** Accepted
- **Date:** 2026-08-02
- **Decision Makers:** Rosy Floristas Development Team

---

# Context

Rosy Floristas es una aplicación de comercio electrónico.

No todas las operaciones sobre la base de datos tienen las mismas necesidades.

Existen dos grandes grupos de operaciones:

- Escritura y modificaciones de agregados.
- Consultas complejas para mostrar información al usuario.

Utilizar exclusivamente JPA simplifica el desarrollo, pero puede generar consultas poco eficientes para listados complejos.

Utilizar únicamente JDBC ofrece un mayor control sobre el SQL, pero incrementa considerablemente el código necesario para operaciones CRUD sencillas.

El objetivo es aprovechar las ventajas de ambas tecnologías manteniendo una única arquitectura.

---

# Decision

El proyecto utilizará una estrategia híbrida de persistencia.

Se combinarán:

- Spring Data JPA
- Spring JDBC

La elección de una tecnología u otra dependerá del tipo de operación, nunca del desarrollador que implemente la funcionalidad.

Esta decisión será uniforme en todo el proyecto.

---

# Responsibilities

## JPA

JPA será la tecnología por defecto para operaciones de escritura y persistencia de agregados.

Se utilizará para:

- INSERT
- UPDATE
- DELETE
- búsqueda por identificador
- búsqueda por claves únicas
- persistencia de agregados completos
- control de concurrencia mediante Optimistic Locking

Ejemplos:

```
Guardar una categoría

Modificar un producto

Eliminar un cliente

Buscar una categoría por UUID
```

---

## JDBC

Spring JDBC será la tecnología por defecto para consultas complejas.

Se utilizará para:

- listados
- filtros
- búsquedas avanzadas
- joins
- consultas optimizadas
- proyecciones
- paginación
- dashboards
- informes

Ejemplos:

```
Listado de productos filtrados

Listado paginado de pedidos

Búsqueda por texto

Productos destacados

Estadísticas
```

---

# Architectural Principle

La capa Application nunca conocerá la tecnología utilizada.

Desde Application únicamente existirán Output Ports.

Ejemplo:

```
FindCategoriesPort
```

La implementación decidirá internamente si utilizar:

- JPA
- JDBC
- una combinación de ambos

---

# Package Structure

La persistencia se organiza de la siguiente forma.

```
infrastructure
└── persistence
    ├── adapter
    │
    ├── entity
    │
    ├── mapper
    │
    ├── jpa
    │   └── repository
    │
    └── jdbc
        ├── repository
        ├── projection
        └── rowmapper
```

Cada paquete tiene una responsabilidad concreta.

---

# Adapter

El Adapter implementa los Output Ports definidos por Application.

Ejemplo:

```
CategoryRepositoryAdapter
```

El Adapter puede utilizar internamente:

- uno o varios repositorios JPA
- uno o varios repositorios JDBC

Application nunca conoce esta decisión.

---

# JPA Layer

Contiene únicamente elementos relacionados con JPA.

Ejemplo:

```
JpaCategoryRepository
```

Utiliza:

- Spring Data JPA
- EntityManager (cuando sea necesario)
- Entity

No contiene lógica de negocio.

---

# JDBC Layer

Contiene únicamente componentes JDBC.

Ejemplo:

```
JdbcCategoryRepository

CategoryProjection

CategoryRowMapper
```

Las consultas SQL complejas permanecen aisladas del resto del proyecto.

---

# Entity

Las entidades JPA existen únicamente para persistencia.

Nunca abandonan Infrastructure.

Nunca son utilizadas por:

- Controllers
- Services
- Domain

---

# Domain

El Domain únicamente trabaja con objetos de dominio.

Nunca conoce:

- Entity
- ResultSet
- JdbcTemplate
- JpaRepository

---

# Mappers

Existen tres tipos de mappers.

## Application Mapper

Convierte entre:

```
Command

↓

Domain

↓

DTO
```

---

## Persistence Mapper

Convierte entre:

```
Domain

↓

Entity

↓

Domain
```

---

## JDBC RowMapper

Convierte entre:

```
ResultSet

↓

Projection
```

o

```
ResultSet

↓

Domain
```

cuando sea necesario.

Cada mapper tiene una única responsabilidad.

---

# Example

Solicitud HTTP

↓

Controller

↓

Use Case

↓

Output Port

↓

CategoryRepositoryAdapter

↓

¿Es escritura?

↓

Sí

↓

JPA

---

¿Es lectura compleja?

↓

Sí

↓

JDBC

---

La decisión es completamente transparente para Application.

---

# Benefits

Esta estrategia proporciona:

## Simplicidad

Las operaciones CRUD requieren muy poco código.

---

## Rendimiento

Las consultas complejas pueden optimizarse manualmente.

---

## Escalabilidad

Cada nueva funcionalidad seguirá exactamente el mismo patrón.

---

## Mantenibilidad

Las consultas SQL quedan aisladas del resto del sistema.

---

## Independencia

Application no depende de ninguna tecnología de persistencia.

---

# Trade-offs

Esta estrategia implica:

- mayor número de clases
- coexistencia de dos tecnologías
- necesidad de mantener convenciones claras

El equipo considera que estas desventajas son asumibles frente a la mejora en mantenibilidad y rendimiento.

---

# Forbidden Practices

No está permitido:

❌ Utilizar JdbcTemplate desde Application.

❌ Utilizar Entity JPA fuera de Infrastructure.

❌ Utilizar ResultSet fuera del paquete JDBC.

❌ Acceder directamente a JpaRepository desde un Controller.

❌ Mezclar SQL dentro de un Service.

❌ Utilizar JDBC para operaciones CRUD simples.

❌ Utilizar JPA para consultas complejas únicamente por comodidad.

---

# ArchUnit Enforcement

Las siguientes reglas protegerán esta decisión.

- Ninguna clase fuera de Infrastructure puede depender de `jakarta.persistence`.
- Ninguna clase fuera del paquete JDBC puede depender de `JdbcTemplate`.
- Ninguna Entity puede utilizarse fuera de Infrastructure.
- Ningún Controller puede acceder a repositorios JPA o JDBC.
- Todo acceso a persistencia debe realizarse mediante Output Ports.

---

# Consequences

## Positive

- Excelente rendimiento en consultas.
- Persistencia desacoplada.
- Arquitectura consistente.
- Separación clara entre escritura y lectura.
- Flexibilidad para evolucionar la persistencia.

## Negative

- Mayor curva de aprendizaje.
- Más clases de infraestructura.
- Dos tecnologías que mantener.

El equipo considera que estas desventajas quedan ampliamente compensadas por la mejora en claridad arquitectónica y rendimiento.

---

# References

- Domain-Driven Design — Eric Evans
- Implementing Domain-Driven Design — Vaughn Vernon
- Spring Data JPA Documentation
- Spring JDBC Documentation
- High Performance Java Persistence — Vlad Mihalcea
