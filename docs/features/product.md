# Product

Productos del catálogo: alta, edición, clasificación, imágenes, complementos sugeridos, búsqueda y
filtrado. Los descuentos tienen documento propio ([`product-discounts.md`](product-discounts.md)) y
los movimientos de stock también ([`inventory.md`](inventory.md)); aquí solo se describe cómo el
producto declara si lleva inventario o no.

Sigue el patrón del módulo de referencia
([`category.md`](category.md), [ADR-004](../architecture/ADR/ADR-004-reference-module-category.md)).
Reglas transversales en
[`00-security-validation-integrity.md`](00-security-validation-integrity.md).

---

## 1. Resumen

Un producto es cualquier cosa que la tienda vende: un ramo, una planta, un centro, y también los
complementos que se ofrecen junto a ellos — bombones, un peluche, un jarrón. Los complementos no son
una entidad aparte: son productos normales marcados con `is_extra`, que cuelgan de sus categorías y
se compran igual que el resto.

Dos cosas distinguen a este módulo del resto del catálogo:

- **Atributos flexibles.** `attributes` es JSONB, así que un ramo puede declarar color y ocasión
  mientras una planta declara altura y necesidad de riego, sin columnas fijas por tipo de producto.
- **Inventario opcional.** Un producto puede llevar control de stock o no llevarlo, y eso cambia qué
  comprueba la venta.

---

## 2. Tablas implicadas

`products`, `product_categories`, `product_images`, `product_suggestions` y
`product_attribute_definitions`. Esquema en [`../database/README.md`](../database/README.md).

| Columna de `products` | Restricción relevante |
|---|---|
| `id` | UUID generado por la aplicación |
| `name` | `VARCHAR(200) NOT NULL` |
| `slug` | `VARCHAR(220) NOT NULL`, `uq_products_slug` |
| `description` | `TEXT`, opcional |
| `price` | `NUMERIC(10,2)`, `chk_products_price`: `>= 0` |
| `stock` | `INTEGER`, `chk_products_stock`: `NULL` o `>= 0` |
| `status` | `chk_products_status`: `ACTIVE`, `INACTIVE` o `DISCONTINUED` |
| `is_extra` | `BOOLEAN NOT NULL DEFAULT false` |
| `attributes` | `JSONB NOT NULL DEFAULT '{}'` |
| `search_text` | Lo rellena el adaptador de persistencia, no la base de datos |
| `search_vector` | Columna generada `STORED` a partir de `search_text` |
| `version` | Bloqueo optimista ([ADR-009](../architecture/ADR/ADR-009-optimistic-locking.md)) |

`products` **sí lleva `version`**, a diferencia de `categories`: aquí se editan precios y estados, y
perder un cambio en silencio tiene consecuencia económica. Un conflicto responde 409
`RESOURCE_MODIFIED`.

---

## 3. Reglas de negocio

### 3.1 Slug

Misma regla que en categorías: se genera del nombre, no es editable, y una colisión falla con
`PRODUCT_ALREADY_EXISTS` en vez de añadir sufijo numérico.

**Slugs reservados:** `suggestions`, `all`. Son segmentos literales de rutas bajo `/products/`
(sección 4) y un producto con ese slug quedaría inalcanzable por su URL pública. Se rechazan con
`PRODUCT_SLUG_RESERVED`.

Renombrar regenera el slug y rompe el enlace anterior. El panel usa siempre el UUID.

### 3.2 Estado

| Estado | Significado | Reversible |
|---|---|---|
| `ACTIVE` | A la venta | — |
| `INACTIVE` | Retirado temporalmente: sin flor, fuera de temporada | Sí, vuelve a `ACTIVE` |
| `DISCONTINUED` | Baja definitiva | **No** |

`DISCONTINUED` es terminal por decisión de
[ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md): no existe un
`deleted_at` aparte porque serían dos formas de decir lo mismo y acabarían contradiciéndose. Un
intento de sacar un producto de `DISCONTINUED` responde 409 `PRODUCT_DISCONTINUED`.

