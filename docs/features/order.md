# Order

Checkout, ciclo de vida del pedido y su entrega o recogida. El módulo con más dependencias de los ya
escritos: consume [`cart.md`](cart.md) para validar y vaciar el carrito, [`product.md`](product.md)
y [`product-discounts.md`](product-discounts.md) para precios y reserva de promoción,
[`inventory.md`](inventory.md) para el movimiento `SALE`, y define el puerto que
[`payment.md`](payment.md) implementará para cobrar y reembolsar.

Sigue el patrón del módulo de referencia ([`category.md`](category.md)). Reglas transversales en
[`00-security-validation-integrity.md`](00-security-validation-integrity.md).

---

## 1. Resumen

Un pedido es un documento autocontenido: guarda su propio snapshot cifrado del comprador y de cada
línea vendida, y sobrevive a que el cliente se dé de baja o el producto cambie o se descontinúe
([ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md)). El checkout es
el único punto donde dinero, stock y descuento se mueven a la vez, y es el flujo más delicado de todo
el backend: si algo falla a medias, no puede quedar un cobro sin pedido ni un pedido sin lo que dice
haber vendido.

`orders.status` es el **estado de cumplimiento** (aceptar, preparar, entregar), no el de pago. El
pago vive en `payments`, con su propio ciclo ([`payment.md`](payment.md)). `PENDING` significa
**ya cobrado, a la espera de que la floristería lo acepte** — no "a la espera de cobro". Un pedido
que no llega a cobrarse no se crea, no queda ninguna fila en `orders`.

---

## 2. Tablas implicadas

`orders`, `order_items`, `order_status_history`, `order_deliveries`, `shipping_rates`. Esquema en
[`../database/README.md`](../database/README.md).

| Columna de `orders` | Restricción |
|---|---|
| `order_number` | `UNIQUE`; generado por la aplicación (`V9`, secuencia) — regla 3.1 |
| `channel` | `WEB`, `STORE`, `INTERFLORA` |
| `fulfillment` | `DELIVERY`, `PICKUP`, `NONE` |
| `status` | `PENDING`, `ACCEPTED`, `PREPARING`, `OUT_FOR_DELIVERY`, `DELIVERED`, `REJECTED`, `CANCELLED` |
| `customer_id` | `SET NULL` — el pedido sobrevive a la baja del cliente |
| `buyer_*_encrypted` / `buyer_*_hash` | Snapshot cifrado del comprador, ver [`00-security-validation-integrity.md`](00-security-validation-integrity.md) |
| `subtotal`, `shipping_cost`, `total` (generada) | `NUMERIC(10,2)`, `total = subtotal + shipping_cost` |
| `version` | Bloqueo optimista ([ADR-009](../architecture/ADR/ADR-009-optimistic-locking.md)) |

| Columna de `order_items` | Restricción |
|---|---|
| `product_id` | `RESTRICT` — historia de venta |
| `product_name`, `product_attributes` | Snapshot del producto en el momento de la venta |
| `unit_price`, `discount_price` | Snapshot del precio; `discount_price < unit_price` si no es `NULL` |
| `discount_id` | `SET NULL` (`V6`) — permite devolver unidades al cancelar |
| `line_total` (generada) | `COALESCE(discount_price, unit_price) * quantity` |

`order_deliveries` es 1:1 con `orders`, solo existe para `fulfillment IN ('DELIVERY', 'PICKUP')`. Para
`PICKUP` lleva únicamente `delivery_date`/`slot_from`/`slot_to`, sin dirección.
`shipping_rates` ya trae datos de arranque (tramos por distancia,
[`../database/README.md`](../database/README.md)).

---

## 3. Reglas de negocio

### 3.1 Número de pedido

`order_number_seq` (`V9`), una secuencia independiente, nunca ligada a ninguna tabla. Formato:
`{CHANNEL}-{YYYYMMDD}-{secuencia con padding a 6 dígitos}` — por ejemplo `WEB-20260826-000001`.

