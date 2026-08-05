# Estructura de carpetas y ficheros del proyecto

> Generado a partir del estado actual del repositorio (rama `feature/addDependenciesForCodeReview`).
> Actualizado tras la creación del **módulo de referencia `Category`** (ver
> [ADR-004](ADR/ADR-004-reference-module-category.md)) y la reestructuración de paquetes que lo acompañó.
> El resto de módulos de negocio (`auth`, `cart`, `customer`, `image`, `notification`, `order`,
> `payment`, `product`) siguen siendo **estructura preparada** (Use Case First / Hexagonal) pero
> están **vacíos**. En total hay 53 ficheros `.java` reales en el repo: 1 clase principal, 46 del
> módulo `category`, y 6 clases de test (`AppApplicationTests` + 5 tests de ArchUnit).

```text
rosyFloristasBack/
├── CLAUDE.md                          # Instrucciones del proyecto para Claude Code
├── HELP.md
├── README.md
├── checkstyle.xml                     # Reglas de estilo (Checkstyle)
├── pom.xml                            # Maven: dependencias y build
├── mvnw / mvnw.cmd                    # Maven Wrapper
│
├── .github/
│   ├── dependabot.yml                 # Configuración de actualizaciones automáticas
│   └── workflows/
│       ├── ci.yml                     # Pipeline de integración continua
│       └── codeql.yml                 # Análisis de seguridad estático (CodeQL)
│
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
│
├── .vscode/
│   ├── launch.json
│   ├── settings.json
│   └── tasks.json
│
├── ai/
│   └── project-context.md             # Contexto del proyecto para asistentes IA
│
├── docs/
│   ├── architecture/                  # Documentación arquitectónica obligatoria (DDD + Hexagonal)
│   │   ├── 00-project-principles.md
│   │   ├── 01-architecture-overview.md
│   │   ├── 02.package-conventions.md
│   │   ├── 03-naming-conventions.md
│   │   ├── 04-rest-conventions.md
│   │   ├── 05-persistence-conventions.md
│   │   ├── 06-validation-conventions.md
│   │   ├── 07-transaction-conventions.md
│   │   ├── 08-domain-events.md
│   │   ├── 09-archunit-rules.md
│   │   ├── 10-development-workflow.md
│   │   ├── PROJECT-STRUCTURE.md       # Este documento
│   │   └── ADR/
│   │       ├── ADR-001-use-case-first.md
│   │       ├── ADR-002-jpa-and-jdbc.md
│   │       ├── ADR-003-capability-based-ports.md
│   │       └── ADR-004-reference-module-category.md
│   └── domain/
│       └── category/                  # (vacío)
│
└── src/
    ├── main/
    │   ├── java/com/floristeriarosy/
    │   │   ├── AppApplication.java            # Entry point Spring Boot
    │   │   │
    │   │   ├── application/                   # Capa de aplicación (casos de uso)
    │   │   │   ├── auth/                      # (vacío, pendiente de implementar)
    │   │   │   │   ├── command/  ├── dto/  ├── mapper/
    │   │   │   │   └── port/{in,out}/  ├── query/  ├── service/
    │   │   │   ├── cart/                      # (vacío, sin subpaquetes)
    │   │   │   ├── category/                  # ✅ MÓDULO DE REFERENCIA — completo
    │   │   │   │   ├── command/
    │   │   │   │   │   ├── ChangeCategoryStatusCommand.java
    │   │   │   │   │   ├── CreateCategoryCommand.java
    │   │   │   │   │   ├── DeleteCategoryCommand.java
    │   │   │   │   │   └── UpdateCategoryCommand.java
    │   │   │   │   ├── query/
    │   │   │   │   │   ├── GetCategoriesQuery.java
    │   │   │   │   │   └── GetCategoryByIdQuery.java
    │   │   │   │   ├── dto/                   # (vacío, solo package-info.java)
    │   │   │   │   ├── mapper/                # (vacío, solo package-info.java)
    │   │   │   │   ├── port/
    │   │   │   │   │   ├── in/
    │   │   │   │   │   │   ├── ChangeCategoryStatusUseCase.java
    │   │   │   │   │   │   ├── CreateCategoryUseCase.java
    │   │   │   │   │   │   ├── DeleteCategoryUseCase.java
    │   │   │   │   │   │   ├── GetCategoriesUseCase.java
    │   │   │   │   │   │   ├── GetCategoryByIdUseCase.java
    │   │   │   │   │   │   └── UpdateCategoryUseCase.java
    │   │   │   │   │   └── out/
    │   │   │   │   │       ├── CategoryExistencePort.java
    │   │   │   │   │       ├── CategoryReadPort.java
    │   │   │   │   │       └── CategoryWritePort.java
    │   │   │   │   └── service/
    │   │   │   │       ├── ChangeCategoryStatusService.java
    │   │   │   │       ├── CreateCategoryService.java
    │   │   │   │       ├── DeleteCategoryService.java
    │   │   │   │       ├── GetCategoriesService.java
    │   │   │   │       ├── GetCategoryByIdService.java
    │   │   │   │       └── UpdateCategoryService.java
    │   │   │   ├── customer/                  # (vacío, pendiente de implementar)
    │   │   │   │   ├── command/  ├── dto/  ├── mapper/
    │   │   │   │   └── port/{in,out}/  ├── query/  ├── service/
    │   │   │   ├── image/                     # (vacío, sin subpaquetes)
    │   │   │   ├── notification/              # (vacío, sin subpaquetes)
    │   │   │   ├── order/                     # (vacío, pendiente de implementar)
    │   │   │   │   ├── command/  ├── dto/  ├── mapper/
    │   │   │   │   └── port/{in,out}/  ├── query/  ├── service/
    │   │   │   ├── payment/                   # (vacío, sin subpaquetes)
    │   │   │   ├── product/                   # (vacío, pendiente de implementar)
    │   │   │   │   ├── command/  ├── dto/  ├── mapper/
    │   │   │   │   └── port/{in,out}/  ├── query/  ├── service/
    │   │   │   └── shared/                    # (vacío, sin subpaquetes)
    │   │   │
    │   │   ├── domain/                        # Capa de dominio (sin dependencias externas)
    │   │   │   ├── event/
    │   │   │   │   └── category/              # (vacío, solo package-info.java)
    │   │   │   ├── exception/
    │   │   │   │   └── category/
    │   │   │   │       ├── CategoryAlreadyExistsException.java
    │   │   │   │       ├── CategoryInUseException.java
    │   │   │   │       └── CategoryNotFoundException.java
    │   │   │   ├── model/
    │   │   │   │   ├── auth/  ├── cart/  ├── customer/                 # (vacíos)
    │   │   │   │   ├── category/
    │   │   │   │   │   ├── Category.java
    │   │   │   │   │   ├── CategoryId.java
    │   │   │   │   │   └── CategoryStatus.java
    │   │   │   │   └── image/  ├── order/  ├── payment/  └── product/   # (vacíos)
    │   │   │   ├── service/
    │   │   │   │   └── category/              # (vacío, solo package-info.java)
    │   │   │   ├── shared/
    │   │   │   │   ├── validation/            # (vacío)
    │   │   │   │   └── valueobject/           # (vacío)
    │   │   │   └── specification/
    │   │   │       └── category/              # (vacío, solo package-info.java)
    │   │   │
    │   │   ├── infrastructure/                # Adaptadores (entrada/salida) — renombrado desde "infraestructure"
    │   │   │   ├── config/                    # (vacío)
    │   │   │   ├── mail/                      # (vacío)
    │   │   │   ├── persistence/
    │   │   │   │   ├── adapter/
    │   │   │   │   │   └── category/
    │   │   │   │   │       └── CategoryPersistenceAdapter.java
    │   │   │   │   ├── entity/
    │   │   │   │   │   └── category/
    │   │   │   │   │       └── CategoryEntity.java
    │   │   │   │   ├── jdbc/
    │   │   │   │   │   └── category/
    │   │   │   │   │       ├── projection/CategoryProjection.java
    │   │   │   │   │       ├── repository/CategoryJdbcRepository.java
    │   │   │   │   │       └── rowmapper/CategoryRowMapper.java
    │   │   │   │   ├── jpa/
    │   │   │   │   │   └── category/
    │   │   │   │   │       └── repository/CategoryJpaRepository.java
    │   │   │   │   └── mapper/
    │   │   │   │       └── category/
    │   │   │   │           └── CategoryPersistenceMapper.java
    │   │   │   ├── scheduler/                 # (vacío)
    │   │   │   ├── security/                  # (vacío) {config, filter, jwt, service}
    │   │   │   ├── storage/                   # (vacío) {local, mapper, s3}
    │   │   │   └── web/
    │   │   │       ├── advice/                # (vacío)
    │   │   │       ├── controller/
    │   │   │       │   ├── auth/  ├── cart/  ├── customer/              # (vacíos)
    │   │   │       │   ├── category/
    │   │   │       │   │   └── CategoryController.java
    │   │   │       │   └── image/  ├── order/  └── product/             # (vacíos)
    │   │   │       ├── mapper/
    │   │   │       │   └── category/
    │   │   │       │       └── CategoryWebMapper.java
    │   │   │       ├── request/
    │   │   │       │   ├── auth/  ├── cart/  ├── customer/              # (vacíos)
    │   │   │       │   ├── category/
    │   │   │       │   │   ├── ChangeCategoryStatusRequest.java
    │   │   │       │   │   ├── CreateCategoryRequest.java
    │   │   │       │   │   └── UpdateCategoryRequest.java
    │   │   │       │   └── image/  ├── order/  └── product/             # (vacíos)
    │   │   │       └── response/
    │   │   │           ├── auth/  ├── cart/  ├── customer/              # (vacíos)
    │   │   │           ├── category/
    │   │   │           │   ├── CategoryResponse.java
    │   │   │           │   └── CategorySummaryResponse.java
    │   │   │           └── image/  ├── order/  └── product/             # (vacíos)
    │   │   │
    │   │   └── shared/                        # Utilidades transversales
    │   │       ├── constant/                  # (vacío)
    │   │       ├── exception/                 # (vacío)
    │   │       ├── util/                      # (vacío)
    │   │       └── validation/                # (vacío)
    │   │
    │   └── resources/
    │       ├── application.properties
    │       ├── application.yml
    │       ├── banner.txt
    │       ├── db/migration/                  # Migraciones (Flyway/Liquibase)
    │       ├── messages/                      # i18n
    │       └── static/
    │
    └── test/
        ├── java/com/floristeriarosy/
        │   ├── AppApplicationTests.java
        │   └── architecture/                  # Tests ArchUnit (validan las reglas de docs/architecture)
        │       ├── ApplicationArchitectureTest.java
        │       ├── DependencyArchitectureTest.java
        │       ├── DomainArchitectureTest.java
        │       ├── InfrastructureArchitectureTest.java
        │       └── NamingConventionArchitectureTest.java  (stub, sin reglas aún)
        └── resources/
            └── application.properties
```