Un producto `DISCONTINUED` sigue apareciendo en los pedidos históricos que lo contienen: cada línea
de pedido guarda su propio `product_name` y `product_attributes`, así que el pedido se lee igual
aunque el producto ya no exista en el catálogo.

### 3.3 Visibilidad

**Definición canónica.** La visibilidad se deriva; no hay ninguna columna que la guarde ni proceso
que la sincronice.

| Puede… | Condición |
|---|---|
| Aparecer en listados, en una categoría y en la búsqueda | `status = 'ACTIVE'` **y** al menos una categoría `ACTIVE` |
| Abrirse por su URL directa | `status = 'ACTIVE'` **y** al menos una categoría `ACTIVE` |
| Ofrecerse como complemento sugerido | `status = 'ACTIVE'` **y** al menos una categoría `ACTIVE` |
| Comprarse desde un carrito donde ya estaba | `status = 'ACTIVE'` |

Las categorías deciden si un producto **se encuentra**; `status` decide si **se vende**. La única
excepción es el carrito ya empezado: quien había elegido el producto antes de que su categoría se
desactivara lo compra igual, porque la decisión fue de la tienda, no suya.

Los complementos (`is_extra`) no tienen reglas propias: son productos del catálogo, se ven a través
de su categoría y siguen exactamente esta tabla. `is_extra` solo decide **cuáles pueden ofrecerse
como sugerencia** (regla 3.6), no cómo se ven.

En consulta, la condición es un `EXISTS` sobre `product_categories` y `categories`, apoyado en
`ix_product_categories_category`.

### 3.4 Categorías

Un producto necesita **al menos una categoría al crearse**: sin ella nacería invisible, y crear algo
que nadie puede ver no es un estado útil. Se rechaza con `PRODUCT_WITHOUT_CATEGORY`.

Después sí puede quedarse sin ninguna, si el administrador borra la última categoría a la que
pertenecía. No se impide, porque el borrado de categorías no debe bloquearse por un producto
([`category.md`](category.md), regla 3.3): el producto queda fuera del escaparate y aparece en la
vista de productos sin categoría del panel (`GET /products/all?withoutCategory=true`).

### 3.5 Atributos

`attributes` es JSONB, pero **no es libre**: toda clave debe estar declarada en
`product_attribute_definitions`, y su valor debe respetar el `data_type` declarado (`TEXT`, `NUMBER`
o `BOOLEAN`).

Sin esta validación reaparece justo el problema que la tabla de definiciones venía a evitar: un
administrador escribe `Color`, otro `color` y otro `colour`, y el filtro deja de encontrar la mitad
del catálogo sin que nada falle. La comprobación vive en el servicio, no en una anotación: necesita
consultar otra tabla ([`06-validation-conventions.md`](../architecture/06-validation-conventions.md)).

| Caso | Resultado |
|---|---|
| Clave no declarada | 422 `PRODUCT_ATTRIBUTE_UNDECLARED` |
| Valor de tipo distinto al declarado | 422 `PRODUCT_ATTRIBUTE_TYPE_MISMATCH` |
| `attributes` vacío | Válido: no todo producto tiene atributos |

Borrar una definición de atributo **no** limpia el JSONB de los productos que la usaban. Esas claves
quedan como datos huérfanos: dejan de ser filtrables y dejan de validarse, pero no se pierden. Se
prefiere a un `UPDATE` masivo sobre todo el catálogo, que borraría información sin vuelta atrás.

### 3.6 Complementos sugeridos

`product_suggestions` dice qué ofrecer con qué, y lo fija el administrador producto a producto. No
hay lista global ni recurso automático: la tabla ya modela exactamente esto, con su `position` para
el orden y `chk_product_suggestions_not_self` para impedir que un producto se sugiera a sí mismo.

