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

`orders.status` es el **estado de cumplimiento** (preparar, entregar), no el de pago. El pago vive en
`payments`, con su propio ciclo ([`payment.md`](payment.md)). `PENDING` significa **ya cobrado, en
cola para que la floristería lo prepare** — no "a la espera de cobro" ni de que un administrador dé
el visto bueno antes de empezar: el pedido pagado entra directo al flujo de trabajo, y el
administrador solo interviene si **no puede** cumplirlo (rechazo, regla 3.9). Un pedido que no llega
a cobrarse no se crea, no queda ninguna fila en `orders`.

---

## 2. Tablas implicadas

`orders`, `order_items`, `order_status_history`, `order_deliveries`, `shipping_rates`. Esquema en
[`../database/README.md`](../database/README.md). Escribe además en `notifications`
([`notification.md`](notification.md), regla 3.4), siempre dentro de su propia transacción.

| Columna de `orders` | Restricción |
|---|---|
| `order_number` | `UNIQUE`; generado por la aplicación (`V9`, secuencia) — regla 3.1 |
| `channel` | `WEB`, `STORE`, `INTERFLORA` |
| `fulfillment` | `DELIVERY`, `PICKUP`, `NONE` |
| `status` | `PENDING`, `PREPARING`, `OUT_FOR_DELIVERY`, `DELIVERED`, `REJECTED`, `CANCELLED` |
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

### 3.2 El checkout en dos pasos

La llamada a la pasarela de pago no puede vivir dentro de una transacción de base de datos
([`00-security-validation-integrity.md`](00-security-validation-integrity.md), sección 5.5). Para
`CARD_ONLINE` esto es además **asíncrono**: el TPV Virtual contratado (Redsys) cobra por
redirección, no de forma síncrona dentro de la petición — el cliente paga en la pasarela de Redsys, y
Redsys confirma el resultado más tarde mediante una notificación servidor-a-servidor, no mediante el
redirect del navegador ([ADR-017](../architecture/ADR/ADR-017-card-payment-via-redsys-redirect.md)).
Eso reparte el checkout con tarjeta así:

```
Fase 1 — RESERVAR (transacción, se confirma) — igual para todos los métodos de pago
    Por cada línea con inventario gestionado: UPDATE condicional de stock (genera SALE).
    Por cada línea con descuento activo: UPDATE condicional de quantity_sold.
    Si cualquiera falla → todo se deshace, error inmediato, Redsys ni se menciona.
    Si tiene éxito: inserta idempotency_keys PENDING con el payload cifrado de lo que hará
    falta para crear el pedido (ADR-017) — todavía no escribe en orders.
    Responde con la URL y los parámetros firmados del formulario de redirección a Redsys.

Paso 2 — PAGAR EN REDSYS (fuera de este backend, asíncrono, minutos no milisegundos)
    Solo para CARD_ONLINE. El cliente completa el pago (y el 3DS si aplica) en la pasarela.
    Redsys confirma el resultado después, con una notificación servidor-a-servidor —
    ADR-017 — no con el regreso del navegador, que puede no llegar a producirse.

Fase 3a — CONFIRMAR (transacción, se confirma)          Fase 3b — COMPENSAR (transacción, se confirma)
    Si la notificación aprueba el cobro:                    Si la notificación rechaza el cobro, o
    orders + order_items + order_deliveries                 pasan 10 minutos sin notificación:
    + payments (CAPTURED) + order_status_history             ADJUSTMENT que revierte la reserva de
    + notifications (aviso al cliente y a la tienda)          stock (regla 3.6)
    + vaciar el carrito + idempotency_keys COMPLETED          release() de las unidades de descuento
                                                               reservadas (product-discounts.md)
                                                               idempotency_keys FAILED
```

**Por qué reservar antes de cobrar, y no al revés.** Cobrar primero y descubrir después que el
producto se agotó dejaría dinero cobrado sin pedido que lo respalde — exactamente el caso que la
Fase 3b evita para el sentido contrario. Reservar primero significa que si algo se agotó, el cliente
lo sabe **antes** de que se le cobre nada, con el producto exacto y las unidades que quedan (regla
3.6), para que pueda ajustar la cantidad sin haber pagado por algo que no iba a recibir.

**Efectivo, datáfono e INTERFLORA no tienen Paso 2 externo.** Se capturan directamente
(`provider = 'MANUAL'`, [`../database/README.md`](../database/README.md)), así que Fases 1 y 3a se
funden en una sola transacción — no hay ventana en la que algo pueda fallar entre reservar y cobrar,
porque no hay llamada externa que esperar. La redirección solo existe para `CARD_ONLINE`
([`payment.md`](payment.md), regla 3.1); `POST /orders/counter` (regla 11) sigue funcionando así sin
ningún cambio.

