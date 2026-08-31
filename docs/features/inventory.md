# Inventory

Movimientos de stock: alta de inventario, ventas, mermas, ajustes y compras recibidas. El modo de
inventario de un producto (gestionado o no) se define en [`product.md`](product.md), sección 3.7;
este documento define quién escribe en `stock_movements` y con qué reglas.

Sigue el patrón del módulo de referencia ([`category.md`](category.md)). Reglas transversales en
[`00-security-validation-integrity.md`](00-security-validation-integrity.md).

No tiene paquete propio todavía en `application/` — se crea al implementar este módulo.

---

## 1. Resumen

`stock_movements` es la auditoría completa de cada cambio de stock. No es opcional ni derivable: es
la única fuente que explica por qué `products.stock` vale lo que vale.

Cinco tipos, cada uno con signo obligatorio ([`../database/README.md`](../database/README.md)):

| Tipo | Signo | Quién lo genera |
|---|---|---|
| `INITIAL` | `>= 0` | Al activar la gestión de inventario de un producto (único por producto) |
| `PURCHASE` | `> 0` | Al recibir una compra a proveedor ([`purchasing.md`](purchasing.md)) |
| `SALE` | `< 0` | Al confirmarse un pedido ([`order.md`](order.md)) |
| `WASTE` | `< 0` | Acción explícita del administrador: flor que se estropea, rotura |
| `ADJUSTMENT` | Cualquiera, `≠ 0` | Corrección manual, o reactivación de un producto que ya tuvo `INITIAL` |

Todos comparten un único punto de escritura transaccional: `RegisterStockMovementService`. Ningún
otro código escribe en `stock_movements` ni actualiza `products.stock` directamente — ni siquiera
`order.md` o `purchasing.md`, que **llaman** a este servicio en vez de tocar la tabla.

---

## 2. Tablas implicadas

`stock_movements` y `products.stock`. Esquema completo en
[`../database/README.md`](../database/README.md), sección Inventario.

| Columna de `stock_movements` | Restricción |
|---|---|
| `product_id` | `REFERENCES products ON DELETE RESTRICT` |
| `type` | `INITIAL`, `PURCHASE`, `SALE`, `WASTE`, `ADJUSTMENT` |
| `quantity` | Signo obligatorio según `type`; nunca `0` salvo... nunca `0`, ni siquiera `INITIAL` con stock de partida cero se guarda como `0` en cantidad — ver 3.2 |
| `resulting_stock` | `>= 0`; dato de auditoría, no se sincroniza por trigger |
| `admin_user_id` | `ON DELETE SET NULL`; `NULL` en movimientos generados por el sistema (`SALE`) |

`ux_stock_movements_initial` — índice único parcial sobre `(product_id) WHERE type = 'INITIAL'` — es
la garantía de que un producto tiene un único punto de partida de inventario en toda su historia.

---

## 3. Reglas de negocio

### 3.1 Escritura sin lectura previa

Toda venta y todo registro de movimiento actualiza `products.stock` con `UPDATE` condicional, nunca
`SELECT` seguido de `UPDATE`:

```sql
UPDATE products SET stock = stock - :quantity
WHERE id = :productId AND stock IS NOT NULL AND stock >= :quantity
RETURNING stock;
```

Cero filas afectadas significa stock insuficiente; la operación se rechaza sin tocar
`stock_movements`. El valor devuelto por el `UPDATE` es el que se guarda en `resulting_stock` — nunca
un valor calculado aparte, que podría desincronizarse de lo que la base de datos aplicó realmente.

Ambas escrituras, el `UPDATE` de `products.stock` y el `INSERT` en `stock_movements`, van en la misma
transacción.

### 3.2 `INITIAL`: el único punto de partida

Se genera al activar la gestión de inventario de un producto que no la tenía
([`product.md`](product.md), regla 3.7, `ProductInventoryPort.initializeStock`). `quantity` es el
stock de partida — puede ser `0` si el producto se da de alta sin unidades todavía, pero la fila
existe igual.

Un segundo intento de `INITIAL` sobre el mismo producto lo impide `ux_stock_movements_initial`. Se
traduce a 409 `INVENTORY_ALREADY_INITIALIZED` — no debería alcanzarse nunca desde la API si
`product.md` enruta correctamente, pero la base de datos es quien realmente lo garantiza.

