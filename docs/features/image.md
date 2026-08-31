# Image

Subida, variantes y ciclo de vida de las imágenes del catálogo. Es el único módulo que toca ficheros:
todos los demás manejan una referencia a una fila de `images`, nunca bytes ni claves de S3.

Sin superficie pública. Las imágenes las sirve el CDN directamente desde S3, no el backend
(regla 3.5). Reglas transversales en
[`00-security-validation-integrity.md`](00-security-validation-integrity.md).

---

## 1. Resumen

Una imagen es una entidad con vida propia, independiente de dónde se use. Se sube una vez, se asocia a
un producto o a una categoría —o a ninguno—, se puede desasociar y volver a asociar, y solo desaparece
cuando un administrador decide borrarla.

Ese "o a ninguno" es el estado que hace falta explicar, porque no es un error: es la **bandeja**
(regla 3.6). Una imagen que no usa nadie sigue ahí, visible para el administrador, lista para
reutilizarse. Nada la borra sola.

`images` es también la respuesta a una frase que [`product.md`](product.md) y
[`category.md`](category.md) escribieron sin poder cumplir: que una clave enviada por el cliente «se
verifica que corresponda a un objeto subido por ese flujo». Ahora no se envía ninguna clave — se envía
el `id` de una fila que existe porque la creó la subida (regla 3.3).

---

## 2. Tablas implicadas

`images` (`V13`), y las dos columnas que la referencian: `product_images.image_id` y
`categories.image_id`. Esquema en [`../database/README.md`](../database/README.md).

| Columna de `images` | Restricción |
|---|---|
| `s3_key` | `UNIQUE`; clave del **original**, las variantes se derivan de ella (regla 3.4) |
| `content_type` | `image/jpeg`, `image/png`, `image/webp` (`chk_images_content_type`) |
| `byte_size`, `width`, `height` | `> 0`; se miden en la subida, no se aceptan del cliente |
| `uploaded_by_admin_id` | `ON DELETE SET NULL` — la imagen sobrevive a quien la subió |

`V13` sustituye `product_images.s3_key` y `categories.image_s3_key` por claves ajenas. La clave de S3
pasa a vivir en un solo sitio, y las dos referencias son `ON DELETE RESTRICT`: **la base de datos es
quien garantiza que no se puede borrar una imagen en uso** (regla 3.7).

`alt_text` y `position` se quedan en `product_images`, no se mueven a `images`: describen ese uso
concreto de la imagen, no la imagen. La misma foto puede ser la principal de un producto y llevar un
texto alternativo distinto en otro.

---

## 3. Reglas de negocio

### 3.1 La subida pasa por el backend

`POST /admin/images`, multipart. El fichero **atraviesa el backend**, que lo valida antes de subir
nada a S3. No se usan URL prefirmadas: con una subida directa del navegador a S3 el backend nunca ve
los bytes, y la regla 11 de
[`00-security-validation-integrity.md`](00-security-validation-integrity.md) —validar el tipo real,
no la extensión— sería imposible de cumplir en el momento de la subida.

El coste es el ancho de banda de subida pasando por el servidor. Para una floristería que sube unas
pocas fotos al día no significa nada; si algún día sí lo significara, la alternativa es prefirmar y
validar después de la subida, y esa decisión tendría que reabrir la regla 11.

### 3.2 Qué se valida, y en qué orden

1. **Tamaño**, antes de leer nada más: máximo 10 MB. Un fichero mayor se rechaza sin cargarse en
   memoria.
2. **Tipo real**, por los bytes de cabecera del fichero (*magic number*), nunca por la extensión ni
   por el `Content-Type` que declare el cliente. Ambos los escribe quien sube. Se aceptan JPEG, PNG y
   WebP.
3. **Dimensiones**: entre 200×200 y 6000×6000. El mínimo evita subir por error una miniatura como
   original; el máximo acota lo que hay que redimensionar.

Solo después de las tres se sube a S3 y se inserta la fila. Un fichero que falla cualquiera de ellas
no llega a S3, así que no hay nada que limpiar.

`chk_images_content_type` repite la comprobación del punto 2 en la base de datos. No es redundante en
el sentido que importa: es la última barrera si algún día otro camino de escritura se salta este caso
de uso ([`06-validation-conventions.md`](../architecture/06-validation-conventions.md)).

### 3.3 La clave de S3 la genera el backend, y el cliente nunca la ve

