# Informe de cambios — `feature/cart`

> Fecha: 2026-09-03 · Rama: `feature/cart` · Estado: sin commitear (working tree sobre el scaffold `b6946ab`)

## 1. Resumen ejecutivo

Implementación completa del módulo **carrito de compra** siguiendo DDD + Hexagonal y el patrón del módulo de referencia (`category`).

**Veredicto:** implementación sólida y fiel a la arquitectura y a `cart.md`. Un **bug crítico de pérdida de datos** en `MergeCartService` bloquea el merge; el resto son hallazgos puntuales.

---

## 2. Inventario de cambios

### Archivos nuevos (módulo cart)

| Capa | Archivos | Contenido |
|---|---|---|
| **Aplicación — comandos** | `AddCartItemCommand`, `UpdateCartItemCommand`, `RemoveCartItemCommand`, `ClearCartCommand`, `MergeCartCommand`, `ValidateCartCommand` | 6 comandos |
| **Aplicación — DTOs** | `CartDto`, `CartItemDto`, `CartCatalogEntryDto`, `CartValidationDto`, `RemovedCartItemDto`, `InsufficientStockCartItemDto` | 6 DTOs |
| **Aplicación — puertos in** | `AddCartItemUseCase`, `UpdateCartItemUseCase`, `RemoveCartItemUseCase`, `ClearCartUseCase`, `MergeCartUseCase`, `ValidateCartUseCase`, `GetCartUseCase` | 7 casos de uso |
| **Aplicación — puertos out** | `CartReadPort`, `CartWritePort`, `CartItemWritePort`, `CartPricingPort`, `CartProductAvailabilityPort` | 5 puertos por capacidad (ADR-003) |
| **Aplicación — servicios** | `AddCartItemService`, `UpdateCartItemService`, `RemoveCartItemService`, `ClearCartService`, `MergeCartService`, `ValidateCartService`, `GetCartService` | 1 servicio por caso de uso |
| **Aplicación — soporte** | `CartFinder`, `CartDtoAssembler` | resolución de identidad + ensamblado de precio |
| **Dominio — modelo** | `Cart` (agregado), `CartId` (value object) | invariantes: máx. 99/línea, máx. 50 líneas |
| **Dominio — excepciones** | `CartErrorCode` (enum), 5 excepciones + `HasErrorDetails` (interfaz genérica) | contrato ADR-012 |
| **Persistencia** | `CartPersistenceAdapter`, `CartEntity`, `CartItemEntity`, `CartJpaRepository`, `CartItemJpaRepository`, `CartProjectionJdbcRepository`, `CartPersistenceMapper` | JPA escrituras + JDBC proyecciones (ADR-002) |
| **Persistencia — soporte** | `ProductVisibilitySql` | subconsulta de visibilidad compartida |
| **Web** | `CartController`, `CartWebMapper`, 2 requests, 5 responses | 6 endpoints |

### Archivos modificados

| Archivo | Cambio |
|---|---|
| `ProductJdbcRepository.java` | Refactor: extrae la subconsulta de visibilidad a `ProductVisibilitySql` (DRY) |
| `GlobalExceptionHandler.java` | Añade manejo genérico de `HasErrorDetails` (campos extra en el body RFC 7807) |
| `docs/features/cart.md` | Especificación del módulo (17 líneas ajustadas) |

### Archivos eliminados

6 `.gitkeep` de scaffolding (reemplazados por código real).

### Tests (10 archivos)

`CartTest` (invariantes de dominio), 7 `*ServiceTest`, `CartPersistenceAdapterTest`, `CartControllerTest` (CSRF, cookie, JWT, contrato de error).

---

## 3. Arquitectura y decisiones

- **Puertos por capacidad** (ADR-003): 5 puertos de salida, un solo adapter (`CartPersistenceAdapter`) los implementa todos.
- **JPA + JDBC** (ADR-002): JPA para escrituras y lookups simples; JDBC (`CartProjectionJdbcRepository`) para la proyección de precio/display en una sola consulta.
- **Precio siempre en vivo** (regla 3.5): `cart_items` no guarda precio; `CartPricingPort` lo calcula en cada consulta.
- **Concurrencia blanda** (regla 3.3): el carrito no reserva stock; el único bloqueo real es el `UPDATE` condicional del checkout.
- **`HasErrorDetails`**: extensión genérica de ADR-012 para exponer `availableQuantity` en `CART_INSUFFICIENT_STOCK`.

---

## 4. Calidad de la implementación

### Fortalezas

- Fidelidad arquitectónica total; dominio sin dependencias de Spring/JPA/HTTP.
- Encapsulación del agregado `Cart` con invariantes correctos.
- DRY real (`ProductVisibilitySql`, `CartFinder`, `CartDtoAssembler`).
- Logging disciplinado (entry/exit DEBUG, sin PII); `GetCartService` correctamente sin `@Transactional`.
- Tests de comportamiento real, no de cobertura.

### Hallazgos (por severidad)

| # | Severidad | Archivo | Hallazgo |
|---|---|---|---|
| 1 | 🔴 **Crítico** | `MergeCartService.java:64-74` | **Auto-fusión que borra el carrito del cliente.** La reasignación (`assignToCustomer`) deja `session_token` intacto; en un segundo login la misma cookie resuelve el carrito como invitado y como cliente a la vez → `mergeInto(A, A)` duplica cantidades y luego `delete(A.id())` borra el carrito. Pérdida de datos. |
| 2 | 🔴 **Alto** | `CartPersistenceAdapter.java:126-138` | **Upsert no atómico** (find-then-insert). Dos `POST` concurrentes del mismo producto → violación de `UNIQUE (cart_id, product_id)` → 500. |
| 3 | 🟡 **Medio** | `AddCartItemService.java:83` | **Orden stock-vs-límite.** Con línea=90 + añadir 20 y stock=100, responde `CART_INSUFFICIENT_STOCK` con `availableQuantity=100` cuando el límite real es 99/línea; el cliente reintenta con 100 y falla con otro código. |
| 4 | 🟡 **Medio** | `CartEntity.java:14-18` | Sin `@Version` (excluido de ADR-009); la justificación ("nunca editado por dos admins") no cubre el carrito de invitado desde dos dispositivos. |
| 5 | 🟢 **Bajo** | `ValidateCartService.java:91` | Comparación string-vs-enum (`ProductStatus.ACTIVE.name().equals(...)`). |
| 6 | 🟢 **Bajo** | `CartController.java:212` | `UUID.fromString(jwt.getSubject())` puede lanzar si el subject no es UUID. |
| 7 | 🟢 **Bajo** | `CartProjectionJdbcRepository.java:78-83` | `IN ()` inválido si se llama con set vacío (hoy protegido por los callers). |
| 8 | 🟢 **Bajo** | `CartProjectionJdbcRepository.java:66` | Cast `(Integer) rs.getObject("stock")` asume columna `INTEGER`. |

---

## 5. Recomendaciones

1. **Bloquea el merge:** corregir el hallazgo #1 (guarda `guestCart.id().equals(customerCart.id())` o rotar `session_token` al reasignar).
2. **Antes de producción:** hallazgo #2 (upsert nativo `INSERT ... ON CONFLICT DO UPDATE`).
3. **Opcional:** #3–#8 son mejoras de robustez sin impacto funcional inmediato.
