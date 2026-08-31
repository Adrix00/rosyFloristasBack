# Purchasing

Proveedores y compras: alta de proveedor, registro de una compra, recepción de mercancía y su
reversión si algo se registró mal.

Sigue el patrón del módulo de referencia ([`category.md`](category.md)). Reglas transversales en
[`00-security-validation-integrity.md`](00-security-validation-integrity.md).

No tiene paquete propio todavía en `application/` — se crea al implementar este módulo.

---

## 1. Resumen

Este módulo es puramente administrativo: no tiene superficie pública, no lo toca ni el cliente ni el
catálogo. Registra de dónde viene la mercancía y qué costó, y es quien **dispara** los movimientos
`PURCHASE` que define [`inventory.md`](inventory.md), sin escribir él mismo en `stock_movements`.

Una compra tiene tres estados —`ORDERED`, `RECEIVED`, `REVERTED`— añadidos en la migración `V8`
porque el esquema original no distinguía "esto se ha pedido" de "esto ya está en la tienda". Detalle
completo de la decisión en
[ADR-014](../architecture/ADR/ADR-014-purchase-receiving-and-reversal.md).

---

## 2. Tablas implicadas

`suppliers`, `purchases`, `purchase_items`. Esquema en
[`../database/README.md`](../database/README.md).

| Columna de `purchases` | Restricción |
|---|---|
| `supplier_id` | `REFERENCES suppliers ON DELETE RESTRICT` |
| `invoice_total` | `>= 0`; **no** tiene por qué coincidir con la suma de `purchase_items` (portes, impuestos) |
| `status` | `V8`: `ORDERED`, `RECEIVED`, `REVERTED` |
| `received_at`, `reverted_at`, `revert_reason` | `V8`; presencia ligada al `status` por `CHECK` |
| `invoice_number` | `V8`: único por proveedor (`uq_purchases_supplier_invoice`); `NULL` conviven sin límite |

| Columna de `purchase_items` | Restricción |
|---|---|
| `purchase_id` | `REFERENCES purchases ON DELETE CASCADE` |
| `product_id` | `REFERENCES products ON DELETE RESTRICT`, **nullable** |
| `description` | `NOT NULL` siempre, incluso con `product_id` presente |
| `quantity` | `NUMERIC(10,2)`, `> 0` — no entero: material a granel se compra por peso o longitud |
| `line_total` | Columna generada: `quantity * unit_cost` |

`product_id = NULL` es material a granel — flor suelta, cinta, papel — que nunca se vende como
producto del catálogo y nunca alimenta `stock_movements`.

`suppliers` no cifra ningún dato: son datos de contacto B2B, no PII de consumidor
([ADR-005](../architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md)).

---

## 3. Reglas de negocio

### 3.1 Ciclo de vida de una compra

```
ORDERED ──(recibir)──> RECEIVED ──(revertir)──> REVERTED
   │
   └──(editar / eliminar libremente)
```

| Estado | Significado | Stock | Editable |
|---|---|---|---|
| `ORDERED` | Pedida al proveedor, aún no ha llegado | Sin efecto | Sí, y se puede eliminar |
| `RECEIVED` | La mercancía ya está en la tienda | Genera `PURCHASE` por línea con producto | No |
| `REVERTED` | Se anuló una recepción equivocada | Genera `ADJUSTMENT` que la deshace | No; terminal |

**`ORDERED` no toca inventario.** Es un documento — "esto se ha pedido"— y se edita o se borra sin
restricción porque nada depende todavía de él.

**Recibir es la acción que mueve stock**, no crear la compra. Al marcar `RECEIVED`, por cada línea de
`purchase_items` con `product_id` no nulo se genera un movimiento `PURCHASE`
(`RegisterStockMovementUseCase`, [`inventory.md`](inventory.md)). Las líneas sin producto (material a
granel) no generan nada.

**Una vez `RECEIVED`, la compra es inmutable.** Sus cantidades y costes son ya un hecho ocurrido, no
un borrador. Corregirla no es editarla: es revertirla y, si hace falta, registrar una compra nueva.

### 3.2 Revertir una recepción