El detalle del protocolo con Redsys (firma, payload cifrado, caducidad de la reserva y resolución del
lado del cliente tras volver de la pasarela) vive en
[ADR-017](../architecture/ADR/ADR-017-card-payment-via-redsys-redirect.md) — no se repite aquí.

### 3.3 Idempotencia

`Idempotency-Key` obligatoria en `POST /checkout`
([ADR-011](../architecture/ADR/ADR-011-idempotent-money-operations.md)). Cubre exactamente el riesgo
del Paso 2: un timeout de red o una notificación que tarda no permiten saber si la pasarela llegó a
cobrar o no, y reintentar sin esta cabecera podría cobrar dos veces por el mismo pedido.

Como este `POST` no lleva productos en el cuerpo (regla 5, se construye desde el carrito), el
`request_fingerprint` de ADR-011 incluye también un hash del contenido del carrito en ese instante, no
solo del cuerpo del `POST` — ver [`payment.md`](payment.md), regla 3.8. Un carrito modificado entre un
intento y su reintento con la misma clave se trata como cuerpo distinto (422
`IDEMPOTENCY_KEY_REUSED`), no como el mismo reintento.

Con el cobro por redirección
([ADR-017](../architecture/ADR/ADR-017-card-payment-via-redsys-redirect.md)), la fila
`idempotency_keys` de `POST /checkout` no se resuelve en la misma petición que la crea: queda
`PENDING` mientras el cliente paga en Redsys, y la resuelve la notificación asíncrona (`COMPLETED` o
`FAILED`), o la tarea de limpieza si pasan 10 minutos sin noticias de Redsys
([`scheduled-tasks.md`](scheduled-tasks.md), regla 3.3). `GET /checkout/{idempotencyKey}/status`
(regla 4) es la forma en que el frontend pregunta "¿en qué quedó esto?" al volver de la pasarela, sin
inventar un segundo mecanismo de resolución.

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
sin distancia no hay tarifa que aplicar. Un resultado de Google cuenta como dirección localizada solo
si es lo bastante preciso (calle, portal o punto de interés; no un código postal, ciudad o país), porque
la consulta termina siempre en ", Spain" y una dirección inventada caería si no en el centro del país. Un
`partial_match` sí invalida el resultado: la dirección tecleada debe ser real, y Google lo activa cuando
solo ha podido casar una parte (por ejemplo un portal que no existe en esa calle); el frontal ofrece
entonces corregir la dirección u otras opciones. También se exige que el código postal del resultado
coincida con el indicado. La clave
de la API viaja en la URL de la petición (Google no ofrece cabecera alternativa para una clave simple), así
que no debe activarse ningún registro de peticiones del cliente HTTP ni de proxies delante del backend
para esa llamada. Una clave denegada, una cuota agotada, un fallo de red o un tiempo de espera agotado no juzgan la
dirección: se responden con `DELIVERY_GEOCODING_UNAVAILABLE` (502), distinto de una dirección no real,
para no decirle a un cliente que su dirección es falsa por una avería nuestra. Antes de reservar nada, como
los otros dos errores de reparto.

**El radio de reparto es el del tramo activo más lejano** (hoy 10 km con los tramos de arranque): una
distancia que ningún tramo activo cubre se rechaza con 422 `DELIVERY_OUT_OF_RANGE`. No hay una constante
de radio aparte de los tramos, para que no puedan desalinearse.

**Los tramos los administra un `ADMIN` desde el panel** (endpoints de la sección 4). Reglas que la
aplicación exige tras cada cambio, sobre el conjunto de tramos **activos**:

- Debe haber al menos uno (sin tramos activos no se reparte a nadie; desactivar el reparto entero por
  descuido no debe ser posible).
- El primero empieza en 0 km y cada tramo empieza donde acaba el anterior: sin huecos ni solapes. Un hueco
  daría un rechazo a una dirección más cercana que otra que sí se acepta.
- `price >= 0`, `max_km > min_km`, `free_from_amount` nulo o positivo (ya forzado por la base de datos,
  que además impide el solape de tramos activos con `ex_shipping_rates_no_overlap`).

Cambiar un tramo no toca pedidos ya hechos: cada pedido guarda su `shipping_cost` (regla 3.5, ADR-007). No
se borran tramos, se desactivan. La ubicación de la tienda (origen de las distancias) sigue en
configuración, no en el panel: casi nunca cambia y un error ahí falsea todas las distancias.

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
PENDING ──(preparar)──> PREPARING ──┬─(DELIVERY: enviar)──> OUT_FOR_DELIVERY ──(entregar)──> DELIVERED
                                     └─(PICKUP/NONE: entregar)──────────────────────────────> DELIVERED

