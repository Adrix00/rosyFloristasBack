# Cart

Carrito de compra: invitado y cliente registrado comparten el mismo mecanismo, distinguido solo por
si el carrito tiene `customer_id` o no.

Sigue el patrón del módulo de referencia ([`category.md`](category.md)). Reglas transversales en
[`00-security-validation-integrity.md`](00-security-validation-integrity.md).

---

## 1. Resumen

Un carrito no es un documento comercial: no lleva snapshot de precios ni de nombres de producto, a
diferencia de un pedido ([`order.md`](order.md)). El precio se calcula **en vivo** cada vez que se
consulta, nunca se guarda — el carrito no es más que una lista de `(producto, cantidad)`.

"Invitado" aquí significa **sin sesión iniciada** (`carts.customer_id IS NULL`), un concepto distinto
del `type = 'GUEST'` de `customers`, que es sobre cómo se hizo el pedido, no sobre cómo se navegó.
Un invitado que nunca se registra puede completar un pedido igualmente
([`order.md`](order.md)); el carrito no exige cuenta.

---

## 2. Tablas implicadas

`carts`, `cart_items`. Esquema en [`../database/README.md`](../database/README.md).

| Columna de `carts` | Restricción |
|---|---|
| `customer_id` | `REFERENCES customers ON DELETE CASCADE`, **nullable** — `NULL` es un carrito de invitado |
| `session_token` | `NOT NULL`, `UNIQUE` — identifica el carrito en ambos casos, invitado o cliente |
| `expires_at` | `NOT NULL` |

| Columna de `cart_items` | Restricción |
|---|---|
| `cart_id` | `REFERENCES carts ON DELETE CASCADE` |
| `product_id` | `REFERENCES products ON DELETE CASCADE` — el carrito no es histórico ([`../database/README.md`](../database/README.md)) |
| `quantity` | `> 0` |
| — | `UNIQUE (cart_id, product_id)`: una fila por producto, la cantidad se acumula ahí |

`cart_items.product_id` es `CASCADE`, no `RESTRICT`: borrar un producto se lleva su presencia en
cualquier carrito, porque el carrito no es un registro que deba sobrevivir al producto.

---

## 3. Reglas de negocio

### 3.1 Identificación y creación perezosa

`session_token` viaja en una cookie (`HttpOnly`, `Secure`, `SameSite=Lax`), generada por el backend
la primera vez que hace falta — no antes. **El carrito no se crea al visitar la web, se crea al
añadir el primer producto.** Crear una fila en cada visita llenaría `carts` de filas vacías que nadie
va a completar nunca.

Un cliente autenticado localiza su carrito por `customer_id`, no por la cookie: así lo recupera igual
desde cualquier dispositivo. La cookie solo importa mientras no hay sesión.

### 3.2 Fusión al iniciar sesión

Si un carrito de invitado (identificado por la cookie) existe en el momento del login, y el cliente
ya tenía su propio carrito de una sesión anterior, se **fusionan**: por cada producto presente en
ambos, las cantidades se suman, con el máximo por línea (regla 3.3) como tope. Los productos que solo
estaban en uno de los dos se copian tal cual. El carrito de invitado se borra al terminar — su
`CASCADE` se lleva sus líneas.

Si el cliente no tenía carrito propio todavía, el de invitado simplemente pasa a ser suyo
(`UPDATE carts SET customer_id = :customerId`), sin fusión que hacer.

Esta regla la ejecuta [`auth.md`](auth.md) como parte del login, invocando
`MergeCartUseCase` de este módulo — no es un endpoint propio de `cart`, es una capacidad que otro
módulo consume, mismo patrón que [`inventory.md`](inventory.md) con `RegisterStockMovementUseCase`.

### 3.3 Añadir y actualizar cantidad

Añadir un producto ya presente **suma** a la cantidad existente, no la reemplaza — es lo que expresa
el `UNIQUE (cart_id, product_id)`: una fila por producto.