Deshace el efecto de stock de una compra `RECEIVED`, sin borrar ni editar el movimiento `PURCHASE`
original: `stock_movements` es un histórico de solo escritura
([`../database/README.md`](../database/README.md)). Por cada línea con producto se genera un
`ADJUSTMENT` de signo contrario, en la misma transacción que el cambio de estado a `REVERTED`.

`revert_reason` es obligatorio, mismo criterio que `WASTE`/`ADJUSTMENT` en
[`inventory.md`](inventory.md): una reversión sin motivo escrito es tan inexplicable como una merma
sin motivo.

**Se rechaza si ya no queda stock suficiente que devolver.** Si parte de lo recibido ya se vendió, el
`ADJUSTMENT` de reversión dejaría `products.stock` en negativo, y el `UPDATE` condicional
([`inventory.md`](inventory.md), regla 3.1) lo impide. No hay reversión parcial: es un rechazo
completo con 409, y el administrador decide a mano cómo proceder — la reversión automática de "lo que
quede" dejaría la compra en un estado ambiguo, ni recibida del todo ni corregida del todo.

Revertir es terminal: una compra `REVERTED` no vuelve a `RECEIVED`. Si la recepción original sí era
correcta y el error fue revertir por equivocación, se registra una compra nueva.

### 3.3 Descripción de línea

`description` es obligatoria siempre, incluso cuando la línea lleva `product_id`: es lo que el propio
esquema exige (`NOT NULL` sin excepción). Al añadir una línea con producto, el formulario la
autorrellena con el nombre del producto en ese momento — editable, porque una factura de proveedor
puede describir el artículo de otra forma ("Rosa roja tallo largo 60cm" en vez de solo "Rosa roja").

### 3.4 Coste y factura

`invoice_total` es el importe del documento del proveedor, y no se recalcula desde las líneas: puede
incluir portes o impuestos que `purchase_items` no desglosa. No es un dato redundante con
`SUM(line_total)`, es una cifra distinta por diseño.

`invoice_number` es único por proveedor (`V8`): cargar la misma factura dos veces es el error de
tecleo más común al registrar compras a mano, y ahora lo impide la base de datos, no la atención del
administrador.

### 3.5 Proveedores

`active = false` bloquea **crear compras nuevas** contra ese proveedor — 409
`SUPPLIER_INACTIVE` — pero no toca las compras ya existentes, que siguen siendo historial válido.

Un proveedor no se borra si tiene compras (`purchases.supplier_id` es `RESTRICT`): es un registro
contable, igual que un producto vendido. La baja de un proveedor es `active = false`, reversible.

---

## 4. Endpoints

Prefijo `/api/v1`. Todo `ADMIN`; sin superficie pública.

### Proveedores

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/suppliers` | 200 — paginado; filtra por `active` |
| `GET` | `/suppliers/{id}` | 200 |
| `POST` | `/suppliers` | 201 |
| `PUT` | `/suppliers/{id}` | 200 |
| `PATCH` | `/suppliers/{id}/status` | 200 — activa/desactiva |

Sin `DELETE`: `RESTRICT` en `purchases.supplier_id` lo haría fallar en cuanto tuviera una sola
compra, y `active = false` ya cubre "dejamos de trabajar con este proveedor".

### Compras

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/purchases` | 200 — paginado; filtra por `supplierId`, `status`, rango de fechas |
| `GET` | `/purchases/{id}` | 200 |
| `POST` | `/purchases` | 201 — nace `ORDERED` |
| `PUT` | `/purchases/{id}` | 200 — solo si `status = ORDERED` |
| `DELETE` | `/purchases/{id}` | 204 — solo si `status = ORDERED` |
| `POST` | `/purchases/{id}/receive` | 200 — pasa a `RECEIVED`, genera `PURCHASE` |
| `POST` | `/purchases/{id}/revert` | 200 — pasa a `REVERTED`, genera `ADJUSTMENT` |

`receive` y `revert` son acciones, no cambios de un campo `status` genérico — igual que `end` en
[`product-discounts.md`](product-discounts.md): cada una dispara efectos distintos (mover stock en un
sentido o en otro) y merece su propio verbo, no un `PATCH /status` que oculte que está generando
movimientos de inventario.

---

## 5. Request DTOs

