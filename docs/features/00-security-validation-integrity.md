# 00 — Seguridad, validación e integridad

Documento transversal. Toda funcionalidad descrita en `docs/features/` se apoya en las reglas de
este fichero; los documentos de feature no repiten estas reglas, las referencian.

Fuentes de verdad relacionadas:

- [`docs/database/README.md`](../database/README.md) — estructura de la base de datos.
- [ADR-005](../architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md) — protección de PII y tokenización de pagos.
- [ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md) — integridad histórica y ciclo de vida del dato.
- [ADR-008](../architecture/ADR/ADR-008-refresh-token-rotation.md) — rotación y revocación de refresh tokens.
- [ADR-009](../architecture/ADR/ADR-009-optimistic-locking.md) — bloqueo optimista en raíces de agregado.
- [ADR-010](../architecture/ADR/ADR-010-admin-audit-log.md) — registro de auditoría de administrador.
- [ADR-011](../architecture/ADR/ADR-011-idempotent-money-operations.md) — idempotencia en operaciones que mueven dinero.
- [ADR-012](../architecture/ADR/ADR-012-api-error-contract.md) — formato de error de la API y códigos de negocio.
- [`06-validation-conventions.md`](../architecture/06-validation-conventions.md) — cómo se valida (este documento dice qué se valida).

---

## 1. Autenticación

### Transporte

El **refresh token** viaja en cookie `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`; el
frontend nunca lo ve. `SameSite=Strict` es la protección CSRF de esos endpoints: el navegador no envía
la cookie en peticiones originadas fuera del sitio. El **access token** viaja en el cuerpo de la
respuesta y el frontend lo guarda solo en memoria, enviándolo en `Authorization: Bearer` — nunca en
`localStorage`, donde un XSS lo alcanzaría. Detalle en [`auth.md`](auth.md), regla 3.1.

### Access token (JWT, stateless)

No se persiste. Vida útil: **15 minutos** para cliente, **5 minutos** para administrador.

Claims mínimos: sujeto (`customer_id` o `admin_user_id`), tipo de sujeto, rol (`OWNER`/`ADMIN` para
administradores), emisión y expiración. **Ningún claim contiene PII**: ni email, ni teléfono, ni
nombre. Un JWT viaja en cabeceras, se registra en logs de proxy y no está cifrado, solo firmado.

### Refresh token

Persistido en `refresh_tokens` como `token_hash` (SHA-256, [ADR-005](../architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md)). Nunca se almacena en claro.

Rotación de un solo uso: cada renovación emite un par nuevo y revoca (`revoked_at`) el token
presentado. Detalle completo en [ADR-008](../architecture/ADR/ADR-008-refresh-token-rotation.md).

**Detección de reuso.** Presentar un refresh token ya revocado revoca **toda la familia**
(`family_id`) y fuerza reautenticación completa. La API debe distinguir este caso del de expiración
normal, porque el cliente debe reaccionar distinto:

| Situación | Respuesta | Qué hace el cliente |
|---|---|---|
| Refresh válido | 200 con par nuevo | Continúa |
| Refresh expirado | 401 `TOKEN_EXPIRED` | Login normal |
| Refresh ya usado (reuso) | 401 `SESSION_REVOKED` | Logout total + aviso visible al usuario |

Vida máxima de la familia: 30 días cliente, 12 horas administrador. Tope absoluto desde la creación,
no ventana deslizante.

### TOTP de administrador

`admin_users.totp_secret_encrypted` cifrado (AES-256-GCM). El segundo factor es **obligatorio** para
todo administrador: el login del panel es de dos pasos, y un admin recién creado
(`totp_enabled = false`) no accede a nada hasta enrolar su TOTP en ese primer acceso. Un código no se
acepta dos veces (`totp_last_used_step`, `V11`). Detalle en [`auth.md`](auth.md), reglas 3.3 y 3.4.

---

## 2. Autorización

Spring Security con `@PreAuthorize` a nivel de método. La regla vive junto al caso de uso que
protege, no en un fichero de configuración lejano.

Sujetos:

| Sujeto | Origen | Puede |
|---|---|---|
| Anónimo | Sin token | Catálogo público, carrito de invitado, checkout de invitado |
| Cliente | `customers` (`REGISTERED`) | Lo anterior + su perfil, sus direcciones, sus pedidos |
| `ADMIN` | `admin_users` | Panel completo: catálogo, pedidos, stock, compras |
| `OWNER` | `admin_users` | Lo de `ADMIN` + alta y baja de administradores |