### 3.3 `PURCHASE`: mercancía recibida

Lo genera [`purchasing.md`](purchasing.md) al marcar una compra `RECEIVED`, para cada línea de
`purchase_items` cuyo `product_id` no sea `NULL` — el material a granel (flor suelta, cinta) no
alimenta `stock_movements`, porque no es un producto del catálogo.

Este módulo no expone un endpoint propio para `PURCHASE`: lo dispara `purchasing.md` a través de
`RegisterStockMovementUseCase`, y aquí solo se documenta la regla.

**Revertir una recepción** ([`purchasing.md`](purchasing.md), regla 3.2) genera un `ADJUSTMENT` de
signo contrario, no borra ni edita el `PURCHASE` original — `stock_movements` es de solo escritura.
Si parte de lo recibido ya se vendió, el `UPDATE` condicional de la regla 3.1 rechaza la reversión
completa: no hay reversión parcial.

### 3.4 `SALE`: venta confirmada

Lo genera [`order.md`](order.md), Fase 1 de su checkout (regla 3.2 de ese documento): un `UPDATE`
condicional por línea, **antes** de cobrar, no después — el pedido no llega a crearse si el cobro
falla, así que reservar primero evita generar una venta que termine sin pedido. `admin_user_id` es
siempre `NULL` — nadie del panel ejecuta una venta.

Si el cobro se rechaza tras la reserva, `order.md` revierte con un `ADJUSTMENT` (regla 3.3 de este
documento). Si el pedido se rechaza o cancela después de creado, la reversión es la misma —
[`order.md`](order.md), regla 3.10.

Este documento no expone `SALE` como acción de la API: es una consecuencia del checkout, no una
operación de inventario que el administrador dispare.

### 3.5 `WASTE`: merma

Acción explícita del administrador: una rosa que se marchita, un jarrón que se rompe. Requiere
`note` — a diferencia del resto de tipos, aquí es **obligatoria**, porque una merma sin motivo
escrito es un agujero de stock que nadie puede explicar seis meses después.

### 3.6 `ADJUSTMENT`: corrección manual

Cubre dos casos:

- Un recuento físico no coincide con `products.stock` (la reconciliación de la regla 3.8 lo detecta).
- Reactivar la gestión de inventario de un producto que **ya tuvo** un `INITIAL` alguna vez
  ([`product.md`](product.md), regla 3.7): como `ux_stock_movements_initial` impide un segundo
  `INITIAL`, la reactivación se registra como `ADJUSTMENT`.

`note` es obligatoria por el mismo motivo que en `WASTE`: una corrección sin explicación es
indistinguible de un error.

### 3.7 Consecuencia de `stock = NULL`

Un producto sin gestión de inventario **no genera movimientos, nunca**. No es un invariante que la
base de datos pueda comprobar — un `CHECK` no puede consultar otra tabla — así que lo sostiene el
único punto de escritura: `RegisterStockMovementService` rechaza cualquier intento de movimiento
sobre un producto con `stock IS NULL`, respondiendo 409 `INVENTORY_NOT_MANAGED`.

### 3.8 Alertas: stock bajo y reconciliación

Dos condiciones distintas comparten un mismo mecanismo de alerta, historial y acciones de
administrador. Detalle completo de la decisión en
[ADR-013](../architecture/ADR/ADR-013-inventory-alerts.md).

| Tipo | Qué detecta | Es un problema de… |
|---|---|---|
| `LOW_STOCK` | `products.stock <= low_stock_threshold` | Negocio: hay que reponer antes de agotarse |
| `RECONCILIATION_MISMATCH` | `products.stock` no coincide con la suma de sus movimientos | Integridad: algo escribió sin pasar por `RegisterStockMovementService` |

**No son lo mismo.** Un `LOW_STOCK` es normal y esperable — la tienda vende, el stock baja, se
repone. Un `RECONCILIATION_MISMATCH` **nunca debería ocurrir** si el único punto de escritura
(regla 3.1) se respeta en todo el código: si aparece, es una señal de bug, un `UPDATE` manual sobre
la base de datos, o una escritura que se saltó el servicio.