PENDING ──(rechazar)──────────────> REJECTED
PENDING o PREPARING ──(cancelar)──> CANCELLED
```

Un pedido pagado entra directo en `PENDING`, listo para prepararse — **no hace falta que un
administrador lo acepte antes**: simplificación deliberada, la floristería no necesita dar un visto
bueno explícito a algo que ya está cobrado y en su cola de trabajo. Todas las transiciones las
ejecuta un administrador, salvo `CANCELLED` desde `PENDING`, que también puede iniciarla el propio
cliente — arrepentirse antes de que la floristería empiece a trabajar en el pedido no debería exigir
una llamada de teléfono.

`REJECTED` es el "no puedo cumplir este pedido" del administrador **antes** de empezar a prepararlo —
dispara reembolso y aviso al cliente (regla 3.10, [`payment.md`](payment.md) regla 3.3). `CANCELLED`
cubre lo mismo pero **después** de que la floristería ya invirtió trabajo o material preparándolo
(desde `PREPARING`), o el arrepentimiento del propio cliente (desde `PENDING`). `OUT_FOR_DELIVERY` se
salta para `PICKUP` y `NONE` — no hay "salida a reparto" cuando no hay reparto; `PREPARING` pasa
directo a `DELIVERED`, que aquí significa "recogido" o "completado" según el caso.

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
- Una fila de `notifications` con el aviso que corresponda
  ([`notification.md`](notification.md), regla 3.1): al cliente si quien rechaza o cancela es el
  administrador, con su motivo; a la floristería si quien cancela es el cliente.

**El reembolso no lo decide este módulo.** El cambio de estado dispara una llamada a
`OrderPaymentPort.refund()`; cuánto se reembolsa lo define [`payment.md`](payment.md) regla 3.3 — hoy,
siempre el importe total, tanto si es un `REJECTED` desde `PENDING` como un `CANCELLED` desde
`PENDING` o `PREPARING`. Esa regla no pertenece a este documento.

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
| `POST` | `/checkout` | Público (con o sin sesión) | 202 — requiere `Idempotency-Key`; devuelve los datos de redirección a Redsys, no un pedido ([ADR-017](../architecture/ADR/ADR-017-card-payment-via-redsys-redirect.md)) |
| `GET` | `/checkout/{idempotencyKey}/status` | Público | 200 — `PENDING`/`COMPLETED`/`FAILED`, para que el frontend resuelva el checkout al volver de Redsys |
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
| `POST` | `/orders/{id}/reject` | `ADMIN` | 200 — solo desde `PENDING` |
| `POST` | `/orders/{id}/prepare` | `ADMIN` | 200 |
| `POST` | `/orders/{id}/ship` | `ADMIN` | 200 — solo `DELIVERY` |
| `POST` | `/orders/{id}/complete` | `ADMIN` | 200 — entregado o recogido |
| `POST` | `/orders/{id}/cancel` (admin) | `ADMIN` | 200 — desde `PENDING` o `PREPARING` |
| `POST` | `/orders/counter` | `ADMIN` | 201 — venta de mostrador, `STORE`/`INTERFLORA`; mecánica de pago en [`payment.md`](payment.md), regla 3.6 |
| `GET` | `/shipping-rates` | `ADMIN` | 200 — todos los tramos, activos e inactivos, por `min_km`; incluye el radio de reparto vigente (el `max_km` del último tramo activo) |
| `POST` | `/shipping-rates` | `ADMIN` | 201 — crea un tramo (`minKm`, `maxKm`, `price`, `freeFromAmount` opcional, `active`) |
| `PUT` | `/shipping-rates/{id}` | `ADMIN` | 200 — sustituye los campos del tramo, incluido `active`; no hay borrado (regla 3.7) |

Las tres operaciones de tramos comprueban las reglas de coherencia de la regla 3.7 sobre el conjunto
**resultante** y responden 422 `SHIPPING_RATES_INVALID` si no se cumplen, sin cambiar nada. Cada escritura
queda en el registro de auditoría de administración
([ADR-010](../architecture/ADR/ADR-010-admin-audit-log.md)). Dos administradores editando a la vez se
serializan bloqueando las filas de `shipping_rates` durante el cambio, sin columna `version` (son pocas filas
y un solo cambio a la vez; [ADR-009](../architecture/ADR/ADR-009-optimistic-locking.md) no las incluye).

Cada transición es un verbo propio, no un `PATCH /status` genérico: `ship` solo tiene sentido si
`fulfillment = DELIVERY`, y un `PATCH` con un valor de estado arbitrario obligaría a repetir en el
controlador toda la lógica del grafo de la regla 3.9 en vez de dejar que la ruta la exprese.

`POST /orders/counter` exige también cabecera `Idempotency-Key`, igual que `POST /checkout`
([ADR-011](../architecture/ADR/ADR-011-idempotent-money-operations.md),
[`00-security-validation-integrity.md`](00-security-validation-integrity.md), sección 6): reserva
stock y captura el pago en una sola transacción (Fases 1+3a fundidas, [`payment.md`](payment.md),
regla 3.6), y un reintento de red no debe duplicar ni el pedido ni el cobro en efectivo. El
`request_fingerprint` es el hash por defecto del cuerpo de la petición, sin la excepción de carrito
que lleva `POST /checkout`: este `POST` sí recibe los productos directamente en el cuerpo.

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
motivo **es el cuerpo del correo que recibe el cliente** ([`notification.md`](notification.md),
regla 3.1), además de quedar para el equipo.

Se guarda en `order_status_history.reason` (`V14`), columna que no existía: este documento lo pedía
desde que se escribió y el esquema no tenía dónde ponerlo. `chk_order_status_history_reason_required`
lo exige también en la base de datos cuando quien rechaza o cancela es un administrador.

### `RejectOrderRequest` / etc.

Las transiciones administrativas sin datos propios (`prepare`, `ship`, `complete`) no llevan cuerpo.
`reject` lleva el mismo `reason` que `cancel`.

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
| `CheckoutUseCase` | `CheckoutService` | Sí — reserva stock/descuento y genera la redirección a Redsys (regla 3.2) |
| `ConfirmCheckoutUseCase` | `ConfirmCheckoutService` | Sí — resuelve la notificación de Redsys, aprobada o rechazada (regla 3.2, [ADR-017](../architecture/ADR/ADR-017-card-payment-via-redsys-redirect.md)) |
| `GetCheckoutStatusUseCase` | `GetCheckoutStatusService` | No — `GET /checkout/{idempotencyKey}/status` |
| `CancelOrderUseCase` | `CancelOrderService` | Sí |
| `GetShippingRatesUseCase` | `GetShippingRatesService` | No |
| `CreateShippingRateUseCase` | `CreateShippingRateService` | Sí |
| `UpdateShippingRateUseCase` | `UpdateShippingRateService` | Sí |
| `RejectOrderUseCase` | `RejectOrderService` | Sí |
| `PrepareOrderUseCase` | `PrepareOrderService` | Sí |
| `ShipOrderUseCase` | `ShipOrderService` | Sí |
| `CompleteOrderUseCase` | `CompleteOrderService` | Sí |
| `CreateCounterOrderUseCase` | `CreateCounterOrderService` | Sí — regla 11, pendiente |
| `GetOrderUseCase` | `GetOrderService` | No |
| `GetOrdersUseCase` | `GetOrdersService` | No |

`CheckoutService` coordina `ValidateCartUseCase` ([`cart.md`](cart.md)),
`RegisterStockMovementUseCase` ([`inventory.md`](inventory.md)) y `DiscountReservationPort`
([`product-discounts.md`](product-discounts.md)) para la reserva, y solo escribe en
`idempotency_keys` — no toca `orders`
([ADR-017](../architecture/ADR/ADR-017-card-payment-via-redsys-redirect.md)). `ConfirmCheckoutService`
es quien de verdad materializa el pedido cuando Redsys confirma: coordina el puerto de pago que
define este documento y `ClearCartUseCase` ([`cart.md`](cart.md)), y escribe en `orders`,
`order_items`, `order_deliveries`, `order_status_history` e `idempotency_keys`.

`RejectOrderService` y `CancelOrderService` comparten la lógica de reversión (regla 3.10) a través de
un mismo componente interno; se exponen como casos de uso separados porque sus reglas de quién puede
invocarlos y desde qué estado son distintas (regla 3.9).

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `OrderReadPort` | `findById`, `findByOrderNumber`, `findByCustomer`, `findAllForAdmin` |
| `OrderWritePort` | `save` |
| `ShippingRatePort` | `findRateForDistance`, `findMaxDeliveryDistanceKm` (radio vigente); para el panel, `findAll`, `findAllForUpdate` (bloqueo de filas), `findById`, `save` |
| `GeocodingPort` | `geocode(address)` — distancia en línea recta desde la tienda |
| `OrderPaymentPort` | `charge`, `refund` — implementado por [`payment.md`](payment.md) |

`OrderPaymentPort` es la frontera con pagos: `order` pide "cobra esto" y "reembolsa esto", sin saber
si detrás hay Redsys, una entrada manual de efectivo, o cualquier otra pasarela — mismo principio que
`ProductInventoryPort` en [`product.md`](product.md) frente a `inventory`. Para `CARD_ONLINE`, `charge`
no devuelve un cobro resuelto: devuelve los datos de redirección a Redsys, y es
`ConfirmCheckoutUseCase` quien resuelve el resultado real cuando llega la notificación
([ADR-017](../architecture/ADR/ADR-017-card-payment-via-redsys-redirect.md)).

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
| `CHECKOUT_NOT_FOUND` | 404 | `GET /checkout/{idempotencyKey}/status`: no existe una operación de checkout con esa clave |
| `CHECKOUT_INSUFFICIENT_STOCK` | 422 | Fase 1: una línea agotó su stock; con `availableQuantity` |
| `CHECKOUT_DISCOUNT_EXHAUSTED` | 409 | Fase 1: la promoción se agotó entre que se vio y se pagó |
| `CHECKOUT_PAYMENT_DECLINED` | 402 | La notificación de Redsys rechaza el cobro (Paso 3b); la reserva ya se revirtió |
| `DELIVERY_OUT_OF_RANGE` | 422 | Dirección a más de 10 km; no hay tramo de tarifa. El cuerpo lleva `maxDistanceKm` (10) para que el frontal explique el límite y ofrezca la recogida; nunca la distancia, la dirección ni coordenadas |
| `DELIVERY_ADDRESS_NOT_GEOCODABLE` | 422 | La dirección no es real o no es lo bastante precisa (calle, portal), o su código postal no coincide con el localizado; el cliente puede corregirla |
| `DELIVERY_GEOCODING_UNAVAILABLE` | 502 | El servicio de geocodificación falla (clave denegada, cuota, red, tiempo de espera); la dirección no está juzgada, y el frontal ofrece reintentar o recoger en tienda |
| `ORDER_INVALID_TRANSITION` | 409 | Acción fuera del grafo de la regla 3.9 (p. ej. `ship` sobre un `PICKUP`) |
| `ORDER_NOT_CANCELABLE` | 409 | Cliente intentando cancelar algo que no está `PENDING` |
| `RESOURCE_MODIFIED` | 409 | Conflicto de bloqueo optimista (ADR-009) |
| `SHIPPING_RATE_NOT_FOUND` | 404 | `PUT /shipping-rates/{id}` sobre un tramo que no existe |
| `SHIPPING_RATES_INVALID` | 422 | El conjunto de tramos activos resultante incumple la regla 3.7 (sin tramos activos, no empieza en 0, hueco o solape); el mensaje dice cuál |
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
| Cliente cancela su pedido en `PREPARING` | 409 `ORDER_NOT_CANCELABLE`: fuera de `PENDING`, solo el admin puede |
| Admin intenta `ship` sobre un pedido `PICKUP` | 409 `ORDER_INVALID_TRANSITION`: `PICKUP` no pasa por `OUT_FOR_DELIVERY` |
| Rechazar un pedido ya en `PREPARING` | 409 `ORDER_INVALID_TRANSITION`: `REJECTED` solo existe desde `PENDING` |
| Dirección de entrega a 10,5 km de la tienda | 422 `DELIVERY_OUT_OF_RANGE` |
| Subtotal exactamente igual al `free_from_amount` del tramo | Envío gratis: la comparación es `>=`, no `>` |
| Reintento de `POST /orders/counter` con la misma `Idempotency-Key` tras éxito | Se responde con el pedido ya creado, sin repetir la reserva de stock ni el cobro (ADR-011) |

---

## 11. Alcance ajeno

- **Cobro y reembolso reales** — [`payment.md`](payment.md) implementa `OrderPaymentPort`; este
  documento solo define qué necesita pedirle.
- **Reserva y liberación de unidades de descuento** — [`product-discounts.md`](product-discounts.md).
- **`SALE` y su reversión (`ADJUSTMENT`)** — [`inventory.md`](inventory.md).
- **Validación y vaciado del carrito** — [`cart.md`](cart.md).
- **Ejecución y condiciones de la purga de PII** — [`scheduled-tasks.md`](scheduled-tasks.md).
- **Baja de cliente** — [`customer.md`](customer.md).
- **Correos de confirmación, rechazo, cancelación y entrega** — [`notification.md`](notification.md);
  este documento solo registra la notificación en su transacción.

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