Límites de carga útil, defensivos y sin significado de negocio (
[`00-security-validation-integrity.md`](00-security-validation-integrity.md), sección 4): máximo 99
unidades por línea, máximo 50 líneas distintas por carrito.

**Comprobación de stock al añadir o actualizar cantidad.** Es la primera de tres comprobaciones — la
selección de cantidad en el frontend, que limita al stock visible, es la primera y no es
responsabilidad de este backend. Si aun así llega una cantidad mayor que el stock gestionado
disponible, se rechaza con el número real disponible en el mensaje: pedir 5 con solo 3 en stock
responde 422 con `availableQuantity: 3`, no un simple "sin stock".

Esta comprobación es **blanda**: no reserva nada. El único bloqueo real de concurrencia es el
`UPDATE` condicional del checkout ([`inventory.md`](inventory.md), regla 3.1). Entre que se añade al
carrito y se paga, otra persona puede agotar el producto — por eso existe la tercera comprobación
(regla 3.4).

Un producto sin gestión de inventario (`stock IS NULL`) no tiene límite que comprobar aquí.

### 3.4 Validación antes de pagar

Justo antes de iniciar el pago, en el arranque del checkout ([`order.md`](order.md)), se revalida el
carrito entero. Es la tercera comprobación, red de seguridad por si las dos anteriores (frontend y
regla 3.3) se saltaron. Dos motivos distintos, dos tratamientos distintos:

| Motivo | Tratamiento |
|---|---|
| Producto ya no `ACTIVE` (`INACTIVE` o `DISCONTINUED`) | Se **elimina** la línea del carrito automáticamente y se informa |
| Stock insuficiente para la cantidad pedida | Se **bloquea** el paso a pago; no se ajusta la cantidad sola |

Un producto retirado de la venta no tiene nada que negociar: se quita, se pide disculpas, y el
checkout **continúa** con el resto de líneas si queda alguna — sin reiniciar lo que el cliente ya
hubiera rellenado en el propio checkout (dirección, franja de entrega). El estado del checkout no
vive en `cart` ni en este documento; lo sostiene [`order.md`](order.md), y esta regla solo dice qué le
pasa al carrito antes de que el pedido se cree.

Stock insuficiente sí bloquea: cambiar la cantidad en nombre del cliente decidiría por él cuánto
está dispuesto a pagar. Se le devuelve la cantidad real disponible para que decida.

Esta revalidación no tiene por qué esperar al primer paso del checkout: el frontend puede llamarla
antes, al pasar de "ver el carrito" a "ir a pagar", para no hacer esperar al cliente hasta haber
rellenado ya la dirección. El mecanismo es el mismo — `ValidateCartUseCase` — se invoque desde
`GET /cart/validation` o desde dentro de `order.md`.

### 3.5 Precio: siempre en vivo

`cart_items` no guarda precio. Cada `GET /cart` calcula el precio vigente del momento —
`effectivePrice` de [`product.md`](product.md), con descuento si lo hay — uniendo con `products` y
`product_discounts`. Si el precio cambia entre que se añadió y que se consulta, el carrito lo refleja
sin que nadie lo haya tocado. El precio que de verdad se cobra es el que el pedido recalcula al
crearse, no el que el carrito mostró la última vez.

### 3.6 Visibilidad al añadir

Añadir un producto exige que sea comprable en el sentido amplio de
[`product.md`](product.md), regla 3.3: `ACTIVE` y con al menos una categoría `ACTIVE`. Llegar a poder
añadirlo ya implica haberlo visto por listado, búsqueda o URL directa, que exigen exactamente eso.

Una vez en el carrito, la excepción de esa misma regla se activa: el producto sigue siendo comprable
aunque su categoría se desactive después, mientras siga `ACTIVE` (regla 3.4 de este documento cubre
el caso en que deja de estarlo).

### 3.7 Caducidad