Solo pueden sugerirse productos con `is_extra = true`. Sugerir un ramo con otro ramo no es lo que la
funcionalidad resuelve; se rechaza con `PRODUCT_NOT_AN_EXTRA`.

Al mostrar las sugerencias se aplica la regla 3.3: un complemento cuya categoría está desactivada no
se ofrece, aunque la fila de `product_suggestions` siga existiendo. La fila no se borra — la
categoría puede reactivarse.

### 3.7 Inventario

`stock = NULL` significa **sin gestión de inventario**, no "stock desconocido": la venta no comprueba
disponibilidad y no genera movimientos. Es el caso del ramo que se monta bajo pedido.

`stock >= 0` significa **inventario gestionado**: toda venta comprueba disponibilidad y todo cambio
genera un `stock_movement`.

El administrador puede cambiar de modo en ambos sentidos:

| Cambio | Qué ocurre |
|---|---|
| No gestionado a gestionado, primera vez | Exige stock inicial; genera el movimiento `INITIAL` |
| Gestionado a no gestionado | `stock` pasa a `NULL`; el historial de movimientos queda intacto |
| No gestionado a gestionado, otra vez | Genera un `ADJUSTMENT`, no un segundo `INITIAL` |

El segundo `INITIAL` lo impide `ux_stock_movements_initial`, un índice único parcial. No es un
obstáculo a rodear: expresa que un producto tiene un único punto de partida de inventario en toda su
historia, y las correcciones posteriores son ajustes.

La escritura de stock no pertenece a este módulo: la hace `RegisterStockMovementService`
([`inventory.md`](inventory.md)), único punto de escritura transaccional. `product.md` solo declara
el modo.

### 3.8 Precio

`price` es el precio base. El precio vigente puede ser menor si hay un descuento activo
([`product-discounts.md`](product-discounts.md)). Las respuestas devuelven ambos, para que la web
pueda tachar el anterior.

**El precio base no se cambia mientras haya un descuento vigente**: se rechaza con 409
`PRODUCT_HAS_ACTIVE_DISCOUNT`. La promoción congela el precio anterior en `original_price` para
mostrar el tachado, así que cambiar el base dejaría la web anunciando un "antes" que ya no existe.
El administrador cierra la promoción y luego cambia el precio
([`product-discounts.md`](product-discounts.md), regla 3.2).

El precio **nunca se toma del cliente**. El carrito y el pedido lo recalculan desde el catálogo; lo
que envíe el cliente solo sirve para detectar una discrepancia y rechazar la petición
([`00-security-validation-integrity.md`](00-security-validation-integrity.md), sección 4).

### 3.9 Imágenes

`product_images` referencia una fila de `images` (`V13`), nunca guarda binarios ni claves de S3. La
subida es del módulo [`image.md`](image.md), que devuelve el `id` de la imagen; el producto lo recibe
como un campo más. El cliente nunca envía una clave de S3: la clave ajena hace que solo pueda
referenciar algo que la subida creó.

`position` ordena la galería. La imagen de posición más baja es la principal, la que se ve en el
listado. No hay columna `is_main`: sería una segunda forma de decir lo mismo que `position = 0`, y
las dos acabarían discrepando.

### 3.10 Borrado

`DELETE` solo funciona si el producto **nunca se vendió, nunca tuvo movimiento de stock y nunca
apareció en una compra a proveedor**. Lo impone la base de datos con `RESTRICT` en
`order_items.product_id`, `stock_movements.product_id` y `purchase_items.product_id` (ADR-007).

Un intento sobre un producto con historial responde 409 `PRODUCT_HAS_HISTORY`, con el motivo
concreto. La alternativa para retirar ese producto es `DISCONTINUED`, que es exactamente para lo que
existe.

En la práctica el borrado sirve para deshacer un alta reciente equivocada; cualquier producto con
vida comercial se retira, no se borra.