La secuencia **no se reinicia** nunca, ni a diario ni por canal: la fecha en el número es puramente
informativa. La unicidad la da el valor de `nextval()`, atómico bajo concurrencia sin necesidad de
`SELECT`-antes-de-`INSERT` ni reintentos por colisión — el mismo criterio que ya rige el resto del
proyecto (regla del `UPDATE` condicional en [`inventory.md`](inventory.md)).

### 3.2 El checkout en tres fases

La llamada a la pasarela de pago no puede vivir dentro de una transacción de base de datos
([`00-security-validation-integrity.md`](00-security-validation-integrity.md), sección 5.5). Eso
parte el checkout con tarjeta en tres pasos, cada uno con su propio compromiso:

```
Fase 1 — RESERVAR (transacción, se confirma)
    Por cada línea con inventario gestionado: UPDATE condicional de stock (genera SALE).
    Por cada línea con descuento activo: UPDATE condicional de quantity_sold.
    Si cualquiera falla → todo se deshace, error inmediato, la pasarela ni se llama.

Fase 2 — COBRAR (fuera de transacción, llamada externa)
    Solo si la Fase 1 tuvo éxito.
    Pasarela de pago (Stripe o equivalente), CARD_ONLINE.

Fase 3a — CONFIRMAR (transacción, se confirma)          Fase 3b — COMPENSAR (transacción, se confirma)
    Si el cobro tuvo éxito:                                  Si el cobro fue rechazado:
    orders + order_items + order_deliveries                  ADJUSTMENT que revierte la reserva de stock
    + payments (CAPTURED) + order_status_history              (regla 3.6)
    + vaciar el carrito + idempotency_keys COMPLETED          release() de las unidades de descuento
                                                               reservadas (product-discounts.md)
                                                               idempotency_keys FAILED
                                                               Error al cliente: pago rechazado,
                                                               el carrito sigue intacto
```

**Por qué reservar antes de cobrar, y no al revés.** Cobrar primero y descubrir después que el
producto se agotó dejaría dinero cobrado sin pedido que lo respalde — exactamente el caso que la
Fase 3b evita para el sentido contrario. Reservar primero significa que si algo se agotó, el cliente
lo sabe **antes** de que se le cobre nada, con el producto exacto y las unidades que quedan (regla
3.6), para que pueda ajustar la cantidad sin haber pagado por algo que no iba a recibir.

**Efectivo y datáfono no tienen Fase 2 externa.** Se capturan directamente
(`provider = 'MANUAL'`, [`../database/README.md`](../database/README.md)), así que Fases 1 y 3a se
funden en una sola transacción — no hay ventana en la que algo pueda fallar entre reservar y cobrar,
porque no hay llamada externa que esperar.

### 3.3 Idempotencia

`Idempotency-Key` obligatoria en `POST /checkout`
([ADR-011](../architecture/ADR/ADR-011-idempotent-money-operations.md)). Cubre exactamente el riesgo
de la Fase 2: un timeout de red no permite saber si la pasarela llegó a cobrar o no, y reintentar sin
esta cabecera podría cobrar dos veces por el mismo pedido.

Como este `POST` no lleva productos en el cuerpo (regla 5, se construye desde el carrito), el
`request_fingerprint` de ADR-011 incluye también un hash del contenido del carrito en ese instante, no
solo del cuerpo del `POST` — ver [`payment.md`](payment.md), regla 3.8. Un carrito modificado entre un
intento y su reintento con la misma clave se trata como cuerpo distinto (422
`IDEMPOTENCY_KEY_REUSED`), no como el mismo reintento.

### 3.4 Validación antes de la Fase 1

Antes de reservar nada, se ejecuta `ValidateCartUseCase` ([`cart.md`](cart.md), regla 3.4): quita del
carrito lo que ya no esté `ACTIVE` y, si detecta que la cantidad pedida supera el stock disponible en
ese instante, **bloquea** aquí — es una comprobación blanda, de lectura, para dar un error temprano y
específico antes de intentar la reserva atómica de la Fase 1, que es la que de verdad cuenta bajo
concurrencia.

### 3.5 Snapshot en cada línea