`expires_at`: **30 días** para un carrito de invitado. Un cliente registrado no tiene caducidad
efectiva — su carrito es parte de su cuenta, se actualiza `expires_at` a un valor lejano en cada
escritura y una tarea de limpieza solo actúa sobre invitados.

Un carrito caducado no se borra al momento: lo hace una tarea periódica
(`infrastructure/scheduler`), igual que la limpieza de `idempotency_keys`
([`00-security-validation-integrity.md`](00-security-validation-integrity.md)). Acceder a un carrito
ya caducado antes de que la tarea pase equivale a no tener carrito: se trata como vacío y, al añadir
algo, se crea uno nuevo.

---

## 4. Endpoints

Prefijo `/api/v1`. Público — funciona sin autenticación; con JWT válido, identifica por `customer_id`
en vez de por cookie.

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/cart` | 200 — vacío si no existe carrito todavía |
| `POST` | `/cart/items` | 200 — añade o suma cantidad |
| `PATCH` | `/cart/items/{productId}` | 200 — fija una cantidad exacta |
| `DELETE` | `/cart/items/{productId}` | 200 — devuelve el carrito resultante, no 204 |
| `DELETE` | `/cart` | 200 — vacía el carrito, no lo borra |
| `GET` | `/cart/validation` | 200 — ejecuta la regla 3.4 bajo demanda |

`DELETE /cart/items/{productId}` y `DELETE /cart` devuelven el carrito actualizado, no `204`: el
frontend necesita el total recalculado sin una segunda llamada.

`DELETE /cart` no borra la fila `carts`: la vacía. Un cliente registrado sin productos en el carrito
sigue teniendo un carrito, solo que vacío — no hay motivo para forzarlo a que uno nuevo se cree en su
próxima visita.

---

## 5. Request DTOs

### `AddCartItemRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `productId` | UUID | `@NotNull` |
| `quantity` | Integer | `@NotNull`, `@Positive`, máximo 99 |

### `UpdateCartItemRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `quantity` | Integer | `@NotNull`, `@Positive`, máximo 99 |

`quantity` fija el valor, no lo suma — a diferencia de `POST /cart/items` (regla 3.3). `PATCH` con
`quantity` igual a la actual no es un error, simplemente no cambia nada.

---

## 6. Response DTOs

### `CartResponse`

`id`, `items`, `subtotal`, `itemCount`.

`subtotal` es la suma de `effectivePrice * quantity` de cada línea, calculada en la respuesta — no
existe columna que la guarde (regla 3.5).

### `CartItemResponse`

`productId`, `productName`, `productSlug`, `mainImageUrl`, `unitPrice`, `effectivePrice`, `onSale`,
`quantity`, `lineTotal`, `availableQuantity`.

`availableQuantity` es el stock actual del producto, o `null` si no tiene gestión de inventario. El
frontend lo usa para capar el selector de cantidad (primera comprobación de la regla 3.3) sin tener
que adivinarlo.

### `CartValidationResponse`

| Campo | Descripción |
|---|---|
| `valid` | `true` si no hubo que tocar nada |
| `removedItems` | Productos quitados por no estar `ACTIVE`: `productId`, `productName` |
| `insufficientStockItems` | Productos con menos stock del pedido: `productId`, `productName`, `requestedQuantity`, `availableQuantity` |

`valid = false` con `removedItems` no vacío y `insufficientStockItems` vacío significa que el
checkout puede continuar igualmente — el carrito ya se corrigió solo. `valid = false` con
`insufficientStockItems` no vacío significa que el cliente tiene que decidir algo antes de seguir.

---

## 7. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `AddCartItemUseCase` | `AddCartItemService` | Sí — crea el carrito si no existe (regla 3.1) |
| `UpdateCartItemUseCase` | `UpdateCartItemService` | Sí |
| `RemoveCartItemUseCase` | `RemoveCartItemService` | Sí |
| `ClearCartUseCase` | `ClearCartService` | Sí |
| `MergeCartUseCase` | `MergeCartService` | Sí — lo invoca `auth.md` en el login |
| `ValidateCartUseCase` | `ValidateCartService` | Sí — puede escribir (elimina líneas no `ACTIVE`) aunque se exponga también como lectura |
| `GetCartUseCase` | `GetCartService` | No |