La clave es `catalog/{uuid}/original.{ext}`, con el `uuid` generado por el backend. Nada de lo que
envía el cliente entra en ella: ni el nombre del fichero original —que puede traer rutas, caracteres
de control o el nombre de otro objeto— ni ningún identificador que él controle.

**El cliente nunca vuelve a mandar una clave.** Asociar una imagen a un producto o a una categoría se
hace con el `id` de la fila de `images` (regla 4 de [`product.md`](product.md) y de
[`category.md`](category.md)). Un `id` que no exista es 404, y la clave ajena de `V13` lo impide
incluso si el caso de uso no mirara.

### 3.4 Variantes: tres tamaños, claves derivadas

La subida genera tres variantes en WebP y las guarda junto al original:

| Variante | Ancho | Para qué |
|---|---|---|
| `thumb` | 300 px | Listados, carrito, panel |
| `medium` | 800 px | Ficha de producto |
| `large` | 1600 px | Ampliación y pantallas de alta densidad |

La altura es proporcional; nunca se recorta ni se deforma. Una imagen más estrecha que la variante no
se amplía: se sirve tal cual, porque agrandar no añade detalle y sí peso.

**Las claves de las variantes no se guardan en ninguna columna.** Se derivan del prefijo de `s3_key`:
`catalog/{uuid}/thumb.webp`, `medium.webp`, `large.webp`. Guardarlas sería almacenar tres cadenas
deducibles de una cuarta, con la posibilidad de que discrepen — mismo criterio por el que
[`product.md`](product.md) no tiene una columna `is_main` además de `position`.

El original se conserva: es lo que permite regenerar las variantes si algún día cambian los tamaños.

### 3.5 El backend no sirve imágenes

`images` guarda claves; los ficheros los sirve Cloudflare desde S3. La API devuelve URL absolutas ya
construidas (`ImageResponse`, sección 6), no claves: el frontend no tiene que saber ni el bucket ni
el dominio del CDN ni el esquema de variantes.

Ningún endpoint de este módulo devuelve bytes de imagen. Un backend que hiciera de proxy de sus
propias imágenes estaría pagando por cada visita lo que el CDN hace gratis y mejor.

### 3.6 La bandeja: imágenes que no usa nadie

Una imagen está **no asociada** cuando ninguna fila de `product_images` y ninguna categoría la
referencian. No hay columna de estado: se deriva de las referencias, igual que la visibilidad de un
producto en [`product.md`](product.md) o el estado de una promoción en
[`product-discounts.md`](product-discounts.md). Una columna diría "asociada" mucho después de que la
última referencia desapareciera.

`GET /admin/images/unattached` es esa bandeja. Ahí acaban:

- Una subida que nunca se llegó a asociar, porque el administrador cerró el formulario.
- Una imagen sustituida por otra en un producto.
- Las imágenes de un producto borrado (`CASCADE` sobre `product_images` quita las filas de galería;
  las de `images` no las toca nada).

**Desactivar un producto no manda nada a la bandeja.** Un producto `INACTIVE` o `DISCONTINUED`
conserva sus filas de `product_images` intactas: sigue asociado a sus imágenes, y reactivarlo las
recupera tal cual estaban. Solo el borrado real del producto —que la base de datos solo permite si
nunca se vendió ([`product.md`](product.md), regla 3.10)— desasocia.

**Restaurar es volver a asociar.** No hay un endpoint de restauración: el administrador ve la bandeja,
copia el `id` de la imagen que quiere y la incluye en la galería del producto como cualquier otra. No
hacía falta inventar un mecanismo cuando el que ya existe sirve.

### 3.7 Borrado definitivo: manual, y solo si no la usa nadie

`DELETE /admin/images/{id}` borra la fila y los cuatro objetos de S3 (original y tres variantes).
Solo lo hace un administrador, deliberadamente, desde la bandeja.

**Nada borra una imagen automáticamente.** Ni una tarea programada, ni una caducidad, ni el borrado
del producto que la usaba. El almacenamiento es barato y una imagen huérfana no rompe nada; que una
foto desaparezca sin que nadie lo haya decidido, sí. Mismo criterio que las alertas de
[`inventory.md`](inventory.md), que tampoco se cierran solas.