`product_name` y `product_attributes` se copian del producto en el instante de la venta. `unit_price`
es el precio base en ese momento; `discount_price` es el precio con la promoción aplicada, o `NULL`
si no había ninguna activa. `discount_id` enlaza con la promoción concreta —lo que permite devolver
sus unidades si el pedido se cancela (regla 3.9,
[`product-discounts.md`](product-discounts.md)).

El pedido sigue siendo legible aunque el producto cambie de nombre, cambie de atributos, se desactive
o se descontinúe después: nada de esto se lee de `products` en el futuro, todo vive ya en la línea.

### 3.6 `SALE` y la reserva de stock

Solo los productos con inventario gestionado (`stock IS NOT NULL`) participan del `UPDATE`
condicional de la Fase 1. Un producto sin gestión (`stock IS NULL`) se acepta sin comprobar
disponibilidad — es el caso explícito de material que se pide al proveedor en un plazo de horas y no
se guarda en tienda.

Si el `UPDATE` condicional de una línea afecta cero filas, la Fase 1 entera se deshace — no solo esa
línea— y el error identifica el producto concreto y, si queda alguna unidad, cuántas: el cliente
puede reducir la cantidad y reintentar, en vez de recibir un "sin stock" genérico.

### 3.7 Envío

Tarifa por tramo de distancia (`shipping_rates`,
[`../database/README.md`](../database/README.md)). La distancia se calcula geocodificando la
dirección de entrega (línea recta, ya documentado como cálculo en Java al geocodificar). Una
dirección que no se puede geocodificar se rechaza en la validación del request, antes de la Fase 1:
sin distancia no hay tarifa que aplicar.

**Más de 10 km no se reparte a domicilio** — no hay tramo para ese caso, lo rechaza la aplicación con
409 `DELIVERY_OUT_OF_RANGE`.

`free_from_amount` se compara contra el **subtotal**, antes de sumar el envío: es la cantidad
comprada la que da derecho a envío gratis, no el total que ya lo incluye.

`PICKUP` y `NONE` no calculan tarifa: `shipping_cost = 0`, ya forzado por
`chk_orders_pickup_no_shipping` / `chk_orders_none_no_shipping`.

### 3.8 Entrega o recogida

`order_deliveries` existe solo si `fulfillment IN ('DELIVERY', 'PICKUP')`. Para `DELIVERY` lleva
destinatario, dirección completa (cifrada) y franja horaria. Para `PICKUP`, solo fecha y franja — sin
destinatario ni dirección, porque el propio comprador (o quien él diga en tienda) recoge en el local.

El destinatario de la entrega es un dato distinto del comprador: `orders.buyer_*` es quien paga,
`order_deliveries.recipient_*` es quien recibe. Pueden ser la misma persona o no — un ramo puede
comprarse para otra persona.

`card_message_encrypted` es el mensaje de la tarjeta que acompaña al ramo, opcional, cifrado igual
que el resto de campos de la entrega.

### 3.9 Ciclo de estados

```
PENDING ──(aceptar)──> ACCEPTED ──(preparar)──> PREPARING ──┬─(DELIVERY: enviar)──> OUT_FOR_DELIVERY ──(entregar)──> DELIVERED
                                                              └─(PICKUP/NONE: entregar)───────────────────────────> DELIVERED

PENDING ──(rechazar)──> REJECTED
PENDING o ACCEPTED ──(cancelar)──> CANCELLED
```

Todas las transiciones las ejecuta un administrador, salvo `CANCELLED` desde `PENDING`, que también
puede iniciarla el propio cliente — arrepentirse antes de que la floristería empiece a trabajar en el
pedido no debería exigir una llamada de teléfono.

`REJECTED` solo existe desde `PENDING`: rechazar es decir "no lo aceptamos", y una vez `ACCEPTED` ya
no es rechazo, es cancelación. `OUT_FOR_DELIVERY` se salta para `PICKUP` y `NONE` — no hay "salida a
reparto" cuando no hay reparto; `PREPARING` pasa directo a `DELIVERED`, que aquí significa "recogido"
o "completado" según el caso.