**Umbral por producto.** `products.low_stock_threshold`, `NULL` si no está configurado — mismo
convenio que `products.stock = NULL` para "sin gestión". Un umbral global no sirve: cinco unidades es
mucho para un jarrón caro y casi nada para rosa suelta.

**Generación: una tarea diaria**, no una comprobación en cada petición. Ninguna de las dos
condiciones es urgente en el sentido en que sí lo es una venta (regla 3.1): comprobarlas en cada
lectura costaría mucho más de lo que aportaría.

**Sin duplicados.** Si ya hay una alerta `OPEN` del mismo tipo para el mismo producto, la tarea del
día siguiente no crea otra — lo garantiza `ux_inventory_alerts_open`, un índice único parcial, no
solo la lógica de la tarea.

**Tres desenlaces posibles**, y uno de ellos es no hacer nada:

| Acción | Efecto |
|---|---|
| Resolver | El problema se corrigió (se repuso, se hizo un `ADJUSTMENT`). Termina el historial de esa alerta |
| Descartar | Se reconoce y no hace falta actuar (umbral demasiado conservador). También termina el historial |
| Mantener | No se hace nada; la alerta sigue `OPEN` |

**La resolución es manual, siempre.** La tarea diaria nunca cierra una alerta por su cuenta, aunque el
número que la causó ya se haya normalizado. Es una simplificación deliberada, no un olvido: cerrarla
sola exigiría distinguir "se corrigió a propósito" de "hoy da la casualidad de que no se cumple",
que es una decisión en sí misma (sección 12).

Las consultas subyacentes son las que ya definía
[`../database/README.md`](../database/README.md):

```sql
-- RECONCILIATION_MISMATCH
SELECT p.id, p.stock, COALESCE(SUM(m.quantity), 0) AS movements_total
FROM products p LEFT JOIN stock_movements m ON m.product_id = p.id
WHERE p.stock IS NOT NULL
GROUP BY p.id, p.stock
HAVING p.stock <> COALESCE(SUM(m.quantity), 0);

-- LOW_STOCK
SELECT id, stock, low_stock_threshold
FROM products
WHERE stock IS NOT NULL AND low_stock_threshold IS NOT NULL AND stock <= low_stock_threshold;
```

---

## 4. Endpoints

Prefijo `/api/v1`. Todo administración — el catálogo público nunca ve `stock_movements`.

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/products/{id}/stock-movements` | 200 — historial paginado de un producto |
| `POST` | `/products/{id}/stock-movements/waste` | 201 — registra `WASTE` |
| `POST` | `/products/{id}/stock-movements/adjustment` | 201 — registra `ADJUSTMENT` |
| `GET` | `/inventory/alerts` | 200 — historial paginado; filtra por `type`, `status`, `productId` |
| `PATCH` | `/inventory/alerts/{id}/resolve` | 200 — cierra la alerta como corregida |
| `PATCH` | `/inventory/alerts/{id}/dismiss` | 200 — cierra la alerta como descartada |

`INITIAL` no tiene endpoint aquí: se dispara desde `PATCH /products/{id}/inventory`
([`product.md`](product.md)). `PURCHASE` no tiene endpoint aquí: lo dispara `purchasing.md`. `SALE` no
tiene endpoint en absoluto: es interno al checkout. Este módulo expone las dos acciones que un
administrador ejecuta directamente sobre el inventario, y las dos que ejecuta sobre una alerta ya
generada.

No hay `GET /inventory/reconciliation` como endpoint de consulta puntual: la comprobación es diaria
y automática (regla 3.8), y su resultado se consume como historial de alertas, no como informe bajo
demanda.

---

## 5. Request DTOs

### `RegisterWasteRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `quantity` | Integer | `@NotNull`, `@Positive` — el signo negativo lo aplica el servicio, no lo envía el cliente |
| `note` | String | `@NotBlank`, `@Size(max = 500)` |

### `RegisterAdjustmentRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `quantity` | Integer | `@NotNull` — puede ser positivo o negativo, según si el ajuste suma o resta |
| `note` | String | `@NotBlank`, `@Size(max = 500)` |

`quantity = 0` se rechaza: `chk_stock_movements_quantity_nonzero` ya lo impide para todo tipo salvo
`INITIAL`, y aquí no aplica ninguna excepción.

