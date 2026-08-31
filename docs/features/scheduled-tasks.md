# Scheduled tasks

Tareas de fondo sin superficie pública propia — o con, como mucho, un endpoint de administración que
sirve de válvula manual sobre lo mismo que la tarea automática vigila. Cada una vive en el módulo
dueño de la tabla que toca, no en un módulo "scheduler" genérico; este documento existe para que se
puedan encontrar todas juntas, no para poseerlas.

Reglas transversales en [`00-security-validation-integrity.md`](00-security-validation-integrity.md).

---

## 1. Resumen

Cuatro tareas, tres cerradas y una pendiente:

| Tarea | Tabla | Automática | Manual | Documentada aquí |
|---|---|---|---|---|
| Alertas de stock bajo / reconciliación | `inventory_alerts` | Sí, diaria | Resolver/descartar, no disparar | [`inventory.md`](inventory.md), sección 3.8 — no se repite aquí |
| Purga de PII de pedidos vencidos | `orders`, `order_deliveries` | Sí, condicionada | Sí, `ADMIN` | Esta sección |
| Envío de notificaciones pendientes | `notifications` | Sí, cada pocos minutos | Reintentar una fallida | [`notification.md`](notification.md), reglas 3.5 y 3.6 — no se repite aquí |
| Limpieza de filas caducadas | `refresh_tokens`, `verification_tokens`, `idempotency_keys`, `notifications` | Pendiente | No | Sección 3.3 |

---

## 2. Tablas implicadas

`orders`, `order_deliveries`. Esquema y columnas de retención (`retention_until`,
`personal_data_purged_at`) en [`../database/README.md`](../database/README.md) y
[ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md).

---

## 3. Reglas de negocio

### 3.1 Purga automática — solo cliente dado de baja

`PurgeExpiredOrderPersonalDataService`, tarea diaria (mismo patrón que la alerta de inventario,
[ADR-013](../architecture/ADR/ADR-013-inventory-alerts.md)). Pone a `NULL` los campos cifrados del
comprador en `orders` y de `order_deliveries`, y estampa `personal_data_purged_at`, para todo pedido
que cumpla **las dos** condiciones:

- `retention_until <= hoy` (el periodo legal ya pasó, `app.retention.orders-period`,
  [ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md)).
- El cliente del pedido está `ARCHIVED` (dado de baja, [`customer.md`](customer.md)).

Un pedido de un cliente que sigue `ACTIVE` **nunca** se purga solo, por muy vencido que esté su
`retention_until`: el periodo legal marca cuánto tiempo *puede* conservarse el dato, no cuánto tiempo
se borra automáticamente. Un cliente que sigue comprando no tiene por qué perder en silencio la
dirección de un pedido del año pasado.

Consecuencia directa: un pedido de invitado (`customer_id IS NULL`, sin cuenta que pueda estar
`ARCHIVED`) **nunca** entra por esta vía, por vencido que esté. Solo se purga por la vía manual.

### 3.2 Purga manual — `ADMIN`, sin restricción de plazo ni de estado

`POST /orders/{id}/purge-personal-data`. Cubre lo que la automática deja fuera a propósito:

- Pedidos de invitado.
- Pedidos de un cliente `ACTIVE` que pide expresamente el borrado de un pedido concreto, tramitado por
  soporte: no hay autoservicio.
- Cualquier pedido, esté o no vencido su `retention_until`: la vía manual no comprueba el periodo
  legal, es una petición explícita de supresión, no una aplicación automática del plazo.

Solo el administrador puede iniciarla — el cliente, logueado o no, no tiene ningún endpoint propio
para borrar datos de sus pedidos; si lo pide, lo tramita soporte. Idempotente: sobre un pedido ya
purgado, 409 `ORDER_ALREADY_PURGED`, no un no-op silencioso.

### 3.3 Limpieza de filas caducadas — pendiente

`refresh_tokens` ([`auth.md`](auth.md)), `verification_tokens` ([`customer.md`](customer.md)),
`idempotency_keys` ([ADR-011](../architecture/ADR/ADR-011-idempotent-money-operations.md)) y las filas
`SENT` de `notifications` ([ADR-015](../architecture/ADR/ADR-015-transactional-outbox-for-notifications.md))
acumulan filas que dejan de servir para nada. Ninguna se borra sola: las ADR que las introdujeron dicen
explícitamente que la limpieza es una tarea programada, no una constraint.

**No está diseñada todavía.** Falta decidir frecuencia y ventana de gracia, y es la única tarea de
este documento sin cerrar
([`00-security-validation-integrity.md`](00-security-validation-integrity.md), sección 12, punto 5).
No es urgente en el sentido funcional —nada se rompe si la tabla crece— pero sí lo es en el operativo:
crecimiento sin límite y sin nadie mirándolo.