Cada transición añade una fila a `order_status_history` con `changed_by_admin_id` — `NULL` cuando la
inicia el cliente.

### 3.10 Reversión al rechazar o cancelar

`REJECTED` y `CANCELLED` disparan, en la misma transacción que el cambio de estado:

- Un `ADJUSTMENT` que revierte cada `SALE` de ese pedido (por línea con inventario gestionado) —
  mismo mecanismo que la reversión de una compra en
  [`purchasing.md`](purchasing.md), regla 3.2: nunca se borra el `SALE` original, se compensa con un
  movimiento nuevo de signo contrario.
- `release()` de las unidades reservadas en cada `product_discounts` con `discount_id` no nulo
  ([`product-discounts.md`](product-discounts.md), regla 3.6).

**El reembolso no lo decide este módulo.** El cambio de estado dispara una llamada a
`OrderPaymentPort.refund()`, pero cuánto se reembolsa —todo, una parte, nada— y con qué criterio lo
define [`payment.md`](payment.md). Un `REJECTED` en `PENDING` y un `CANCELLED` en `PREPARING` (donde
la floristería ya invirtió trabajo y material) no tienen por qué devolver el mismo importe, y esa
regla no pertenece a este documento.

### 3.11 Comprador: invitado o registrado

El checkout no exige cuenta. Con sesión, `buyer_*` se rellena del perfil del cliente en el momento de
la compra; sin sesión, del formulario de checkout. En ambos casos se guarda como snapshot cifrado —
ni siquiera un cliente registrado hace que el pedido dependa de leer `customers` después
([ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md)).

Sin sesión, la Fase 3a también fija `orders.customer_id` a través de
`FindOrCreateGuestCustomerUseCase` ([`customer.md`](customer.md), regla 3.2): busca o crea una fila
`GUEST` por el email del comprador, para que un futuro registro con ese mismo email herede este
pedido como historial. El snapshot en `orders` no depende de esa fila para nada — es solo el enlace
que permite reconstruir el historial más adelante.

Un cliente registrado puede usar una dirección guardada (`customer_addresses`) o escribir una nueva
en el propio checkout; un invitado solo puede escribir una nueva, porque no tiene direcciones
guardadas.

### 3.12 Retención y purga de PII

`retention_until` se calcula al cerrar el pedido (`status` terminal), con el periodo de
`app.retention.orders-period`. Este documento no fija ningún número de días — lo determina el
requisito legal aplicable
([ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md)). La purga en sí,
quién la dispara y bajo qué condición (el cliente debe estar dado de baja, no basta con que venza el
plazo) vive en [`scheduled-tasks.md`](scheduled-tasks.md), fuera de este módulo.

---

## 4. Endpoints

Prefijo `/api/v1`.

### Público — checkout

| Método | Ruta | Rol | Devuelve |
|---|---|---|---|
| `POST` | `/checkout` | Público (con o sin sesión) | 201 — requiere `Idempotency-Key` |
| `GET` | `/orders/{orderNumber}` | Cliente dueño, o público con email+número si es invitado | 200 |
| `GET` | `/orders` | Cliente autenticado | 200 — sus propios pedidos, paginado |
| `POST` | `/orders/{id}/cancel` | Cliente dueño | 200 — solo si `status = PENDING` |

`GET /orders/{orderNumber}` para un invitado exige además el email del comprador como parámetro —
sin sesión, el número de pedido solo no basta para probar que es suyo
([`00-security-validation-integrity.md`](00-security-validation-integrity.md), autorización a nivel
de recurso).

### Administración

