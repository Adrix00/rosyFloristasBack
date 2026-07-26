# Rosy Floristas - Architecture Handbook

> Versión: 1.0  
> Arquitectura: Monolito Modular + DDD + Arquitectura Hexagonal

## Índice

1. Visión
2. Objetivos
3. Arquitectura general
4. Principios
5. Dominios
6. Estructura de un dominio
7. Use Case First
8. Commands & Queries
9. Puertos
10. Dominio
11. Infraestructura
12. Repositorios
13. Mappers
14. Validaciones
15. Eventos de dominio
16. Persistencia
17. Seguridad
18. Configuración
19. Testing
20. Convenciones
21. Roadmap

---

# 1. Visión

Rosy Floristas será un **monolito modular** construido con **Domain-Driven Design (DDD)** y **Arquitectura Hexagonal (Ports & Adapters)**.

El objetivo es mantener un dominio completamente independiente de Spring Boot, JPA y cualquier tecnología de infraestructura.

---

# 2. Objetivos

- Bajo acoplamiento.
- Alta cohesión.
- Código mantenible.
- Escalabilidad.
- Preparación para microservicios.
- Testabilidad.
- Seguridad desde el diseño.

---

# 3. Arquitectura General

```text
Internet
   │
Cloudflare
   │
 HTTPS
   │
 Nginx
 ├───────────────┐
 │               │
React        Spring Boot
 │               │
 └──────┬────────┘
        │
   PostgreSQL
        │
    Amazon S3
```

Frontend:
- Repositorio: `rosyFloristasFront`
- React + Vite

Backend:
- Repositorio: `rosyFloristasBack`
- Spring Boot + Java

---

# 4. Principios Arquitectónicos

- Monolito Modular
- Domain-Driven Design
- Arquitectura Hexagonal
- Use Case First
- SOLID
- DRY
- KISS
- YAGNI
- Fail Fast
- Security by Design

---

# 5. Dominios

```text
auth
users
products
flowers
bouquets
orders
cart
inventory
payments
images
administration
```

Cada dominio es autónomo.

---

# 6. Organización de un dominio

```text
users
│
├── application
│   ├── command
│   ├── query
│   ├── dto
│   ├── mapper
│   ├── port
│   │   ├── in
│   │   └── out
│   └── usecase
│
├── domain
│   ├── model
│   ├── valueobject
│   ├── service
│   ├── event
│   └── exception
│
└── infrastructure
    ├── controller
    ├── persistence
    ├── email
    ├── storage
    ├── payment
    ├── event
    ├── security
    ├── listener
    └── configuration
```

---

# 7. Use Case First

No existirán clases `UserService`, `ProductService`, etc.

Cada operación será un caso de uso independiente.

Interfaces:

```java
public interface RegisterUserUseCase {
    UserResponse execute(RegisterUserCommand command);
}
```

Implementación:

```java
public class RegisterUserUseCaseImpl
        implements RegisterUserUseCase {
}
```

Los casos de uso se agruparán por funcionalidad.

---

# 8. Commands & Queries

Todos los Commands y Queries serán **records**.

```java
public record RegisterUserCommand(
    String email,
    String password,
    String firstName,
    String lastName
){}
```

Nunca se pasarán DTOs directamente al caso de uso.

---

# 9. Puertos

## Entrada

Interfaces de casos de uso:

```text
application/port/in
```

## Salida

```text
UserRepository
EmailGateway
PasswordEncoder
EventPublisher
PaymentGateway
FileStorageGateway
ClockProvider
UUIDGenerator
```

Los casos de uso dependen exclusivamente de estos contratos.

---

# 10. Dominio

El dominio únicamente contendrá:

```text
model
valueobject
service
event
exception
```

No conocerá:

- Spring
- JPA
- PostgreSQL
- Email
- JWT
- HTTP

---

# 11. Infraestructura

Implementaciones técnicas:

- JpaRepository
- SMTP
- Amazon S3
- Argon2
- Spring Events
- Stripe/Redsys
- Controllers

---

# 12. Repositorios

Interfaces:

```text
application/port/out/UserRepository
```

Implementaciones:

```text
infrastructure/persistence/JpaUserRepository
```

---

# 13. Mappers

Todo mapeo estará desacoplado.

Ejemplos:

- RegisterUserRequestMapper
- RegisterUserCommandMapper
- UserEntityMapper
- UserResponseMapper

---

# 14. Validaciones

Controller:
- Sintácticas (@Email, @NotBlank...)

Application:
- Funcionales (email existente, usuario inexistente...)

Domain:
- Reglas de negocio.

---

# 15. Eventos

Ejemplo:

RegisterUserUseCase

1. Crear usuario
2. Guardar usuario
3. Publicar UserRegisteredEvent

Handlers:

- WelcomeEmailHandler
- AuditHandler
- MetricsHandler

El caso de uso nunca envía el email directamente.

---

# 16. Persistencia

- PostgreSQL
- Flyway
- Nunca modificar tablas manualmente.

---

# 17. Seguridad

- Spring Security
- Argon2id
- JWT (futuro)
- HTTPS
- CORS
- Variables de entorno

---

# 18. Configuración

application-local.yml

application-dev.yml

application-prod.yml

---

# 19. Testing

- Unitarios
- Integración
- Arquitectura
- Testcontainers (futuro)

---

# 20. Convenciones

- Constructor Injection
- Sin @Autowired por campo
- Clases pequeñas
- Una responsabilidad
- Nombres expresivos
- Dominio sin dependencias técnicas

---

# 21. Roadmap

- Redis
- Kafka
- Docker
- GitHub Actions
- Prometheus
- Grafana
- ELK
- AWS SES
- Kubernetes
