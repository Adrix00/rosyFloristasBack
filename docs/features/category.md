# Category

Módulo de referencia del proyecto ([ADR-004](../architecture/ADR/ADR-004-reference-module-category.md)).
Las convenciones que fija aquí las replican los diez módulos restantes.

Reglas transversales (autenticación, autorización, validación, errores, auditoría) en
[`00-security-validation-integrity.md`](00-security-validation-integrity.md). Este documento no las
repite.

---

## 1. Resumen

Las categorías organizan el catálogo. El administrador las crea libremente — "Ramos", "Plantas",
"San Valentín", "Peluches" — y las ordena para decidir cómo se presentan en la web.

La relación con productos es **N:M**: un mismo ramo puede estar en "Ramos" y en "San Valentín" a la
vez. Esto condiciona todo el comportamiento de desactivación y borrado descrito más abajo.

---

## 2. Tablas implicadas

`categories` y `product_categories`. Esquema en
[`../database/README.md`](../database/README.md).

| Columna | Restricción relevante |
|---|---|
| `id` | UUID generado por la aplicación, no por la base de datos |
| `name` | `VARCHAR(150) NOT NULL` |
| `slug` | `VARCHAR(170) NOT NULL`, `uq_categories_slug` |
| `description` | `TEXT`, opcional |
| `status` | `chk_categories_status`: `ACTIVE` o `INACTIVE` |
| `image_s3_key` | `VARCHAR(500)`, opcional |
| `position` | `INTEGER NOT NULL DEFAULT 0`, sin unicidad |

`product_categories` tiene clave primaria `(product_id, category_id)` y `ON DELETE CASCADE` hacia
ambos lados: borrar una categoría elimina la **asociación**, nunca la fila del producto.

**`categories` no lleva `version`, y es deliberado.**
[ADR-009](../architecture/ADR/ADR-009-optimistic-locking.md) añadió bloqueo optimista a cinco raíces
de agregado; ésta no está entre ellas. Dos administradores editando la misma categoría a la vez: gana
el último y el otro cambio se pierde sin aviso. Aceptado — la categoría tiene pocos campos, ninguno
con consecuencia económica, y la tienda opera con uno o dos administradores. Si alguna vez deja de
ser cierto, se añade la columna y se amplía ADR-009.

---

## 3. Reglas de negocio

### 3.1 Slug

Se genera a partir del nombre: minúsculas, sin tildes, espacios a guiones. "Ramos de novia" produce
`ramos-de-novia`.

No lo escribe el administrador y no es editable. Si el slug generado ya existe, la operación falla
con `CATEGORY_ALREADY_EXISTS` y el administrador cambia el nombre. No se añade sufijo numérico: un
`ramos-2` genera una URL que nadie eligió y deja dos categorías casi idénticas conviviendo sin aviso.

**Slugs reservados.** `all` y `positions` no pueden generarse: son segmentos literales de rutas
existentes (`GET /categories/all`, `PUT /categories/positions`) y una categoría con ese slug quedaría
inalcanzable por su URL pública. Un nombre que produzca uno de ellos se rechaza con
`CATEGORY_SLUG_RESERVED`. La lista vive junto al generador de slugs y crece si aparece una ruta
literal nueva bajo `/categories/`.

Consecuencia aceptada: renombrar una categoría cambia su slug, y con él su URL pública. Un enlace
antiguo deja de funcionar y responde 404. Es una operación poco frecuente y el administrador debe
saberlo. El UUID, en cambio, no cambia nunca: por eso el panel trabaja siempre con él.

### 3.2 Estado y visibilidad

`ACTIVE` e `INACTIVE`, reversible en ambos sentidos. Desactivar una categoría **no escribe nada en
los productos**.

**Regla de visibilidad del catálogo.** Una sola condición para todas las formas de llegar a un
producto, y una única excepción:

| Puede… | Condición |
|---|---|
| Aparecer en listados, en una categoría y en la búsqueda | `products.status = 'ACTIVE'` **y** al menos una categoría `ACTIVE` |
| Abrirse por su URL directa | `products.status = 'ACTIVE'` **y** al menos una categoría `ACTIVE` |
| Ofrecerse como complemento sugerido | `products.status = 'ACTIVE'` **y** al menos una categoría `ACTIVE` |
| Comprarse desde un carrito donde ya estaba | `products.status = 'ACTIVE'` |

Desactivar una categoría retira sus productos del escaparate por completo: dejan de listarse, de
buscarse, de abrirse por enlace y de sugerirse. Lo único que sobrevive es el carrito ya empezado —
quien ya había elegido el producto lo compra, porque castigarle por una decisión de la tienda no
tendría sentido.

`products.status` sigue siendo el único eje que decide si un producto **se vende**. Las categorías
deciden si **se encuentra**.

La visibilidad se **deriva**, no se persiste. No hay ninguna columna que la guarde y ningún proceso
que la sincronice.

Consecuencias, todas buscadas:

- Desactivar "San Valentín" oculta los productos que solo estaban ahí. Un ramo que también está en
  "Ramos" sigue visible en "Ramos", porque sigue siendo un producto válido de esa categoría.
- Desactivar y reactivar una categoría es **simétrico**: restaura exactamente el estado anterior, sin
  necesidad de recordar qué productos estaban activos antes.
- `products.status` significa una sola cosa: la decisión comercial sobre ese producto. Un ramo
  `INACTIVE` por falta de flor sigue `INACTIVE` por falta de flor, pase lo que pase con sus
  categorías.

Se descartó desactivar los productos en cascada. Rompía las tres propiedades anteriores: apagaba
productos válidos en otra categoría, hacía la desactivación irreversible de hecho — reactivar la
categoría no puede saber qué productos apagó — y machacaba el estado comercial original del producto.

> Definición canónica en [`product.md`](product.md), sección 3.3. Se resume aquí porque nace de la
> desactivación de categorías; si las dos versiones discrepan alguna vez, manda `product.md`.

Coste de consulta: el listado público lleva un `EXISTS` sobre `product_categories` y `categories`,
apoyado en `ix_product_categories_category`. Es el mismo tipo de join que ya requiere filtrar el
catálogo por categoría.

### 3.3 Borrado

`DELETE` elimina la categoría y, por `CASCADE`, sus filas de `product_categories`. **Los productos
sobreviven**, incluidos los que se queden sin ninguna categoría.

Un producto que se queda sin ninguna categoría desaparece del escaparate, por la regla 3.2: ni se
lista, ni se busca, ni se abre por enlace. Sigue existiendo con su historial intacto, sigue
comprándose desde un carrito donde ya estuviera, y el panel lo lista en una vista de productos sin
categoría para que el administrador lo recoloque.

No se borran productos en cascada, ni siquiera los que quedan huérfanos:
[ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md) declara `RESTRICT`
en `order_items.product_id`, `stock_movements.product_id` y `purchase_items.product_id`. Cualquier
producto vendido alguna vez, con movimiento de stock o presente en una compra a proveedor, es
historia comercial y contable y no puede borrarse. Un borrado en cascada fallaría con error de clave
ajena en cuanto la categoría tuviera un solo producto vendido, que es el caso normal. Para dar de
baja un producto existe `DISCONTINUED`.

### 3.4 Vista previa de impacto

Antes de desactivar o borrar, el panel consulta el impacto y lo muestra en el diálogo de
confirmación. La consulta no modifica nada.

Devuelve solo lo que **cambia de verdad**, no el recuento completo de productos asociados:

| Dato | Para qué acción | Qué cuenta |
|---|---|---|
| `productsLosingVisibility` | Desactivar | Productos `ACTIVE` que dejarían de verse porque ésta es su única categoría `ACTIVE` |
| `productsLeftWithoutCategory` | Borrar | Productos que se quedarían sin ninguna categoría |
| `totalProducts` | Contexto | Productos asociados, visibles o no |