**Autorización a nivel de recurso, no solo de rol.** Que un cliente esté autenticado no le da acceso
al pedido de otro cliente. Todo caso de uso que recibe un identificador de recurso propiedad de un
cliente comprueba la pertenencia dentro del servicio, nunca solo en el controlador. Un `GET
/orders/{id}` con rol correcto y pedido ajeno responde **404**, no 403: un 403 confirmaría que ese
identificador existe.

Un cliente con `status = 'ARCHIVED'` no puede autenticarse (no tiene `password_hash`, garantizado por
`chk_customers_archived_no_pii`). Un administrador con `active = false` tampoco.

---

## 3. Protección de datos personales

Resumen operativo; el detalle y el porqué están en [ADR-005](../architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md).

| Dato | Tratamiento | Consecuencia para la API |
|---|---|---|
| Contraseñas | Argon2id | Nunca se devuelven ni se comparan fuera del verificador |
| Email, teléfono | AES-256-GCM + HMAC-SHA256 | Búsqueda solo por igualdad exacta |
| Nombre, apellidos | AES-256-GCM, sin HMAC | No hay búsqueda por nombre |
| Direcciones, destinatario, coordenadas, mensaje de tarjeta | AES-256-GCM | Solo lectura por el propietario o por administrador |
| Código postal, `distance_km` | En claro | Necesarios para la tarifa de envío |
| Tarjetas | Solo token de la pasarela | El PAN nunca llega al backend |
| Tokens de sesión y verificación | SHA-256 | Nunca se devuelven dos veces |

**Claves.** La clave AES y el pepper HMAC se inyectan por entorno (variable de entorno o gestor de
secretos externo). Nunca en el repositorio, en una migración, en una tabla ni en un fichero de
configuración versionado.

**Búsqueda de clientes en el panel:** email exacto, teléfono exacto o número de pedido. No hay
búsqueda parcial por nombre, y añadirla exige revisar [ADR-005](../architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md) primero.

---

## 4. Validación de entrada

El mecanismo (Bean Validation, validadores propios, validación de dominio, qué capa valida qué)
está en [`06-validation-conventions.md`](../architecture/06-validation-conventions.md). Aquí van las
reglas concretas de este dominio.

### Reglas que necesitan validador propio

| Regla | Dónde | Por qué no basta una anotación |
|---|---|---|
| `products.attributes` (JSONB) | Servicio | Cada clave debe existir en `product_attribute_definitions` y su valor respetar el `data_type` declarado (`TEXT`/`NUMBER`/`BOOLEAN`) |
| Teléfono | DTO | Formato nacional, normalizado antes de cifrar y de calcular el HMAC |
| Franja de entrega | Servicio | `slot_from < slot_to`, dentro del horario de la tienda, y fecha no pasada |
| Dirección de entrega | Servicio | La distancia geocodificada debe caer en algún tramo activo de `shipping_rates`; por encima de 10 km no se reparte |
| Contraseña | DTO | Longitud mínima y comprobación de que no está en una lista de contraseñas comunes |

### Normalización antes de cifrar

Email y teléfono se normalizan (minúsculas, espacios fuera, prefijo internacional) **antes** de
calcular el HMAC. Sin esto, `Ana@X.com` y `ana@x.com` producen dos HMAC distintos y
`uq_customers_email_hash` deja de impedir el duplicado que existe para impedir.

### Límites

Todo campo de texto lleva `@Size` con el mismo límite que su columna. Toda colección lleva tamaño
máximo (líneas de carrito, imágenes por producto, direcciones por cliente). Toda consulta paginada
lleva tamaño de página máximo. Sin esto, un cliente puede pedir el catálogo entero en una llamada.

---

## 5. Integridad de datos

Tres mecanismos, cada uno para un problema distinto. No son intercambiables.

### 5.1 Constraints de base de datos

Es el único nivel que no se puede saltar. Todo lo que puede expresarse como `CHECK`, `UNIQUE`,
`EXCLUDE` o clave ajena vive ahí y **no** se reimplementa en Java como sustituto: la validación en
Java es para dar un mensaje de error decente, no para garantizar el invariante.

### 5.2 `UPDATE` condicional

Para reservar una cantidad limitada sin leer antes de escribir:

- `products.stock` — `UPDATE ... WHERE stock IS NOT NULL AND stock >= :quantity`
- `product_discounts.quantity_sold` — `UPDATE ... WHERE quantity_limit IS NULL OR quantity_sold + :quantity <= quantity_limit`

Cero filas afectadas significa "no hay disponibilidad", y la operación se rechaza. Nunca
`SELECT`-comprobar-`UPDATE`: entre la lectura y la escritura cabe otra venta.

### 5.3 Bloqueo optimista (`version`)

En `products`, `orders`, `customers`, `customer_payment_methods`, `admin_users` ([ADR-009](../architecture/ADR/ADR-009-optimistic-locking.md)). Detecta
que la fila cambió entre la lectura y la escritura. El conflicto sale como **409**, nunca como
reintento silencioso: la aplicación no puede saber si los dos cambios eran compatibles.

Las entidades hijas no llevan `version`: se escriben siempre a través de su raíz de agregado.

### 5.4 Invariantes que la base de datos no puede garantizar

Un `CHECK` no puede consultar otra tabla ni otra fila. Estos invariantes los sostiene un único punto
de escritura transaccional, y romperlos no da error de base de datos — da datos incoherentes en
silencio:

| Invariante | Único punto de escritura |
|---|---|
| Un producto sin gestión de stock (`stock IS NULL`) no genera movimientos | `RegisterStockMovementService` |
| `stock_movements.resulting_stock` coincide con `products.stock` | El mismo servicio, en una transacción |
| Todas las filas de una familia de refresh tokens comparten `expires_at` | El caso de uso de rotación |
| Un pedido `WEB` tiene `customer_id` al crearse | `PlaceOrderService` |

Las consultas de reconciliación de stock están en [`docs/database/README.md`](../database/README.md) y deben devolver cero
filas.

### 5.5 Transacciones

Una transacción por caso de uso, abierta en el servicio, nunca en el controlador ni en el adaptador.
Detalle en [`07-transaction-conventions.md`](../architecture/07-transaction-conventions.md).

Las llamadas a sistemas externos (pasarela de pago, email, geocodificación) **no** van dentro de la
transacción de base de datos: una pasarela lenta no puede mantener abierta una transacción que
bloquea filas.

---

## 6. Idempotencia

Los `POST` que mueven dinero exigen la cabecera `Idempotency-Key` (UUID generado por el cliente):
`POST /checkout`, `POST /orders/counter` ([`order.md`](order.md), sección 4) y
`POST /orders/{id}/refund` ([`payment.md`](payment.md), regla 3.4). Detalle en
[ADR-011](../architecture/ADR/ADR-011-idempotent-money-operations.md).

| Situación | Respuesta |
|---|---|
| Clave nueva | Se ejecuta la operación; 201 |
| Clave repetida, operación terminada | Se relee el recurso por `resource_id`; mismo estado que la primera vez |
| Clave repetida, operación en curso | 409 `OPERATION_IN_PROGRESS` |
| Clave repetida, cuerpo distinto | 422 `IDEMPOTENCY_KEY_REUSED` |

El cliente debe **conservar** la clave durante toda la operación. Regenerarla en el reintento anula
el mecanismo entero.

El `request_fingerprint` es, por defecto, el hash del cuerpo de la petición. `POST /checkout` es la
excepción: su cuerpo no lleva productos, así que el fingerprint incorpora también el contenido del
carrito en ese instante — ver [`payment.md`](payment.md), regla 3.8.

---

## 7. Límite de peticiones

Bucket4j, clave dual **IP + identificador** (email, o el sujeto autenticado). La IP sola es
insuficiente: una IP compartida bloquearía a usuarios legítimos, y un atacante distribuido evita el
límite. Se cuentan ambas y se aplica la más restrictiva.

IP real: cabecera **`CF-Connecting-IP`** (Cloudflare). La IP del socket es la del proxy, no la del
cliente. `CF-Connecting-IP` solo se acepta si la petición llega efectivamente del rango de
Cloudflare; en caso contrario cualquiera podría falsificarla.

Endpoints con límite obligatorio: login de cliente, login de administrador, verificación TOTP,
solicitud de reseteo de contraseña, verificación de email, reenvío de verificación, renovación de
sesión (`refresh`) — sin límite en este último, un refresh token robado permite renovar en bucle sin
coste ([`auth.md`](auth.md), sección 4).