### `ResolveAlertRequest` / `DismissAlertRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `note` | String | `@Size(max = 500)`, opcional |

Sin campos obligatorios: a diferencia de `WASTE`/`ADJUSTMENT`, cerrar una alerta no crea un
movimiento de stock, solo cierra un hallazgo. El `note` es contexto opcional para quien revise el
historial después.

El umbral de stock bajo (`low_stock_threshold`) no tiene request propio en este documento: se edita
como parte de `ChangeInventoryModeRequest` en [`product.md`](product.md), porque es configuración del
producto, no una acción sobre una alerta.

---

## 6. Response DTOs

### `StockMovementResponse`

`id`, `productId`, `type`, `quantity`, `resultingStock`, `adminUserName`, `note`, `createdAt`.

`adminUserName`, no `adminUserId`: el panel necesita mostrar quién hizo el movimiento, no resolver el
identificador por su cuenta. Si el administrador se dio de baja, aparece como `null` — el `SET NULL`
de la fila.

### `InventoryAlertResponse`

`id`, `type`, `productId`, `productName`, `observedValue`, `expectedValue`, `status`,
`resolvedByAdminName`, `resolvedAt`, `createdAt`.

`observedValue`/`expectedValue` se etiquetan según `type` en la capa de presentación —
"stock actual" / "umbral" para `LOW_STOCK`, "stock" / "según movimientos" para
`RECONCILIATION_MISMATCH` — no son columnas distintas por tipo, para no duplicar la tabla.

---

## 7. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `RegisterStockMovementUseCase` | `RegisterStockMovementService` | Sí — único punto de escritura, lo llaman `product`, `order` y `purchasing` |
| `RegisterWasteUseCase` | `RegisterWasteService` | Sí — orquesta sobre `RegisterStockMovementUseCase` |
| `RegisterAdjustmentUseCase` | `RegisterAdjustmentService` | Sí — orquesta sobre `RegisterStockMovementUseCase` |
| `GetStockMovementsUseCase` | `GetStockMovementsService` | No |
| `GenerateInventoryAlertsUseCase` | `GenerateInventoryAlertsService` | Sí — la tarea diaria; crea alertas, nunca las cierra |
| `ResolveInventoryAlertUseCase` | `ResolveInventoryAlertService` | Sí |
| `DismissInventoryAlertUseCase` | `DismissInventoryAlertService` | Sí |
| `GetInventoryAlertsUseCase` | `GetInventoryAlertsService` | No |

`RegisterStockMovementUseCase` es el único que otros módulos invocan directamente. `RegisterWasteUseCase`
y `RegisterAdjustmentUseCase` existen aparte porque cada uno valida su propia entrada (`note`
obligatoria, signo del `quantity`) antes de delegar — no perforan la capa, la usan.

`GenerateInventoryAlertsService` lo dispara `infrastructure/scheduler`, una vez al día. Ejecuta las
dos consultas de la regla 3.8 e inserta una fila `OPEN` por cada resultado que no tenga ya una
abierta — el `INSERT` se apoya en `ux_inventory_alerts_open` para no duplicar bajo concurrencia,
igual que el resto del módulo evita `SELECT`-antes-de-escribir.

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `StockMovementWritePort` | `save` |
| `StockMovementReadPort` | `findByProduct`, `findReconciliationMismatches` |
| `InventoryAlertPort` | `save`, `findOpen`, `findAll`, `resolve`, `dismiss` |
| `LowStockPort` | `findBelowThreshold` |
| `ProductStockPort` | `decrementConditional`, `incrementConditional`, `setInitial`, `clear` |

`ProductStockPort` es la contraparte de `ProductInventoryPort` que define `product.md`: uno es cómo
`product` le pide a `inventory` que inicialice o desactive stock, el otro es cómo `inventory` aplica
el `UPDATE` condicional sobre `products.stock`. No son el mismo puerto porque tienen dueños distintos
— `product.md` describe la intención, `inventory.md` la ejecución sobre la fila.