| Método | Ruta | Rol | Devuelve |
|---|---|---|---|
| `GET` | `/orders/all` | `ADMIN` | 200 — paginado; filtra por `status`, `channel`, rango de fechas |
| `GET` | `/orders/{id}` (admin) | `ADMIN` | 200 — incluye lo que la vista pública no expone |
| `POST` | `/orders/{id}/accept` | `ADMIN` | 200 |
| `POST` | `/orders/{id}/reject` | `ADMIN` | 200 |
| `POST` | `/orders/{id}/prepare` | `ADMIN` | 200 |
| `POST` | `/orders/{id}/ship` | `ADMIN` | 200 — solo `DELIVERY` |
| `POST` | `/orders/{id}/complete` | `ADMIN` | 200 — entregado o recogido |
| `POST` | `/orders/{id}/cancel` (admin) | `ADMIN` | 200 — desde `PENDING` o `ACCEPTED` |
| `POST` | `/orders/counter` | `ADMIN` | 201 — venta de mostrador, `STORE`/`INTERFLORA`; mecánica de pago en [`payment.md`](payment.md), regla 3.6 |

Cada transición es un verbo propio, no un `PATCH /status` genérico: `ship` solo tiene sentido si
`fulfillment = DELIVERY`, y un `PATCH` con un valor de estado arbitrario obligaría a repetir en el
controlador toda la lógica del grafo de la regla 3.9 en vez de dejar que la ruta la exprese.

---

## 5. Request DTOs

### `CheckoutRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `fulfillment` | Enum | `@NotNull`: `DELIVERY`, `PICKUP` o `NONE` |
| `paymentMethod` | Enum | `@NotNull`: `CARD_ONLINE` (único disponible en `WEB`) |
| `paymentMethodToken` | String | Tarjeta nueva; alternativa a `paymentMethodId` cuando `paymentMethod = CARD_ONLINE`, token de la pasarela, nunca datos de tarjeta |
| `paymentMethodId` | UUID | Tarjeta guardada del cliente ([`payment.md`](payment.md), regla 3.5); alternativa a `paymentMethodToken`, solo con sesión |
| `buyer` | `BuyerRequest` | `@NotNull` si no hay sesión; ignorado (se usa el perfil) si la hay |
| `addressId` | UUID | Alternativa a `delivery`; dirección guardada de un cliente registrado |
| `delivery` | `DeliveryRequest` | Obligatorio si `fulfillment = DELIVERY` y no se envía `addressId` |
| `pickup` | `PickupRequest` | Obligatorio si `fulfillment = PICKUP` |

No lleva ni productos ni precios: el pedido se construye **desde el carrito del backend**, nunca de
lo que envíe el cliente — mismo principio que el precio nunca se toma del cliente
([`00-security-validation-integrity.md`](00-security-validation-integrity.md)).

Validación de negocio: si `paymentMethod = CARD_ONLINE`, exactamente uno de `paymentMethodToken` /
`paymentMethodId` debe venir informado — ambos o ninguno es 422 `ORDER_VALIDATION_FAILED`.

`BuyerRequest`: `firstName`, `lastName`, `email` (`@Email`), `phone` — todos `@NotBlank` cuando el
bloque es obligatorio.

`DeliveryRequest`: `recipientName`, `recipientPhone`, `street`, `number`, `detail` (opcional),
`postalCode`, `deliveryDate` (`@Future`), `slotFrom`, `slotTo`, `cardMessage` (opcional,
`@Size(max = 500)`).

`PickupRequest`: `deliveryDate` (`@Future`), `slotFrom`, `slotTo`.

### `CancelOrderRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `reason` | String | `@Size(max = 500)`, opcional para el cliente, `@NotBlank` para admin |

Un cliente cancelando su propio pedido no necesita justificarse; un administrador sí, porque ese
motivo es lo que verá el equipo y, potencialmente, el propio cliente en una comunicación.

### `AcceptOrderRequest` / `RejectOrderRequest` / etc.

Las transiciones administrativas sin datos propios (`accept`, `prepare`, `ship`, `complete`) no
llevan cuerpo. `reject` lleva el mismo `reason` que `cancel`.

---

## 6. Response DTOs

### `OrderResponse`

`id`, `orderNumber`, `channel`, `fulfillment`, `status`, `items`, `subtotal`, `shippingCost`,
`total`, `placedAt`, `delivery` (si aplica), `statusHistory`.

La vista pública **no** incluye `buyer_*` descifrado más allá de lo que el propio comprador ya
conoce, ni el `customer_id`: es su propio pedido, no necesita verse reflejado con sus datos cifrados
vueltos a mostrar como si fueran nuevos.