**Respuesta uniforme.** El login responde igual con email inexistente que con contraseña incorrecta,
y con el mismo tiempo de respuesta. Distinguirlos convierte el endpoint en un enumerador de cuentas.
Lo mismo aplica al reseteo de contraseña: responde igual exista o no la cuenta.

---

## 8. Auditoría

`audit_log` ([ADR-010](../architecture/ADR/ADR-010-admin-audit-log.md)) registra las acciones de administrador no cubiertas ya por
`order_status_history` ni `stock_movements`, que siguen siendo la fuente de verdad de sus propios
cambios y no se duplican.

`changed_fields` siempre; `changes` (valores antes/después) **solo** en entidades sin PII, con la
lista blanca en `chk_audit_log_changes_pii_free`. Añadir una entidad a esa lista exige una migración
explícita — que es justo el momento en que alguien se pregunta si esa entidad lleva datos personales.

También se registran `LOGIN` y `LOGIN_FAILED` de administrador.

---

## 9. Registro de actividad y errores

**Ningún log contiene PII.** Ni email, ni teléfono, ni nombre, ni dirección, ni token, ni el cuerpo
completo de una petición de checkout. Se registran identificadores (UUID), nunca los valores que
identifican a la persona.

Los errores de la API no filtran detalle interno: ni traza de excepción, ni SQL, ni nombre de
constraint. Un `uq_customers_email_hash` violado se traduce a un mensaje de negocio, no se propaga.

---

## 10. Formato de error

RFC 7807 (`application/problem+json`). Contrato completo y tabla de mapeo en
[ADR-012](../architecture/ADR/ADR-012-api-error-contract.md).

```json
{
  "type": "https://api.rosyfloristas.com/errors/validation",
  "title": "Validation failed",
  "status": 422,
  "detail": "El pedido no se puede crear",
  "instance": "/api/v1/orders",
  "code": "ORDER_VALIDATION_FAILED",
  "errors": [{ "field": "deliveryDate", "code": "DATE_IN_PAST" }]
}
```

`detail` es para una persona y puede traducirse. `code` es para el programa cliente y no cambia una
vez publicado: renombrarlo rompe la API.

Los códigos viven en un enum por módulo, junto a las excepciones de dominio
(`domain/exception/<módulo>/<Módulo>ErrorCode`). El enum no conoce HTTP; el mapeo a estado lo hace
`infrastructure/web/advice`.

Códigos citados en este documento, ya fijados por sus ADR: `SESSION_REVOKED`, `TOKEN_EXPIRED`,
`OPERATION_IN_PROGRESS`, `IDEMPOTENCY_KEY_REUSED`, `RESOURCE_MODIFIED`.

---

## 11. Superficie HTTP

- HTTPS obligatorio; HTTP redirige.
- CORS restringido a los orígenes del frontend, declarados por configuración. Nunca `*` con
  credenciales.
- Cabeceras: `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`,
  `Content-Security-Policy`.
- Tamaño máximo de cuerpo y de subida de fichero limitados.
- Las imágenes se suben **a través del backend**, que valida tamaño, tipo real (por los bytes de
  cabecera, no por la extensión ni por el `Content-Type` declarado) y dimensiones antes de escribir
  nada en S3. La clave la genera el backend y el cliente nunca la ve: asocia imágenes por el `id` de
  la fila de `images`. Detalle en [`image.md`](image.md), reglas 3.1 a 3.3.

---

## 12. Pendiente de decidir

No están cerradas. No implementar ninguna de ellas por deducción:

1. **Rotación de la clave AES y del pepper HMAC.** [ADR-005](../architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md) fija dónde viven, no cómo se rotan. Rotar
   el pepper obliga a recalcular todos los HMAC; rotar la clave, a recifrar. Necesita su propio ADR.
2. **Catálogo publicado de códigos de error.** El formato y el sitio están decididos (ADR-012); el
   catálogo de `docs/api/` se genera cuando existan los enums de cada módulo.
3. **Valores concretos de los límites de peticiones.** El mecanismo está decidido; los números no.
4. **Periodo de retención (`app.retention.orders-period`).** [ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md) lo deja explícitamente al
   requisito legal aplicable.
5. **Purga de `idempotency_keys` y de tokens caducados.** Hace falta una tarea programada; su
   frecuencia y su ventana no están fijadas.