Persistencia ([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): el `UPDATE` condicional y las
dos consultas de detección de alertas son JDBC, por ser SQL con predicado y agregados. El `INSERT` de
cada movimiento y de cada alerta es JPA; resolver y descartar son `UPDATE` simples sobre una fila por
id, también JPA.

---

## 9. Errores

Enum `InventoryErrorCode` en `domain/exception/inventory/`
([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `INVENTORY_NOT_MANAGED` | 409 | El producto tiene `stock = NULL`; no admite movimientos |
| `INVENTORY_INSUFFICIENT_STOCK` | 409 | El `UPDATE` condicional afecta cero filas en una salida (`SALE`, `WASTE`) |
| `INVENTORY_ALREADY_INITIALIZED` | 409 | Segundo `INITIAL` sobre el mismo producto |
| `INVENTORY_VALIDATION_FAILED` | 422 | Bean Validation; con `errors[]` |
| `INVENTORY_ALERT_NOT_FOUND` | 404 | El identificador no existe |
| `INVENTORY_ALERT_NOT_OPEN` | 409 | Se intenta resolver o descartar una alerta ya cerrada |

`INVENTORY_INSUFFICIENT_STOCK` en `WASTE` significa que se intenta desechar más de lo que hay en
stock — un error de captura del administrador, no una condición de carrera.

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| `WASTE` mayor que el stock disponible | 409 `INVENTORY_INSUFFICIENT_STOCK`; no se registra el movimiento |
| `ADJUSTMENT` que dejaría `resulting_stock` negativo | Lo rechaza `chk_stock_movements_resulting_stock`; se traduce al mismo 409 |
| `WASTE` o `ADJUSTMENT` sobre un producto no gestionado | 409 `INVENTORY_NOT_MANAGED` |
| Dos ventas simultáneas sobre la última unidad | Solo una gana el `UPDATE` condicional; la otra recibe stock insuficiente sin haber leído nada antes |
| Reactivar inventario tras haberlo desactivado | `ADJUSTMENT`, no `INITIAL`; el histórico previo a la desactivación sigue en la tabla |
| Compra con líneas de material a granel (`product_id = NULL`) | No genera `stock_movements`; solo las líneas con producto del catálogo lo hacen |
| La tarea diaria no encuentra discrepancias | No inserta ninguna fila; no es un error, es el resultado esperado |
| La tarea diaria vuelve a encontrar el mismo problema al día siguiente | No duplica: ya hay una alerta `OPEN` para ese producto y tipo |
| Resolver una alerta ya `RESOLVED` o `DISMISSED` | 409 `INVENTORY_ALERT_NOT_OPEN` |
| El stock sube por encima del umbral sin que nadie cierre la alerta | Sigue `OPEN`; la resolución es siempre manual (regla 3.8) |
| Producto con `low_stock_threshold` configurado pero sin gestión de inventario (`stock = NULL`) | Nunca genera `LOW_STOCK`: la consulta exige `stock IS NOT NULL` |

---

## 11. Alcance ajeno

- **Modo de inventario del producto** (`ChangeInventoryModeRequest`, cuándo exige stock inicial) —
  [`product.md`](product.md), sección 3.7.
- **Generación de `PURCHASE` al recibir mercancía** — `purchasing.md`.
- **Generación de `SALE` al confirmar un pedido** — `order.md`.

---

## 12. Decisiones cerradas

**Retención del historial de movimientos: sin límite.** `stock_movements` crece sin fin; no se
purga ni se archiva. No tiene PII (ADR-007 no le aplica) y es la única auditoría de por qué
`products.stock` vale lo que vale — recortarla borraría la explicación de un valor que sigue vigente.

**Alertas de stock bajo y de reconciliación: resueltas.** Sección 3.8 y
[ADR-013](../architecture/ADR/ADR-013-inventory-alerts.md). Tarea diaria, sin duplicados, tres
desenlaces posibles, resolución siempre manual.

**Fuera de alcance, por decisión explícita, no por olvido:**

- **Auto-resolución.** La tarea diaria nunca cierra una alerta por su cuenta, aunque el número que la
  causó ya se haya normalizado. No se implementa.
- **Notificación activa.** Las alertas se consultan en `GET /inventory/alerts`; no hay email, push ni
  ningún aviso que saque al administrador de su rutina para que las vea. No se implementa por ahora.

Este módulo no deja pendientes abiertos.