Un producto que también cuelga de otra categoría activa no aparece en `productsLosingVisibility`:
desactivar esta categoría no le afecta, y listarlo haría que el aviso exagerara el daño.

### 3.5 Orden

`position` ordena el catálogo público. No es único: dos categorías pueden compartir posición, y en
ese caso desempata el nombre.

El reordenado se envía completo, no categoría a categoría: una sola petición con todos los
identificadores en su orden nuevo, aplicada en una transacción. El envío debe contener **todas** las
categorías existentes; si falta alguna, se rechaza con `CATEGORY_POSITIONS_INCOMPLETE`. Un envío
parcial dejaría posiciones a medias sin que nadie se entere.

### 3.6 Imagen

`image_s3_key` guarda la clave del objeto en S3, nunca el binario. La subida es responsabilidad del
módulo `image`: devuelve la clave, y el `POST`/`PUT` de categoría la recibe como un campo más. La
clave la genera el backend en la subida; **una clave enviada por el cliente nunca se acepta tal
cual**, se verifica que corresponda a un objeto subido por ese flujo.

---

## 4. Endpoints

Prefijo `/api/v1` ([`04-rest-conventions.md`](../architecture/04-rest-conventions.md)).

| Método | Ruta | Rol | Devuelve |
|---|---|---|---|
| `GET` | `/categories` | Público | 200 — solo `ACTIVE`, ordenadas por `position` |
| `GET` | `/categories/{idOrSlug}` | Público | 200 — acepta UUID o slug |
| `GET` | `/categories/all` | `ADMIN` | 200 — incluye `INACTIVE` |
| `GET` | `/categories/{id}/impact` | `ADMIN` | 200 — vista previa, sin efectos |
| `POST` | `/categories` | `ADMIN` | 201 |
| `PUT` | `/categories/{id}` | `ADMIN` | 200 |
| `PATCH` | `/categories/{id}/status` | `ADMIN` | 200 |
| `PUT` | `/categories/positions` | `ADMIN` | 200 |
| `DELETE` | `/categories/{id}` | `ADMIN` | 204 |

`{idOrSlug}` acepta las dos formas de identificar una categoría: si el segmento parsea como UUID se
busca por `id`, y si no, por `slug`. Ambas columnas son únicas. El panel de administración usa
siempre el UUID, que no cambia nunca; la web pública usa el slug, que es lo que lleva su URL. No hay
ambigüedad posible, porque un slug jamás tiene forma de UUID: la regla 3.1 lo genera desde el nombre.

Un `GET` público de una categoría `INACTIVE` responde **404**, no 403: un 403 confirmaría que ese
identificador existe.

`GET /categories/all` como ruta separada, en vez de un parámetro `?includeInactive=true` sobre la
ruta pública, evita que un fallo de autorización en un parámetro exponga el catálogo oculto.

---

## 5. Request DTOs

Validación según [`06-validation-conventions.md`](../architecture/06-validation-conventions.md).
Los `@Size` replican el límite de su columna.

### `CreateCategoryRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `name` | String | `@NotBlank`, `@Size(max = 150)` |
| `description` | String | `@Size(max = 2000)`, opcional |
| `imageS3Key` | String | `@Size(max = 500)`, opcional |
| `position` | Integer | `@PositiveOrZero`, opcional (por defecto 0) |

No lleva `slug` — se genera. No lleva `status` — nace `ACTIVE`.

### `UpdateCategoryRequest`

Mismos campos que `CreateCategoryRequest`. `PUT` reemplaza el recurso completo: un campo opcional
ausente se interpreta como borrado de ese valor, no como "no lo toques".

### `ChangeCategoryStatusRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `status` | Enum | `@NotNull`, `ACTIVE` o `INACTIVE` |

### `ReorderCategoriesRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `categoryIds` | List\<UUID\> | `@NotEmpty`, `@Size(max = 200)`, sin repetidos |

La posición de cada categoría es su índice en la lista.

---

## 6. Response DTOs