### `OrderItemResponse`

`productId`, `productName`, `productAttributes`, `unitPrice`, `discountPrice`, `quantity`,
`lineTotal`. Viene de la propia línea, nunca de una nueva consulta a `products` — es el snapshot
(regla 3.5).

### `OrderStatusHistoryResponse`

`status`, `changedAt`, `changedByAdminName` (`null` si lo inició el cliente).

---

## 7. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `CheckoutUseCase` | `CheckoutService` | Sí — orquesta las tres fases de la regla 3.2 |
| `CancelOrderUseCase` | `CancelOrderService` | Sí |
| `AcceptOrderUseCase` | `AcceptOrderService` | Sí |
| `RejectOrderUseCase` | `RejectOrderService` | Sí |
| `PrepareOrderUseCase` | `PrepareOrderService` | Sí |
| `ShipOrderUseCase` | `ShipOrderService` | Sí |
| `CompleteOrderUseCase` | `CompleteOrderService` | Sí |
| `CreateCounterOrderUseCase` | `CreateCounterOrderService` | Sí — regla 11, pendiente |
| `GetOrderUseCase` | `GetOrderService` | No |
| `GetOrdersUseCase` | `GetOrdersService` | No |

`CheckoutService` es el más complejo del backend: coordina `ValidateCartUseCase`
([`cart.md`](cart.md)), `RegisterStockMovementUseCase` ([`inventory.md`](inventory.md)),
`DiscountReservationPort` ([`product-discounts.md`](product-discounts.md)), el puerto de pago que
define este documento, y `ClearCartUseCase` ([`cart.md`](cart.md)) — sin escribir él mismo en
ninguna tabla que no sean `orders`, `order_items`, `order_deliveries` y `order_status_history`.

`RejectOrderService` y `CancelOrderService` comparten la lógica de reversión (regla 3.10) a través de
un mismo componente interno; se exponen como casos de uso separados porque sus reglas de quién puede
invocarlos y desde qué estado son distintas (regla 3.9).

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `OrderReadPort` | `findById`, `findByOrderNumber`, `findByCustomer`, `findAllForAdmin` |
| `OrderWritePort` | `save` |
| `ShippingRatePort` | `findRateForDistance` |
| `GeocodingPort` | `geocode(address)` — distancia en línea recta desde la tienda |
| `OrderPaymentPort` | `charge`, `refund` — implementado por [`payment.md`](payment.md) |

`OrderPaymentPort` es la frontera con pagos: `order` pide "cobra esto" y "reembolsa esto", sin saber
si detrás hay Stripe, una entrada manual de efectivo, o cualquier otra pasarela — mismo principio que
`ProductInventoryPort` en [`product.md`](product.md) frente a `inventory`.

`GeocodingPort` vive fuera de la transacción de base de datos, igual que el pago: es una llamada
externa y puede fallar o tardar.