### `CreateSupplierRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `name` | String | `@NotBlank`, `@Size(max = 200)` |
| `contactName` | String | `@Size(max = 150)`, opcional |
| `phone` | String | `@Size(max = 30)`, opcional |
| `email` | String | `@Email`, `@Size(max = 255)`, opcional |
| `notes` | String | opcional |

Sin cifrar — dato de contacto B2B, no PII de consumidor (regla 2). `@Email` valida formato; no lleva
HMAC porque nunca se busca por igualdad exacta, solo se lista y se filtra por texto libre.

### `UpdateSupplierRequest`

Mismos campos que `CreateSupplierRequest`.

### `ChangeSupplierStatusRequest`

`active`: `@NotNull`.

### `CreatePurchaseRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `supplierId` | UUID | `@NotNull` |
| `purchaseDate` | LocalDate | `@NotNull` |
| `invoiceNumber` | String | `@Size(max = 100)`, opcional |
| `invoiceTotal` | BigDecimal | `@NotNull`, `@PositiveOrZero` |
| `items` | List\<PurchaseItemRequest\> | `@NotEmpty` |

`PurchaseItemRequest`: `productId` (opcional), `description` (`@NotBlank`, `@Size(max = 300)`),
`quantity` (`@NotNull`, `@Positive`, hasta dos decimales), `unitCost` (`@NotNull`,
`@PositiveOrZero`).

Validación de negocio: `supplierId` debe existir y estar `active` (regla 3.5); `invoiceNumber`, si se
envía, no puede repetirse para ese proveedor.

### `UpdatePurchaseRequest`

Mismos campos que `CreatePurchaseRequest`. Se rechaza con 409 `PURCHASE_NOT_EDITABLE` si
`status <> ORDERED`.

### `RevertPurchaseRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `reason` | String | `@NotBlank`, `@Size(max = 500)` |

`POST /purchases/{id}/receive` no lleva cuerpo: recibir no es una decisión con matices, es confirmar
que lo pedido llegó tal cual se registró.

---

## 6. Response DTOs

### `SupplierResponse`

`id`, `name`, `contactName`, `phone`, `email`, `notes`, `active`, `createdAt`, `updatedAt`.

### `PurchaseResponse`

`id`, `supplierId`, `supplierName`, `purchaseDate`, `invoiceNumber`, `invoiceTotal`, `itemsTotal`,
`status`, `receivedAt`, `revertedAt`, `revertReason`, `items`, `createdAt`, `updatedAt`.

`itemsTotal` es `SUM(line_total)`, calculado en la respuesta — distinto de `invoiceTotal`, a
propósito (regla 3.4). Mostrar los dos juntos es lo que deja ver de un vistazo si hay portes o
impuestos no desglosados.

### `PurchaseItemResponse`

`id`, `productId`, `productName`, `description`, `quantity`, `unitCost`, `lineTotal`.

`productName` solo si `productId` no es `NULL`; para material a granel queda `null` y manda
`description`.

---

## 7. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `CreateSupplierUseCase` | `CreateSupplierService` | Sí |
| `UpdateSupplierUseCase` | `UpdateSupplierService` | Sí |
| `ChangeSupplierStatusUseCase` | `ChangeSupplierStatusService` | Sí |
| `CreatePurchaseUseCase` | `CreatePurchaseService` | Sí |
| `UpdatePurchaseUseCase` | `UpdatePurchaseService` | Sí |
| `DeletePurchaseUseCase` | `DeletePurchaseService` | Sí |
| `ReceivePurchaseUseCase` | `ReceivePurchaseService` | Sí |
| `RevertPurchaseUseCase` | `RevertPurchaseService` | Sí |
| `GetSuppliersUseCase` | `GetSuppliersService` | No |
| `GetPurchasesUseCase` | `GetPurchasesService` | No |
| `GetPurchaseUseCase` | `GetPurchaseService` | No |

`ReceivePurchaseService` es transaccional: el cambio de estado a `RECEIVED`, `received_at`, y un
movimiento `PURCHASE` por cada línea con producto (vía `RegisterStockMovementUseCase`,
[`inventory.md`](inventory.md)) van juntos o no van — una recepción parcialmente aplicada dejaría
stock inconsistente con lo que la compra dice haber recibido.