`ValidateCartService` escribe condicionalmente: elimina líneas que ya no son `ACTIVE`, pero nunca
toca la cantidad de una línea con stock insuficiente — eso lo decide el cliente, no el servicio
(regla 3.4).

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `CartReadPort` | `findByCustomer`, `findBySessionToken` |
| `CartWritePort` | `save`, `touch` (renueva `expires_at`) |
| `CartItemWritePort` | `save`, `delete`, `deleteAll` |
| `CartPricingPort` | `priceFor` — precio vigente de un producto, delegado a [`product.md`](product.md) |

`CartPricingPort` no reimplementa el cálculo de `effectivePrice`: lo pide a `product`. Este módulo no
sabe cómo se calcula un descuento, solo que existe un precio vigente que preguntar.

Persistencia ([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): JPA para añadir, actualizar y
eliminar líneas; JDBC para `GET /cart`, que es un join con `products` y `product_discounts` para
resolver el precio vigente de cada línea en una sola consulta.

---

## 9. Errores

Enum `CartErrorCode` en `domain/exception/cart/`
([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `CART_PRODUCT_NOT_FOUND` | 404 | El producto no existe, o no es visible (regla 3.6) |
| `CART_ITEM_NOT_FOUND` | 404 | `PATCH`/`DELETE` sobre un producto que no está en el carrito |
| `CART_INSUFFICIENT_STOCK` | 422 | Cantidad pedida mayor que el stock disponible; con `availableQuantity` |
| `CART_ITEM_LIMIT_EXCEEDED` | 422 | Más de 99 unidades en una línea |
| `CART_LINE_LIMIT_EXCEEDED` | 422 | Más de 50 líneas distintas |
| `CART_VALIDATION_FAILED` | 422 | Bean Validation; con `errors[]` |

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| `GET /cart` sin cookie ni sesión, primera visita | 200, carrito vacío; no se crea fila |
| Añadir un producto ya en el carrito | Suma a la cantidad existente, respetando el máximo por línea |
| Añadir con cantidad mayor que el stock disponible | 422 `CART_INSUFFICIENT_STOCK`, con `availableQuantity` |
| Añadir un producto sin categoría activa | 404 `CART_PRODUCT_NOT_FOUND`: no es visible (regla 3.6) |
| Producto ya en el carrito cuya categoría se desactiva después | Sigue comprable: la excepción de [`product.md`](product.md) regla 3.3 aplica |
| Ese mismo producto pasa a `DISCONTINUED` | Se elimina en la validación previa al pago (regla 3.4), no antes |
| Login con carrito de invitado y carrito propio previos | Se fusionan, cantidades sumadas (regla 3.2) |
| Login sin carrito propio previo | El carrito de invitado pasa a ser del cliente, sin fusión |
| Login sin carrito de invitado | Nada que fusionar; se usa el carrito del cliente tal cual |
| Carrito de invitado caducado, se añade algo | Se crea uno nuevo; el caducado lo limpia la tarea periódica |
| `DELETE /cart/items/{productId}` de un producto que no está | 404 `CART_ITEM_NOT_FOUND` |
| Vaciar un carrito ya vacío | 200, sin efecto; no es un error |

---

## 11. Alcance ajeno

- **Ejecución del login que dispara la fusión** — [`auth.md`](auth.md); este documento solo define
  `MergeCartUseCase` como la capacidad que invoca.
- **Creación del pedido a partir del carrito, y el resto del flujo de checkout** —
  [`order.md`](order.md), que consume `ValidateCartUseCase` como primer paso.
- **Precio vigente y stock disponible de cada producto** — [`product.md`](product.md) e
  [`inventory.md`](inventory.md); este módulo los consulta, no los calcula.