## Resumen por capa

| Capa | Paquete | Estado |
|---|---|---|
| Entry point | `com.floristeriarosy` | `AppApplication.java` implementado |
| Aplicación | `application/category` | ✅ Completo (commands, queries, port/in, port/out, services — skeleton sin lógica) |
| Aplicación | `application/{auth,cart,customer,image,notification,order,payment,product}` | Estructura de paquetes vacía, pendiente de implementar |
| Dominio | `domain/model/category`, `domain/exception/category` | ✅ Completo (`Category`, `CategoryId`, `CategoryStatus`, 3 excepciones) |
| Dominio | `domain/{event,service,specification}/category` | Paquete reservado (`package-info.java`), sin clases aún |
| Dominio | resto de `domain/*` | Estructura creada, vacía |
| Infraestructura | `infrastructure/web/*/category`, `infrastructure/persistence/*/category` | ✅ Completo (controller, request, response, mapper, adapter, entity, JPA/JDBC repos) |
| Infraestructura | resto de `infrastructure/*` | Estructura creada, vacía |
| Compartido | `shared/*` | Estructura creada, vacía |
| Tests | `test/*` | Tests de arquitectura (ArchUnit) implementados y activos; sin tests de negocio aún |

## Notas

- El paquete de infraestructura se llama **`infrastructure`** (spelling estándar en inglés). Anteriormente
  existía como `infraestructure` (con la "e" adicional); se renombró para que coincidiera con los patrones
  ya usados por los tests de ArchUnit (`"..infrastructure.."`), que de otro modo nunca coincidían con el
  árbol de paquetes real.