---

## 4. Endpoints

Prefijo `/api/v1`.

### Público

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/products` | 200 — paginado, solo visibles (regla 3.3) |
| `GET` | `/products/{idOrSlug}` | 200 — acepta UUID o slug |
| `GET` | `/products/suggestions` | 200 — autocompletado por trigrama |
| `GET` | `/products/{id}/extras` | 200 — complementos sugeridos, ya filtrados por visibilidad |

`GET /products` acepta, todos combinables:

| Parámetro | Efecto |
|---|---|
| `q` | Búsqueda full-text sobre `search_vector` |
| `category` | UUID o slug de categoría |
| `minPrice`, `maxPrice` | Sobre el precio vigente, con descuento aplicado |
| `onSale` | Solo productos con descuento activo ahora mismo |
| `attr.{clave}` | Atributo declarado con `filterable = true`; por ejemplo `attr.color=rojo` |
| `page`, `size` | Paginación; `size` con máximo |

`attr.{clave}` solo admite claves declaradas y marcadas filtrables. Una clave desconocida se rechaza
con 422 en vez de ignorarse en silencio: ignorarla devolvería el catálogo entero y el usuario creería
que su filtro se aplicó.

### Búsqueda y autocompletado

Dos mecanismos distintos ([ADR-006](../architecture/ADR/ADR-006-postgres-search-instead-of-elasticsearch.md)),
dos endpoints:

```
GET /products?q=rosas rojas
    Full-text sobre search_vector, plainto_tsquery('spanish', ...).
    No hace prefijos: "ros" NO encuentra "rosas".
    Devuelve productos paginados.

GET /products/suggestions?q=ros
    Trigrama sobre search_text (pg_trgm). Prefijos y erratas.
    Devuelve nombres para el desplegable, no productos completos.
```

Mezclarlos en un endpoint que cae de uno a otro daría al usuario coincidencias que no entiende: busca
"rosas" y recibe "rosal" porque el full-text no encontró nada.

### Administración

| Método | Ruta | Rol | Devuelve |
|---|---|---|---|
| `GET` | `/products/all` | `ADMIN` | 200 — incluye `INACTIVE` y `DISCONTINUED` |
| `GET` | `/products/{id}/deletion-impact` | `ADMIN` | 200 — si se puede borrar y por qué no |
| `POST` | `/products` | `ADMIN` | 201 |
| `PUT` | `/products/{id}` | `ADMIN` | 200 |
| `PATCH` | `/products/{id}/status` | `ADMIN` | 200 |
| `PUT` | `/products/{id}/categories` | `ADMIN` | 200 |
| `PUT` | `/products/{id}/images` | `ADMIN` | 200 — lista completa, con su orden |
| `PUT` | `/products/{id}/extras` | `ADMIN` | 200 — lista completa, con su orden |
| `PATCH` | `/products/{id}/inventory` | `ADMIN` | 200 — cambia el modo (regla 3.7) |
| `DELETE` | `/products/{id}` | `ADMIN` | 204 |

`GET /products/all` acepta además `status`, `withoutCategory=true` y `isExtra`.

Las tres colecciones (`categories`, `images`, `extras`) se envían completas, no elemento a elemento,
por el mismo motivo que el reordenado de categorías: un envío parcial deja posiciones a medias.

### Definiciones de atributo

| Método | Ruta | Rol |
|---|---|---|
| `GET` | `/product-attributes` | Público — el front necesita saber qué filtros ofrecer |
| `POST` | `/product-attributes` | `ADMIN` |
| `PUT` | `/product-attributes/{id}` | `ADMIN` |
| `DELETE` | `/product-attributes/{id}` | `ADMIN` |

`attribute_key` es inmutable una vez creada: cambiarla dejaría huérfanas todas las claves ya escritas
en el JSONB de los productos. Para renombrar la etiqueta visible está `label`, que sí es editable.

Un `GET` público de un producto no visible responde **404**, no 403.

---

## 5. Request DTOs

### `CreateProductRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `name` | String | `@NotBlank`, `@Size(max = 200)` |
| `description` | String | `@Size(max = 5000)`, opcional |
| `price` | BigDecimal | `@NotNull`, `@PositiveOrZero`, `@Digits(integer = 8, fraction = 2)` |
| `categoryIds` | List\<UUID\> | `@NotEmpty`, `@Size(max = 20)`, sin repetidos |
| `isExtra` | Boolean | Por defecto `false` |
| `attributes` | Map\<String, Object\> | Validado contra las definiciones (regla 3.5) |
| `imageIds` | List\<UUID\> | `@Size(max = 10)`, opcional, sin repetidos; el orden es la galería |
| `initialStock` | Integer | `@PositiveOrZero`, opcional; ausente significa sin gestión de inventario |