Persistencia ([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): JPA para crear el pedido y sus
líneas, y para cada transición de estado. JDBC para los listados filtrados y paginados, tanto el del
cliente como el de administración.

---

## 9. Errores

Enum `OrderErrorCode` en `domain/exception/order/`
([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `ORDER_NOT_FOUND` | 404 | No existe, o no pertenece a quien pregunta |
| `CHECKOUT_INSUFFICIENT_STOCK` | 422 | Fase 1: una línea agotó su stock; con `availableQuantity` |
| `CHECKOUT_DISCOUNT_EXHAUSTED` | 409 | Fase 1: la promoción se agotó entre que se vio y se pagó |
| `CHECKOUT_PAYMENT_DECLINED` | 402 | Fase 2: la pasarela rechaza el cobro; Fase 3b ya revirtió la reserva |
| `DELIVERY_OUT_OF_RANGE` | 422 | Dirección a más de 10 km; no hay tramo de tarifa |
| `DELIVERY_ADDRESS_NOT_GEOCODABLE` | 422 | La dirección no se puede localizar |
| `ORDER_INVALID_TRANSITION` | 409 | Acción fuera del grafo de la regla 3.9 (p. ej. `ship` sobre un `PICKUP`) |
| `ORDER_NOT_CANCELABLE` | 409 | Cliente intentando cancelar algo que no está `PENDING` |
| `RESOURCE_MODIFIED` | 409 | Conflicto de bloqueo optimista (ADR-009) |
| `ORDER_VALIDATION_FAILED` | 422 | Bean Validation; con `errors[]` |

`CHECKOUT_PAYMENT_DECLINED` usa 402 (`Payment Required`), no 409 ni 422: es el único caso del backend
donde el estado HTTP describe con precisión lo que pasó.

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| Dos pestañas confirman el mismo carrito a la vez | Cada `POST /checkout` lleva su propia `Idempotency-Key`; sin ella compartida, pueden generarse dos pedidos — es responsabilidad del frontend no duplicar la clave entre pestañas |
| Reintento de `POST /checkout` con la misma clave tras éxito | Se responde con el pedido ya creado, sin repetir ninguna fase (ADR-011) |
| Reintento con la misma clave tras un rechazo de pago | La Fase 3b ya liberó la reserva; la fila de `idempotency_keys` quedó `FAILED`, así que el reintento vuelve a intentar desde la Fase 1. Si el carrito no cambió, mismo fingerprint, se ejecuta de nuevo; si cambió, 422 `IDEMPOTENCY_KEY_REUSED` (regla 3.3, `payment.md` 3.8) |
| Producto sin gestión de inventario, sin stock físico real | Se vende igual: la regla 3.6 no comprueba nada para `stock IS NULL`, por diseño |
| Carrito vaciado por otra pestaña justo antes del checkout | `ValidateCartUseCase` no tiene nada que validar; el checkout falla con carrito vacío antes de la Fase 1 |
| Cliente cancela su pedido en `PREPARING` | 409 `ORDER_NOT_CANCELABLE`: pasado `ACCEPTED`, solo el admin puede |
| Admin intenta `ship` sobre un pedido `PICKUP` | 409 `ORDER_INVALID_TRANSITION`: `PICKUP` no pasa por `OUT_FOR_DELIVERY` |
| Rechazar un pedido ya `ACCEPTED` | 409 `ORDER_INVALID_TRANSITION`: `REJECTED` solo existe desde `PENDING` |
| Dirección de entrega a 10,5 km de la tienda | 422 `DELIVERY_OUT_OF_RANGE` |
| Subtotal exactamente igual al `free_from_amount` del tramo | Envío gratis: la comparación es `>=`, no `>` |

---

## 11. Alcance ajeno

- **Cobro y reembolso reales** — [`payment.md`](payment.md) implementa `OrderPaymentPort`; este
  documento solo define qué necesita pedirle.
- **Reserva y liberación de unidades de descuento** — [`product-discounts.md`](product-discounts.md).
- **`SALE` y su reversión (`ADJUSTMENT`)** — [`inventory.md`](inventory.md).
- **Validación y vaciado del carrito** — [`cart.md`](cart.md).
- **Ejecución y condiciones de la purga de PII** — [`scheduled-tasks.md`](scheduled-tasks.md).
- **Baja de cliente** — [`customer.md`](customer.md).

---

## 12. Decisiones cerradas por `payment.md`

Los dos pendientes que dejó esta sección se resolvieron al escribir
[`payment.md`](payment.md), que es el módulo que los gobierna:

1. **Pago de `STORE`/`INTERFLORA`** — `POST /orders/counter` confirmado con Fases 1+3a fundidas, sin
   pasarela externa; `INTERFLORA` añade su propio `method` (`V10`) con captura inmediata garantizada y
   liquidación diferida fuera del backend (`payment.md`, reglas 3.6-3.7).
2. **Fingerprint de `Idempotency-Key` en `POST /checkout`** — incluye el contenido del carrito, no solo
   el cuerpo del `POST` (`payment.md`, regla 3.8; reflejado aquí en la regla 3.3).

Sin pendientes abiertos en este documento.
