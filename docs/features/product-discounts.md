# Product discounts

Promociones sobre un producto: precio rebajado con vigencia y límite opcional de unidades.

Parte del módulo `product`, en documento aparte porque tiene ciclo propio — vigencia sin solape,
reserva de unidades ligada al pedido y devolución al cancelar. El producto en sí está en
[`product.md`](product.md).

Reglas transversales en
[`00-security-validation-integrity.md`](00-security-validation-integrity.md).

---

## 1. Resumen

Un descuento no es un porcentaje guardado: es **un precio promocional con fecha de inicio, fecha de
fin y, opcionalmente, un número máximo de unidades**. Mientras está vigente, ese precio sustituye al
del catálogo.

El panel puede ofrecer al administrador un campo de porcentaje, pero lo que viaja a la API y lo que
se guarda es el precio final. Un 15% sobre 24,99 € da 21,2415 €, y quien decide el redondeo debe ser
la persona que anuncia la promoción, no el backend.

---

## 2. Tablas implicadas

`product_discounts`, y desde la migración `V6` también `order_items.discount_id`.

| Columna | Restricción |
|---|---|
| `product_id` | `REFERENCES products ON DELETE CASCADE` |
| `original_price` | El "antes" que muestra la web, congelado al crear |
| `sale_price` | `chk_product_discounts_price`: `>= 0` y **estrictamente menor** que `original_price` |
| `starts_at`, `ends_at` | `chk_product_discounts_period`: `starts_at < ends_at` |
| `quantity_limit` | `NULL` (sin límite) o `> 0` |
| `quantity_sold` | `>= 0` y nunca mayor que `quantity_limit` |

Dos garantías que da la base de datos y que la aplicación no necesita reimplementar:

```sql
CONSTRAINT ex_product_discounts_no_overlap EXCLUDE USING gist (
    product_id WITH =,
    tstzrange(starts_at, ends_at, '[)') WITH &&
)
```

El rango es **semiabierto**: 10:00–12:00 y 12:00–14:00 son consecutivos y no solapan; 10:00–12:00 y
11:00–13:00 sí, y el `EXCLUDE` los rechaza. Un producto nunca tiene dos promociones a la vez, y eso
lo garantiza el índice, no un `SELECT` previo.

```sql
CONSTRAINT chk_product_discounts_sold
    CHECK (quantity_sold >= 0 AND (quantity_limit IS NULL OR quantity_sold <= quantity_limit))
```

Nunca se venden más unidades promocionales de las permitidas, ni siquiera bajo concurrencia.

### Por qué `original_price` vive en la fila

Un `CHECK` no puede consultar otra tabla, así que `sale_price < original_price` solo es verificable
si el precio anterior está en la propia fila. Además congela el "antes" que se anunció: aunque el
precio del catálogo cambie después, la promoción sigue diciendo lo que decía cuando se publicó.

---

## 3. Reglas de negocio

### 3.1 Precio vigente

Un producto tiene precio promocional si existe una fila cuya vigencia contiene el instante actual:

```sql
WHERE product_id = :id AND tstzrange(starts_at, ends_at, '[)') @> now()
```

La consulta aprovecha el índice GiST que ya crea el `EXCLUDE`; no hace falta índice adicional.

Un descuento que agotó su `quantity_limit` **sigue vigente en el tiempo pero ya no se aplica**: el
producto vuelve a venderse a su precio base. Son dos condiciones distintas, y confundirlas dejaría el
producto sin precio aplicable.

### 3.2 El precio base no se toca mientras hay promoción

Cambiar `products.price` con un descuento vigente se rechaza con 409
`PRODUCT_HAS_ACTIVE_DISCOUNT`. El administrador cierra la promoción y luego cambia el precio.

Sin esta regla la web mostraría un tachado falso: `original_price` seguiría anunciando un "antes" que
ya no existe. Actualizar `original_price` junto al precio tampoco vale — reescribiría una promoción
ya anunciada, y podría violar `chk_product_discounts_price` si el precio nuevo bajara del
`sale_price`.