Sin `slug` — se genera. Sin `status` — nace `ACTIVE`.

### `UpdateProductRequest`

`name`, `description`, `price`, `isExtra`, `attributes`. **No** lleva stock, categorías, imágenes ni
complementos: cada uno tiene su endpoint, porque son operaciones con reglas propias y mezclarlas en
un `PUT` general obligaría a distinguir "no enviado" de "vaciar".

### `ChangeProductStatusRequest`

`status`: `ACTIVE`, `INACTIVE` o `DISCONTINUED`.

### `UpdateProductCategoriesRequest`

`categoryIds`: `@NotEmpty`, `@Size(max = 20)`. No se admite vaciar la lista por esta vía — un producto
solo se queda sin categorías si se borra la última categoría desde su propio módulo.

### `UpdateProductImagesRequest`

Lista de `{ imageId, altText }`. El orden de la lista es `position`. Cada `imageId` debe existir en
`images` ([`image.md`](image.md)); la clave ajena de `V13` lo garantiza además en la base de datos.

### `UpdateProductExtrasRequest`

`extraProductIds`: `@Size(max = 20)`. Puede ir vacía. Todos deben tener `is_extra = true`.

### `ChangeInventoryModeRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `managed` | Boolean | `@NotNull` |
| `stock` | Integer | Obligatorio si `managed = true`; `@PositiveOrZero` |
| `lowStockThreshold` | Integer | `@PositiveOrZero`, opcional; `NULL` desactiva la alerta de stock bajo |
| `note` | String | `@Size(max = 500)`, opcional; alimenta el movimiento |

`lowStockThreshold` solo tiene efecto si `managed = true` — sin gestión de inventario no hay `stock`
que comparar contra un umbral. Alertas de stock bajo y de integridad: detalle completo en
[`inventory.md`](inventory.md), sección 3.8.

---

## 6. Response DTOs

### `ProductResponse`

`id`, `name`, `slug`, `description`, `price`, `effectivePrice`, `onSale`, `status`, `isExtra`,
`attributes`, `categories`, `images`, `stock`, `inventoryManaged`, `createdAt`, `updatedAt`.

`effectivePrice` es el precio con descuento aplicado si lo hay; si no, coincide con `price`. `onSale`
evita que el front tenga que compararlos.

`stock` solo aparece para administradores. El público no necesita saber cuántas unidades quedan, y
publicarlo revela volumen de negocio.

### `ProductSummaryResponse`

`id`, `name`, `slug`, `price`, `effectivePrice`, `onSale`, `mainImageUrl`. Para listados y búsqueda.

### `ProductSuggestionResponse`

`name` y `slug`. Solo lo que necesita un desplegable de autocompletado.

### `ProductDeletionImpactResponse`

| Campo | Descripción |
|---|---|
| `deletable` | Si el borrado físico es posible |
| `blockedBy` | Motivos: `ORDERS`, `STOCK_MOVEMENTS`, `PURCHASES` |
| `orderCount`, `stockMovementCount`, `purchaseCount` | Para que el aviso sea concreto |