Un token caducado que sigue en la tabla no es un agujero de seguridad: la verificación comprueba
`expires_at` y `revoked_at` en cada uso, no la mera existencia de la fila.

### 3.4 Relación con la baja de cliente

Darse de baja ([`customer.md`](customer.md)) no purga los pedidos del cliente en el mismo instante:
solo cambia `customers.status` a `ARCHIVED`, que es la condición que la regla 3.1 necesita para que la
tarea diaria empiece a considerarlos elegibles — y solo los que, además, ya hayan vencido su
`retention_until`. Un cliente puede darse de baja hoy y que su pedido de la semana pasada siga con su
PII intacta durante todo el periodo legal.

---

## 4. Endpoints

Prefijo `/api/v1`.

| Método | Ruta | Rol | Devuelve |
|---|---|---|---|
| `POST` | `/orders/{id}/purge-personal-data` | `ADMIN` | 200 |

Sin cuerpo: es una confirmación, no una decisión con matices — mismo criterio que
`POST /purchases/{id}/receive` en [`purchasing.md`](purchasing.md).

---

## 5. Response DTOs

Reutiliza `OrderResponse` de [`order.md`](order.md): tras la purga, los campos del comprador vuelven
`null` igual que en cualquier otra lectura de un pedido ya purgado.

---

## 6. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `PurgeExpiredOrderPersonalDataUseCase` | `PurgeExpiredOrderPersonalDataService` | Sí — tarea programada, sin controlador |
| `PurgeOrderPersonalDataUseCase` | `PurgeOrderPersonalDataService` | Sí — endpoint `ADMIN` |

Ambos casos de uso llaman a la misma operación de dominio (poner a `NULL` los campos del comprador y
estampar `personal_data_purged_at`); lo único que cambia es qué `WHERE` selecciona los pedidos
candidatos — el filtro completo de la regla 3.1 en un caso, un `id` concreto sin más condición en el
otro.

---

## 7. Output Ports

| Port | Capacidad |
|---|---|
| `OrderPersonalDataPort` | `findEligibleForAutomaticPurge()`, `purge(orderId)` |

No reutiliza `OrderWritePort` de `order.md` (`save` genérico): purgar PII es una operación acotada y
distinta de guardar un pedido, con su propio `UPDATE` de columnas concretas — mismo criterio que
`RegisterStockMovementUseCase` siendo una capacidad propia en vez de un `save` genérico sobre
`products`.

Persistencia ([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): JDBC — es un `UPDATE` masivo
por condición (regla 3.1) o puntual por id (regla 3.2), no una entidad JPA completa la que se
manipula.

---

## 8. Errores

Enum `ScheduledTaskErrorCode` en `domain/exception/order/` (la purga de pedidos es, en términos de
error, un error de `order` — [ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `ORDER_NOT_FOUND` | 404 | No existe |
| `ORDER_ALREADY_PURGED` | 409 | `personal_data_purged_at` ya tiene valor |

---

## 9. Casos borde

| Situación | Comportamiento |
|---|---|
| Cliente se da de baja con un pedido cuyo `retention_until` ya venció | La siguiente ejecución diaria lo purga; hasta entonces sigue con PII |
| Cliente se da de baja y luego vuelve a registrarse con el mismo email | Sin relación: la fila `ARCHIVED` original no resucita ni se reutiliza (ADR-007, regla de baja) |
| Purga manual sobre un pedido de un cliente `ACTIVE` | Permitida (regla 3.2); no cambia el estado del cliente, solo la PII de ese pedido |
| Purga manual sobre un pedido ya purgado por la tarea automática | 409 `ORDER_ALREADY_PURGED` |
| Pedido de invitado con `retention_until` vencido hace años | Sigue con PII intacta hasta que un administrador la purgue a mano (regla 3.1) |
| `app.retention.orders-period` sin configurar | Fuera de alcance de este documento — pendiente en `00-security-validation-integrity.md`, sección 12 |

---

## 10. Alcance ajeno

- **Baja de cliente** (`customers.status = ARCHIVED`) — [`customer.md`](customer.md).
- **Alertas de inventario** (otra tarea programada) — [`inventory.md`](inventory.md), sección 3.8,
  [ADR-013](../architecture/ADR/ADR-013-inventory-alerts.md).
- **Envío de notificaciones** (otra más) — [`notification.md`](notification.md), reglas 3.5 a 3.7,
  [ADR-015](../architecture/ADR/ADR-015-transactional-outbox-for-notifications.md).
- **Valor de `app.retention.orders-period`** — depende del requisito legal aplicable; pendiente en
  [`00-security-validation-integrity.md`](00-security-validation-integrity.md), sección 12.
- **Esquema y snapshot cifrado del comprador** — [`order.md`](order.md).