### 3.3 Qué se puede editar

Depende de si la promoción ha empezado y de si ha vendido algo:

| Estado | `starts_at` | `ends_at` | `quantity_limit` | `sale_price` |
|---|---|---|---|---|
| Aún no empezada (`starts_at > now()`) | Sí | Sí | Sí | Sí |
| Vigente, sin ventas | No | Sí | Sí | Sí |
| Vigente, con ventas | No | Sí | Sí | **No** |
| Terminada | No | No | No | No |

El criterio: **nunca se cambia el precio que alguien ya pagó o vio anunciado**. Mientras nadie ha
comprado, el precio es todavía una intención y puede corregirse. En cuanto hay una venta, ese precio
es parte de una transacción real y se congela.

Límites de las ediciones permitidas:

- `ends_at` no puede quedar por debajo de `now()`. Adelantarlo hasta `now()` es exactamente terminar
  la promoción (regla 3.4).
- `quantity_limit` no puede bajar de `quantity_sold`: lo impide `chk_product_discounts_sold`, y se
  traduce a 422 `DISCOUNT_LIMIT_BELOW_SOLD`.
- Alargar `ends_at` puede chocar con otra promoción ya programada para ese producto. Lo rechaza el
  `EXCLUDE`, traducido a 409 `DISCOUNT_OVERLAP`.

### 3.4 Terminar una promoción

No se borra: se cierra con `ends_at = now()`. La fila sobrevive, así que se conserva qué se vendió
con esa promoción y a qué precio, y `order_items.discount_id` sigue apuntando a algo real.

Cerrarla libera además el rango de tiempo restante, así que puede crearse otra promoción para ese
producto a partir de ese momento sin chocar con el `EXCLUDE`.

Una promoción **aún no empezada** sí puede borrarse físicamente: nadie la ha visto, no tiene ventas y
no hay nada que conservar. Es el caso de un error de configuración recién cometido.

### 3.5 Reserva de unidades

Cuando `quantity_limit` no es `NULL`, cada venta reserva unidades con un `UPDATE` condicional, nunca
leyendo antes:

```sql
UPDATE product_discounts
SET quantity_sold = quantity_sold + :quantity
WHERE id = :discountId
  AND tstzrange(starts_at, ends_at, '[)') @> now()
  AND (quantity_limit IS NULL OR quantity_sold + :quantity <= quantity_limit);
```

Cero filas afectadas significa que la promoción se agotó o expiró entre que el cliente la vio y
confirmó. No es un error del sistema: el precio vuelve al base y el checkout informa al cliente antes
de cobrar nada.

La reserva y la creación del pedido van en **la misma transacción**. Reservar sin crear el pedido
consumiría unidades que nadie compró; crear el pedido sin reservar vendería más unidades de las
prometidas.

Un producto sin `quantity_limit` no reserva nada: no hay contador que agotar.

### 3.6 Devolución de unidades

Las unidades vuelven cuando el pedido no llega a buen término:

| Situación | Devuelve |
|---|---|
| Pedido cancelado por el cliente | Sí |
| Pedido rechazado por la tienda | Sí |
| Pago fallido o preautorización expirada | Sí |
| Pedido entregado | No |
| Pedido devuelto o reembolsado después de entregar | No |

Un reembolso posterior a la entrega no devuelve unidades: la promoción se consumió, el producto salió
de la tienda, y reponer la unidad se la quitaría a otro cliente por un hecho que ya ocurrió.

La devolución usa el mismo mecanismo, en sentido contrario y en la transacción del cambio de estado
del pedido:

```sql
UPDATE product_discounts
SET quantity_sold = quantity_sold - :quantity
WHERE id = :discountId AND quantity_sold >= :quantity;
```