---

## 7. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `CreateProductUseCase` | `CreateProductService` | Sí |
| `UpdateProductUseCase` | `UpdateProductService` | Sí |
| `ChangeProductStatusUseCase` | `ChangeProductStatusService` | Sí |
| `UpdateProductCategoriesUseCase` | `UpdateProductCategoriesService` | Sí |
| `UpdateProductImagesUseCase` | `UpdateProductImagesService` | Sí |
| `UpdateProductExtrasUseCase` | `UpdateProductExtrasService` | Sí |
| `ChangeInventoryModeUseCase` | `ChangeInventoryModeService` | Sí |
| `DeleteProductUseCase` | `DeleteProductService` | Sí |
| `GetProductUseCase` | `GetProductService` | No |
| `SearchProductsUseCase` | `SearchProductsService` | No |
| `AutocompleteProductsUseCase` | `AutocompleteProductsService` | No |
| `GetProductExtrasUseCase` | `GetProductExtrasService` | No |
| `GetProductDeletionImpactUseCase` | `GetProductDeletionImpactService` | No |
| `CreateAttributeDefinitionUseCase` | `CreateAttributeDefinitionService` | Sí |
| `UpdateAttributeDefinitionUseCase` | `UpdateAttributeDefinitionService` | Sí |
| `DeleteAttributeDefinitionUseCase` | `DeleteAttributeDefinitionService` | Sí |
| `GetAttributeDefinitionsUseCase` | `GetAttributeDefinitionsService` | No |

`CreateProductService` es transaccional: el producto, sus categorías, sus imágenes y el movimiento
`INITIAL` si nace con inventario van juntos o no van.

`ChangeInventoryModeService` también: el cambio de `stock` y su movimiento son inseparables.

### Actualización de `search_text`

`search_text` la rellena el adaptador de persistencia en Java, normalizando nombre, descripción y los
valores de texto de `attributes`
([ADR-006](../architecture/ADR/ADR-006-postgres-search-instead-of-elasticsearch.md)). Se recalcula en
cada escritura que toque esos tres campos. `search_vector` lo deriva PostgreSQL sola, por ser columna
generada.

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `ProductReadPort` | `findById`, `findBySlug`, `findAllVisible`, `findAllForAdmin` |
| `ProductWritePort` | `save`, `delete`, `updateStatus` |
| `ProductExistencePort` | `existsBySlug`, `existsById`, `hasCommercialHistory` |
| `ProductSearchPort` | `search`, `autocomplete` |
| `ProductCategoryPort` | `replaceCategories`, `findCategories` |
| `ProductImagePort` | `replaceImages`, `findImages` — asocia filas de `images`, no sube nada |
| `ProductSuggestionPort` | `replaceSuggestions`, `findVisibleSuggestions` |
| `AttributeDefinitionPort` | `findAll`, `findByKey`, `save`, `delete` |
| `ProductInventoryPort` | `initializeStock`, `adjustStock`, `disableStockManagement` |

`ProductSearchPort` esconde los dos mecanismos de ADR-006 detrás de una interfaz: la capa de
aplicación no sabe que uno es `tsvector` y el otro `pg_trgm`.

`ProductInventoryPort` es la frontera con el módulo de inventario. `product` no escribe en
`stock_movements` por su cuenta: delega en el único punto de escritura transaccional
([`inventory.md`](inventory.md)).