`RevertPurchaseService` es igual de transaccional, en sentido contrario: si cualquier línea no puede
revertirse por falta de stock, **ninguna** se revierte y la compra sigue `RECEIVED`. Una reversión
parcial dejaría el estado de la compra sin relación clara con lo que realmente pasó en el inventario.

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `SupplierReadPort` | `findById`, `findAll` |
| `SupplierWritePort` | `save` |
| `SupplierExistencePort` | `existsById`, `isActive` |
| `PurchaseReadPort` | `findById`, `findAll` |
| `PurchaseWritePort` | `save`, `delete` |

Este módulo no define un puerto propio para escribir en `stock_movements`: usa
`RegisterStockMovementUseCase` de [`inventory.md`](inventory.md) directamente, como capacidad de
aplicación, no como puerto de infraestructura — es la misma relación que `order.md` tendrá con
`SALE`.

Persistencia ([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): JPA para crear, editar y
cambiar de estado; JDBC para el listado filtrado y paginado de compras.

---

## 9. Errores

Enum `PurchasingErrorCode` en `domain/exception/purchasing/`
([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `SUPPLIER_NOT_FOUND` | 404 | No existe |
| `SUPPLIER_INACTIVE` | 409 | Se intenta crear una compra con un proveedor `active = false` |
| `SUPPLIER_HAS_PURCHASES` | 409 | No aplica hoy: no hay `DELETE` de proveedor (regla 4). Reservado por si se añade en el futuro |
| `PURCHASE_NOT_FOUND` | 404 | No existe |
| `PURCHASE_INVOICE_ALREADY_REGISTERED` | 409 | `invoice_number` ya usado por ese proveedor |
| `PURCHASE_NOT_EDITABLE` | 409 | `PUT`/`DELETE` sobre una compra que no está `ORDERED` |
| `PURCHASE_ALREADY_RECEIVED` | 409 | `receive` sobre una compra que no está `ORDERED` |
| `PURCHASE_NOT_RECEIVED` | 409 | `revert` sobre una compra que no está `RECEIVED` |
| `PURCHASE_REVERT_INSUFFICIENT_STOCK` | 409 | Parte de lo recibido ya se vendió; la reversión dejaría stock negativo |
| `PURCHASING_VALIDATION_FAILED` | 422 | Bean Validation; con `errors[]` |

`uq_purchases_supplier_invoice` nunca llega al cliente con su nombre: se traduce a
`PURCHASE_INVOICE_ALREADY_REGISTERED`.

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| Registrar la misma factura dos veces del mismo proveedor | 409 `PURCHASE_INVOICE_ALREADY_REGISTERED` |
| Misma `invoiceNumber` en dos proveedores distintos | Válido: la unicidad es por `(supplier_id, invoice_number)` |
| Recibir una compra con líneas de material a granel y de producto mezcladas | Solo las líneas con producto generan `PURCHASE`; el resto queda sin movimiento |
| Editar una compra `RECEIVED` | 409 `PURCHASE_NOT_EDITABLE` |
| Recibir una compra ya `RECEIVED` | 409 `PURCHASE_ALREADY_RECEIVED` |
| Revertir una compra `ORDERED` (nunca recibida) | 409 `PURCHASE_NOT_RECEIVED` |
| Revertir cuando parte del stock recibido ya se vendió | 409 `PURCHASE_REVERT_INSUFFICIENT_STOCK`; ninguna línea se revierte |
| Revertir una compra ya `REVERTED` | 409 `PURCHASE_NOT_RECEIVED`: `REVERTED` no es `RECEIVED` |
| Crear una compra con un proveedor `active = false` | 409 `SUPPLIER_INACTIVE` |
| Desactivar un proveedor con compras `ORDERED` pendientes | Se permite; esas compras se pueden seguir editando o recibiendo, solo se bloquean compras **nuevas** |
| Compra con `invoiceTotal` menor que la suma de sus líneas | Se acepta: la validación es `>= 0`, no una comparación con `itemsTotal` (regla 3.4); un descuento del proveedor puede dar ese resultado |

---

## 11. Alcance ajeno

- **Generación de `stock_movements`** al recibir o revertir — [`inventory.md`](inventory.md),
  `RegisterStockMovementUseCase`.
- **Consulta de reconciliación** que detectaría un desajuste si esto fallara —
  [`inventory.md`](inventory.md), sección 3.8.