### `CategoryResponse`

`id`, `name`, `slug`, `description`, `status`, `imageUrl`, `position`, `createdAt`, `updatedAt`.

`imageUrl` es la URL pública servida por el CDN, derivada de `image_s3_key`. La clave S3 en crudo no
sale de la API.

### `CategorySummaryResponse`

`id`, `name`, `slug`, `imageUrl`, `position`. Para el listado público, que no necesita descripción ni
fechas.

### `CategoryImpactResponse`

| Campo | Descripción |
|---|---|
| `totalProducts` | Productos asociados a la categoría |
| `productsLosingVisibility` | Los que dejarían de verse al desactivarla: ésta es su única categoría `ACTIVE` |
| `productsLeftWithoutCategory` | Los que quedarían sin ninguna categoría al borrarla |

Cada lista lleva `id`, `name` y `status` de producto: lo justo para que el diálogo de confirmación
sea legible. Un producto puede aparecer en las dos listas.

## 7. Casos de uso

Un servicio por caso de uso, sin servicios genéricos.

| Use Case | Service | Escritura |
|---|---|---|
| `CreateCategoryUseCase` | `CreateCategoryService` | Sí |
| `UpdateCategoryUseCase` | `UpdateCategoryService` | Sí |
| `ChangeCategoryStatusUseCase` | `ChangeCategoryStatusService` | Sí |
| `ReorderCategoriesUseCase` | `ReorderCategoriesService` | Sí |
| `DeleteCategoryUseCase` | `DeleteCategoryService` | Sí |
| `GetCategoryUseCase` | `GetCategoryService` | No |
| `GetCategoriesUseCase` | `GetCategoriesService` | No |
| `GetCategoryImpactUseCase` | `GetCategoryImpactService` | No |

Commands: `CreateCategoryCommand`, `UpdateCategoryCommand`, `ChangeCategoryStatusCommand`,
`ReorderCategoriesCommand`, `DeleteCategoryCommand`.

Queries: `GetCategoryQuery`, `GetCategoriesQuery`, `GetCategoryImpactQuery`.

`ChangeCategoryStatusService` no escribe en productos: la visibilidad se deriva (regla 3.2). Su
transacción cubre el cambio de estado y su registro de auditoría.

`ReorderCategoriesService` sí necesita transacción propia: actualiza varias filas y un fallo a medias
dejaría el catálogo con posiciones incoherentes.

---

## 8. Output Ports

Según [ADR-003](../architecture/ADR/ADR-003-capability-based-ports.md), capacidades, no repositorios.

| Port | Capacidad |
|---|---|
| `CategoryReadPort` | `findById`, `findBySlug`, `findAllActive`, `findAll` | |
| `CategoryWritePort` | `save`, `delete`, `updatePositions` |
| `CategoryExistencePort` | `existsBySlug`, `existsById` |
| `CategoryProductsPort` | `countByCategory`, `findLosingVisibility`, `findLeftWithoutCategory` |

`CategoryProductsPort` es la única dependencia del módulo hacia productos, y existe solo para la
vista previa de impacto (regla 3.4). Es de **solo lectura**: al no haber cascada, `category` nunca
escribe en un producto. Es un puerto de salida de `category`, no una llamada directa al servicio de
`product`: el acoplamiento queda declarado en una interfaz pequeña en vez de esparcido.

Persistencia según [ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md): JPA para el guardado y la
búsqueda por identificador o slug; JDBC para el listado ordenado y las consultas de impacto, que son
joins con proyección y agregados.

---

## 9. Errores

Formato RFC 7807 ([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)). Enum
`CategoryErrorCode` en `domain/exception/category/`.