Si la imagen sigue en uso, 409 `IMAGE_IN_USE`. Lo garantiza el `RESTRICT` de `V13`, no una
comprobación previa del caso de uso: entre comprobar y borrar hay una ventana en la que alguien podría
asociarla, y la clave ajena no la tiene.

**El orden al sustituir una imagen es crear antes de borrar.** Se sube la nueva, se actualiza la
galería del producto para que apunte a ella, y solo entonces la antigua queda en la bandeja, donde se
borra si se quiere. En ningún instante el producto se queda sin imagen.

### 3.8 Auditoría

Subir y borrar quedan en `audit_log`
([ADR-010](../architecture/ADR/ADR-010-admin-audit-log.md)), con valores: `images` no lleva datos
personales, así que entra en la lista blanca de `chk_audit_log_changes_pii_free` — un fotógrafo no es
PII, y saber qué clave de S3 se borró es justo lo que se querría reconstruir.

Asociar y desasociar no se auditan aquí: son cambios de `product_images` y de `categories`, y los
audita el módulo dueño de esas tablas.

---

## 4. Endpoints

Prefijo `/api/v1`. Todos `ADMIN`; sin superficie pública (regla 3.5).

| Método | Ruta | Devuelve |
|---|---|---|
| `POST` | `/admin/images` | 201 — multipart; sube, valida y genera variantes |
| `GET` | `/admin/images` | 200 — paginado, más recientes primero |
| `GET` | `/admin/images/unattached` | 200 — la bandeja (regla 3.6) |
| `GET` | `/admin/images/{id}` | 200 |
| `DELETE` | `/admin/images/{id}` | 204 — solo si no la usa nadie |

`unattached` es una ruta propia y no `GET /admin/images?attached=false`: es la bandeja, una vista con
su significado en el panel, no un filtro cualquiera del listado. Y `unattached` no colisiona con
`{id}`, que es un UUID — a diferencia de lo que le pasó a `/categories/all`
([`category.md`](category.md), slugs reservados).

Sin `PUT`: una imagen no se edita. Cambiar la foto de un producto es subir otra y reapuntar la
galería (regla 3.7); `alt_text` y `position` viven en `product_images` y los edita
[`product.md`](product.md).

---

## 5. Request DTOs

### `UploadImageRequest`

Multipart, un solo campo `file`. Sin DTO de Bean Validation: lo que hay que validar de un fichero
—tamaño, bytes de cabecera, dimensiones— no lo expresa ninguna anotación, y va en el servicio
(regla 3.2, y [`06-validation-conventions.md`](../architecture/06-validation-conventions.md)).

No lleva `altText`: el texto alternativo depende de dónde se use la imagen, no de la imagen, y se
manda al asociarla ([`product.md`](product.md), `UpdateProductImagesRequest`).

`DELETE` no lleva cuerpo.

---

## 6. Response DTOs

### `ImageResponse`

`id`, `urls`, `width`, `height`, `byteSize`, `contentType`, `attached`, `createdAt`.

`urls` es un objeto con `thumb`, `medium`, `large` y `original`: URL absolutas del CDN, construidas
por el backend (regla 3.5). **Nunca la clave de S3 en crudo** — el frontend no la necesita para nada y
exponerla revela la estructura del bucket.

`attached` es el booleano derivado de la regla 3.6. Le dice al panel si esa imagen está en uso sin
tener que preguntarlo aparte.

---

## 7. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `UploadImageUseCase` | `UploadImageService` | Sí |
| `DeleteImageUseCase` | `DeleteImageService` | Sí |
| `GetImagesUseCase` / `GetImageUseCase` | — | No |
| `GetUnattachedImagesUseCase` | — | No |

`UploadImageService` **no es transaccional en el sentido habitual**, y conviene decirlo: subir cuatro
objetos a S3 e insertar una fila mezcla una llamada externa con la base de datos, igual que el cobro
en [`order.md`](order.md). El orden lo resuelve sin necesitar saga: primero S3, después la fila. Si S3
falla no hay fila que deshacer; si el `INSERT` falla quedan cuatro objetos sin referencia, invisibles
para todos y borrables a mano. El fallo posible es el inocuo.

`DeleteImageService` va al revés: primero el `DELETE` de la fila —que es donde el `RESTRICT` puede
rechazar la operación—, y solo si la transacción confirma se borran los objetos de S3. Borrar los
ficheros antes de saber si la fila puede irse dejaría filas apuntando a nada.

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `ImageReadPort` | `findById`, `findAll`, `findUnattached` |
| `ImageWritePort` | `save`, `delete` |
| `ImageStoragePort` | `upload`, `delete`, `buildPublicUrl` |
| `ImageProcessorPort` | `probe`, `resize` |