**`order_items.discount_id` es lo que hace posible esto** (migración `V6`). Sin él habría que
adivinar qué promoción se aplicó, a partir del producto y la fecha del pedido, sobre filas que pueden
haber terminado o desaparecido.

Si `discount_id` quedó a `NULL` porque la promoción se borró, no hay nada que devolver y la
cancelación continúa sin error: la fila que contaba esas unidades ya no existe.

---

## 4. Endpoints

Prefijo `/api/v1`. Todo es administración: el público ve el descuento reflejado en
`ProductResponse.effectivePrice`, nunca la promoción como recurso.

| Método | Ruta | Rol | Devuelve |
|---|---|---|---|
| `GET` | `/products/{id}/discounts` | `ADMIN` | 200 — historial completo del producto |
| `POST` | `/products/{id}/discounts` | `ADMIN` | 201 |
| `PUT` | `/discounts/{id}` | `ADMIN` | 200 — según la regla 3.3 |
| `POST` | `/discounts/{id}/end` | `ADMIN` | 200 — cierra con `ends_at = now()` |
| `DELETE` | `/discounts/{id}` | `ADMIN` | 204 — solo si aún no ha empezado |

`POST /discounts/{id}/end` es una acción, no un estado, así que no encaja en el `PATCH
/{id}/status` que usan categorías y productos: no hay campo de estado que cambiar, hay una fecha que
se adelanta.

---

## 5. Request DTOs

### `CreateDiscountRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `salePrice` | BigDecimal | `@NotNull`, `@PositiveOrZero`, `@Digits(integer = 8, fraction = 2)` |
| `startsAt` | Instant | `@NotNull` |
| `endsAt` | Instant | `@NotNull` |
| `quantityLimit` | Integer | `@Positive`, opcional; ausente significa sin límite |

`originalPrice` no se envía: lo copia el servicio de `products.price` en el momento de crear. Aceptarlo
del cliente permitiría anunciar un "antes" inventado.

Validaciones de negocio, en el servicio:

- `endsAt > startsAt` — también lo garantiza `chk_product_discounts_period`.
- `endsAt > now()` — no tiene sentido crear una promoción ya terminada.
- `salePrice < products.price` — también lo garantiza `chk_product_discounts_price`.
- Sin solape con otra promoción del mismo producto — lo garantiza el `EXCLUDE`.

### `UpdateDiscountRequest`

`endsAt`, `quantityLimit`, `salePrice`, `startsAt`. Qué campos se aceptan depende del estado
(regla 3.3); un campo no editable enviado con un valor distinto al actual se rechaza con 422, no se
ignora en silencio.

---

## 6. Response DTOs

### `DiscountResponse`

`id`, `productId`, `originalPrice`, `salePrice`, `startsAt`, `endsAt`, `quantityLimit`,
`quantitySold`, `state`, `createdAt`, `updatedAt`.

`state` es un valor derivado, no una columna:

| `state` | Condición |
|---|---|
| `SCHEDULED` | `starts_at > now()` |
| `ACTIVE` | Vigente y con unidades disponibles |
| `SOLD_OUT` | Vigente pero `quantity_sold = quantity_limit` |
| `ENDED` | `ends_at <= now()` |

Derivarlo evita una columna de estado que habría que mantener sincronizada con el reloj, y que
quedaría desfasada en cuanto pasara la fecha sin que nadie escribiera nada.

---

## 7. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `CreateDiscountUseCase` | `CreateDiscountService` | Sí |
| `UpdateDiscountUseCase` | `UpdateDiscountService` | Sí |
| `EndDiscountUseCase` | `EndDiscountService` | Sí |
| `DeleteDiscountUseCase` | `DeleteDiscountService` | Sí |
| `GetProductDiscountsUseCase` | `GetProductDiscountsService` | No |