| Código | Estado | Cuándo | Excepción |
|---|---|---|---|
| `CATEGORY_NOT_FOUND` | 404 | El identificador no existe, o es `INACTIVE` en acceso público | `CategoryNotFoundException` |
| `CATEGORY_ALREADY_EXISTS` | 409 | El slug generado ya está en uso | `CategoryAlreadyExistsException` |
| `CATEGORY_VALIDATION_FAILED` | 422 | Falla Bean Validation; con `errors[]` | — |
| `CATEGORY_POSITIONS_INCOMPLETE` | 422 | El reordenado no incluye todas las categorías | `CategoryPositionsIncompleteException` |
| `CATEGORY_IMAGE_NOT_FOUND` | 422 | `imageS3Key` no corresponde a un objeto subido | `CategoryImageNotFoundException` |
| `CATEGORY_SLUG_RESERVED` | 422 | El nombre genera un slug reservado (`all`, `positions`) | `CategorySlugReservedException` |

El nombre de la constraint nunca llega al cliente: `uq_categories_slug` se traduce a
`CATEGORY_ALREADY_EXISTS`.

**`CategoryInUseException` se elimina.** Existe hoy en `domain/exception/category/` y suponía
bloquear el borrado de una categoría con productos. Con la regla 3.3 el borrado nunca se bloquea, así
que nadie la lanza, y `CLAUDE.md` prohíbe el código muerto — más aún en el módulo que copian los
otros diez. Si alguna regla futura necesita bloquear un borrado, se creará entonces con su motivo
escrito. Borrarla es tarea de la implementación, no de este documento.

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| Crear una categoría cuyo nombre genera un slug existente | 409 `CATEGORY_ALREADY_EXISTS` |
| Renombrar una categoría | El slug se regenera; la URL pública anterior deja de funcionar |
| Renombrar a un nombre cuyo slug ya existe en otra categoría | 409 |
| Desactivar una categoría | Ningún producto se modifica; dejan de verse los que no tengan otra categoría `ACTIVE` |
| Reactivar la categoría | Los productos vuelven a verse exactamente como estaban; nada que restaurar a mano |
| Desactivar una categoría ya `INACTIVE` | 200, sin efectos; no es un error |
| Producto `INACTIVE` en una categoría `ACTIVE` | Sigue oculto: la regla 3.2 exige las dos condiciones |
| Borrar una categoría con productos | 204; los productos sobreviven sin esa asociación |
| Borrar la última categoría de un producto | 204; desaparece del escaparate; solo se compra desde un carrito donde ya estuviera |
| Producto `ACTIVE` en un carrito cuya categoría se desactiva | Se compra con normalidad: el carrito ya empezado es la excepción de la regla 3.2 |
| Reordenar con un identificador inexistente | 404 `CATEGORY_NOT_FOUND` |
| Reordenar con identificadores repetidos | 422 `CATEGORY_VALIDATION_FAILED` |
| Crear una categoría llamada "All" | 422 `CATEGORY_SLUG_RESERVED`: chocaría con `GET /categories/all` |
| `GET /categories/{idOrSlug}` con un texto que no es UUID ni slug conocido | 404 `CATEGORY_NOT_FOUND` |
| `GET` público de una categoría `INACTIVE` | 404 |
| Dos administradores editando a la vez | Gana el último; `categories` no lleva `version` (sección 2) |

## 11. Decisiones cerradas y alcance ajeno

Este módulo no deja decisiones abiertas.

Resueltas al cerrarlo:

| Punto | Decisión |
|---|---|
| `CategoryInUseException` | Se elimina (sección 9) |
| Búsqueda por slug | `GET /categories/{idOrSlug}`, un solo endpoint (sección 4) |
| Choque de rutas | `all` y `positions` son slugs reservados (sección 3.1) |
| Tamaño del reordenado | Tope de 200 como defensa de carga útil, no como límite de negocio |
| Concurrencia | Sin `version`; gana el último, documentado en la sección 2 |

Definido en otro documento:

- **Vista de productos sin categoría** — `GET /products/all?withoutCategory=true`, en
  [`product.md`](product.md), sección 4.
- **Regla de visibilidad del catálogo** — definición canónica en [`product.md`](product.md),
  sección 3.3. Se resume en la sección 3.2 de este documento porque nace de la desactivación de
  categorías.