`ImageStoragePort` es la frontera con S3: el dominio no sabe si detrás hay AWS, MinIO o un directorio
local en desarrollo. `ImageProcessorPort` aísla la librería de imagen — `probe` devuelve tipo real y
dimensiones sin decodificar el fichero entero, `resize` produce las variantes de la regla 3.4.

Ninguno de los dos aparece en el dominio: son puertos de salida, y las dos dependencias que aíslan
(un SDK de almacenamiento y una librería de manipulación de imágenes) son exactamente lo que
[ADR-003](../architecture/ADR/ADR-003-capability-based-ports.md) mantiene fuera.

Persistencia ([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): JPA para insertar y borrar;
JDBC para el listado paginado y para la consulta de la bandeja, que es un doble `NOT EXISTS` contra
`product_images` y `categories`.

---

## 9. Errores

Enum `ImageErrorCode` en `domain/exception/image/`
([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `IMAGE_NOT_FOUND` | 404 | No existe |
| `IMAGE_IN_USE` | 409 | `DELETE` sobre una imagen que usa un producto o una categoría (regla 3.7) |
| `IMAGE_TOO_LARGE` | 413 | Supera 10 MB |
| `IMAGE_TYPE_NOT_SUPPORTED` | 422 | Los bytes de cabecera no son JPEG, PNG ni WebP (regla 3.2) |
| `IMAGE_DIMENSIONS_OUT_OF_RANGE` | 422 | Fuera de 200×200 – 6000×6000 |
| `IMAGE_STORAGE_UNAVAILABLE` | 502 | S3 no responde o rechaza la subida |

`IMAGE_TOO_LARGE` usa 413 (`Content Too Large`), no 422: es el único código de este documento donde el
estado HTTP describe exactamente lo que pasó, igual que el 402 de
[`order.md`](order.md).

`IMAGE_TYPE_NOT_SUPPORTED` no distingue "extensión mentirosa" de "formato no admitido". Quien sube una
foto no necesita saberlo, y quien está probando el endpoint tampoco.

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| Un `.exe` renombrado a `.jpg` | 422 `IMAGE_TYPE_NOT_SUPPORTED`: la validación mira los bytes, no el nombre (regla 3.2) |
| Un JPEG válido con `Content-Type: image/png` declarado | Se acepta como JPEG; manda el fichero, no lo que diga el cliente |
| Imagen más estrecha que 800 px | `medium` y `large` se sirven sin ampliar (regla 3.4); las claves existen igual |
| Subir dos veces el mismo fichero | Dos filas distintas, dos claves distintas: el backend no deduplica por contenido. Son dos imágenes que casualmente coinciden, y borrar una no debe afectar a la otra |
| Borrar una imagen usada por un producto `DISCONTINUED` | 409 `IMAGE_IN_USE`: sigue referenciada; el estado comercial del producto no cambia eso |
| Borrar un producto que era el único que usaba una imagen | La imagen aparece en la bandeja, intacta en S3 (regla 3.6) |
| Desactivar un producto | La imagen **no** va a la bandeja: sigue asociada, y reactivar el producto la recupera |
| S3 falla a mitad de subir las variantes | No se inserta la fila; quedan objetos sin referencia en S3, invisibles y borrables a mano (regla 7) |
| La fila se borra pero falla el borrado en S3 | La fila ya no está y los objetos quedan huérfanos. Se registra el error; no se revierte el borrado, porque la imagen ya no la usa nadie |
| `GET /admin/images/unattached` con la bandeja vacía | 200 y lista vacía; no es un 404 |

---

## 11. Alcance ajeno

- **Galería de un producto: orden, texto alternativo, cuál es la principal** —
  [`product.md`](product.md), regla 3.9.
- **Imagen de una categoría** — [`category.md`](category.md), regla 3.6.
- **Configuración del CDN y del bucket** — despliegue, no backend. Este documento solo asume que
  existe un dominio público desde el que se sirven los objetos.
- **Limpieza automática de huérfanas** — no existe, por decisión explícita (regla 3.7). Por eso
  [`scheduled-tasks.md`](scheduled-tasks.md) no tiene ninguna tarea de imágenes.