La reserva y la devolución de unidades **no son casos de uso de este módulo**: ocurren dentro de la
transacción del pedido, en `order.md`. Aquí solo se define el contrato que usan
(`DiscountReservationPort`).

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `DiscountReadPort` | `findById`, `findByProduct`, `findActiveForProduct` |
| `DiscountWritePort` | `save`, `delete`, `endNow` |
| `DiscountReservationPort` | `reserve`, `release` |

`DiscountReservationPort` lo consume el módulo `order`, no éste. Es el contrato de los dos `UPDATE`
condicionales de las reglas 3.5 y 3.6, y existe para que el módulo de pedidos no escriba SQL sobre
una tabla que no es suya.

Persistencia ([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): JPA para crear y editar; JDBC
para el precio vigente, el historial con su `state` derivado y los dos `UPDATE` condicionales, que
son SQL con predicado y no encajan en el ciclo de vida de una entidad.

---

## 9. Errores

Enum `DiscountErrorCode` en `domain/exception/discount/`
([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `DISCOUNT_NOT_FOUND` | 404 | No existe |
| `DISCOUNT_OVERLAP` | 409 | Solapa con otra promoción del mismo producto |
| `DISCOUNT_PRICE_NOT_LOWER` | 422 | `salePrice` no es menor que el precio del producto |
| `DISCOUNT_PERIOD_INVALID` | 422 | `endsAt` no es posterior a `startsAt`, o ya pasó |
| `DISCOUNT_LIMIT_BELOW_SOLD` | 422 | El límite nuevo es menor que lo ya vendido |
| `DISCOUNT_NOT_EDITABLE` | 422 | El campo no es editable en el estado actual (regla 3.3) |
| `DISCOUNT_ALREADY_STARTED` | 409 | `DELETE` de una promoción ya empezada |
| `DISCOUNT_EXHAUSTED` | 409 | La reserva no encuentra unidades disponibles |
| `PRODUCT_HAS_ACTIVE_DISCOUNT` | 409 | Cambio de `products.price` con promoción vigente (regla 3.2) |

`ex_product_discounts_no_overlap` nunca llega al cliente con su nombre: se traduce a
`DISCOUNT_OVERLAP`.

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| Dos promociones consecutivas, 12:00 como frontera | Válido: el rango es semiabierto, no solapan |
| Dos promociones que se pisan una hora | 409 `DISCOUNT_OVERLAP` |
| Promoción vigente con `quantity_sold = quantity_limit` | `state = SOLD_OUT`; el producto vuelve a su precio base |
| Cliente que confirma justo cuando se agota | El `UPDATE` condicional afecta cero filas; se le cobra el precio base y se le informa antes de pagar |
| Cancelar un pedido con línea promocional | Devuelve unidades vía `order_items.discount_id` |
| Cancelar un pedido cuya promoción se borró | `discount_id` es `NULL`; no hay nada que devolver, la cancelación no falla |
| Reembolso tras la entrega | No devuelve unidades: la promoción ya se consumió |
| Cambiar el precio del producto con promoción vigente | 409 `PRODUCT_HAS_ACTIVE_DISCOUNT` |
| Bajar `quantity_limit` por debajo de lo vendido | 422 `DISCOUNT_LIMIT_BELOW_SOLD` |
| Editar `salePrice` de una promoción con ventas | 422 `DISCOUNT_NOT_EDITABLE` |
| Adelantar `ends_at` al pasado | 422; para terminarla existe `POST /discounts/{id}/end` |
| Borrar el producto | `ON DELETE CASCADE` se lleva sus promociones — pero solo se puede borrar un producto sin historial ([`product.md`](product.md), regla 3.10) |

---

## 11. Alcance ajeno

- **Reserva y devolución de unidades dentro de la transacción del pedido** — [`order.md`](order.md).
  Aquí se define el puerto, allí quién lo llama y cuándo.
- **Precio vigente en las respuestas del catálogo** — `effectivePrice` y `onSale` en
  [`product.md`](product.md), sección 6.