Persistencia ([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): JPA para guardar y buscar por
identificador o slug; JDBC para el listado filtrado, la búsqueda, el autocompletado, la comprobación
de visibilidad y el impacto de borrado, que son joins con proyección y agregados.

---

## 9. Errores

Enum `ProductErrorCode` en `domain/exception/product/`
([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `PRODUCT_NOT_FOUND` | 404 | No existe, o no es visible en acceso público |
| `PRODUCT_ALREADY_EXISTS` | 409 | El slug generado ya está en uso |
| `PRODUCT_SLUG_RESERVED` | 422 | El nombre genera `suggestions` o `all` |
| `PRODUCT_WITHOUT_CATEGORY` | 422 | Alta sin ninguna categoría |
| `PRODUCT_ATTRIBUTE_UNDECLARED` | 422 | Clave de `attributes` no declarada |
| `PRODUCT_ATTRIBUTE_TYPE_MISMATCH` | 422 | El valor no respeta el `data_type` declarado |
| `PRODUCT_NOT_AN_EXTRA` | 422 | Se intenta sugerir un producto con `is_extra = false` |
| `PRODUCT_DISCONTINUED` | 409 | Se intenta sacar de `DISCONTINUED`, o editar uno dado de baja |
| `PRODUCT_HAS_HISTORY` | 409 | `DELETE` de un producto con ventas, stock o compras |
| `PRODUCT_STOCK_REQUIRED` | 422 | Se activa el inventario sin indicar stock inicial |
| `PRODUCT_HAS_ACTIVE_DISCOUNT` | 409 | Cambio de precio con promoción vigente (regla 3.8) |
| `RESOURCE_MODIFIED` | 409 | Conflicto de bloqueo optimista (ADR-009) |
| `PRODUCT_VALIDATION_FAILED` | 422 | Bean Validation; con `errors[]` |

Ninguna violación de constraint llega al cliente con su nombre: `uq_products_slug` se traduce a
`PRODUCT_ALREADY_EXISTS`.

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| Producto `ACTIVE` cuya única categoría se desactiva | Desaparece del escaparate; solo se compra desde un carrito donde ya estuviera |
| Producto `INACTIVE` en una categoría `ACTIVE` | Sigue oculto: la regla 3.3 exige las dos condiciones |
| `GET` público de un producto no visible | 404 |
| Editar un producto `DISCONTINUED` | 409 `PRODUCT_DISCONTINUED` |
| `DELETE` de un producto recién creado, sin ventas | 204 |
| `DELETE` de un producto vendido alguna vez | 409 `PRODUCT_HAS_HISTORY`, con el motivo concreto |
| Borrar una definición de atributo en uso | Se borra; las claves quedan huérfanas en el JSONB, sin filtrar ni validar (regla 3.5) |
| Filtrar por un atributo no declarado o no filtrable | 422, nunca se ignora en silencio |
| `q` con una errata en `GET /products` | Puede no devolver nada: el full-text no corrige erratas, eso es del autocompletado |
| Sugerir un producto como complemento de sí mismo | Lo rechaza `chk_product_suggestions_not_self`; se traduce a 422 |
| Complemento sugerido cuya categoría se desactiva | Deja de ofrecerse; la fila de `product_suggestions` no se borra |
| Reactivar el inventario de un producto que ya lo tuvo | `ADJUSTMENT`, no un segundo `INITIAL` |
| Dos administradores editando el mismo producto | 409 `RESOURCE_MODIFIED`; `products` sí lleva `version` |
| Cambiar el precio con promoción vigente | 409 `PRODUCT_HAS_ACTIVE_DISCOUNT`; cerrar la promoción primero |

---

## 11. Alcance ajeno

Definido en otro documento:

- **Descuentos** — vigencia, solape prohibido, reserva y devolución de unidades:
  [`product-discounts.md`](product-discounts.md).
- **Movimientos de stock** — `INITIAL`, `PURCHASE`, `SALE`, `WASTE`, `ADJUSTMENT` y la reconciliación:
  [`inventory.md`](inventory.md).
- **Subida de imágenes, variantes y borrado del fichero** — [`image.md`](image.md). Borrar un
  producto deja sus imágenes en la bandeja de no asociadas, nunca las borra de S3.
- **Qué hace el checkout con un producto no visible** — [`order.md`](order.md), aplicando la
  excepción del carrito de la regla 3.3.