- Los paquetes `application/<módulo>/usecase` han sido eliminados de todos los módulos (`auth`, `customer`,
  `order`, `product`). Los casos de uso se representan únicamente como interfaces en `application/<módulo>/port/in`,
  según [ADR-001](ADR/ADR-001-use-case-first.md).
- `Category` es el **módulo de referencia** ([ADR-004](ADR/ADR-004-reference-module-category.md)): su
  estructura de paquetes debe replicarse exactamente en cada módulo futuro (`Product`, `Order`, `Customer`,
  `Cart`, `Image`, `Payment`...).
- Los Output Ports de `Category` siguen la convención **capability-based** de
  [ADR-003](ADR/ADR-003-capability-based-ports.md): `CategoryReadPort`, `CategoryWritePort`,
  `CategoryExistencePort` — no un port por método.
- Todo el código de `category` es un esqueleto arquitectónico: las interfaces no tienen implementación
  real y los métodos de clases concretas (`Service`, `Controller`, `PersistenceAdapter`, etc.) lanzan
  `UnsupportedOperationException("Not implemented yet")`. No hay lógica de negocio, validaciones,
  persistencia real ni migraciones Flyway todavía.
- La persistencia está pensada para combinar **JPA** (insert/update/delete/queries simples) y
  **JDBC** (filtros, joins, paginación, proyecciones), ver [ADR-002](ADR/ADR-002-jpa-and-jdbc.md).
- `docs/domain/category/` sigue vacío; reservado para documentación de dominio específica del módulo
  `category`.
