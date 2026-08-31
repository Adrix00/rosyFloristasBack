# Admin

Gestión de `admin_users` como entidad: alta, baja, cambio de rol, reseteo de contraseña y de TOTP.
Todo lo que hace un administrador **con** su sesión vive en el documento del módulo correspondiente;
esto es lo que se hace **sobre** los administradores.

No incluye login, sesión ni el enrolamiento de TOTP: eso es [`auth.md`](auth.md), que consume las
columnas que este documento escribe (`password_hash`, `active`, `role`, `password_change_required`).
Reglas transversales en
[`00-security-validation-integrity.md`](00-security-validation-integrity.md).

---

## 1. Resumen

Dos roles, con una única diferencia: **`OWNER` puede gestionar administradores, `ADMIN` no**. En todo
lo demás —catálogo, pedidos, stock, compras— tienen exactamente las mismas atribuciones
(00-security, regla 2). Por eso este documento es, en la práctica, la definición de lo que significa
ser `OWNER`.

`admin_users` es una tabla pequeña y de vida larga: un puñado de filas que rara vez cambian, y cuyo
único riesgo real es quedarse sin ninguna que sirva para entrar. Las reglas 3.6 y 3.7 existen para
eso.

---

## 2. Tablas implicadas

`admin_users`. Esquema en [`../database/README.md`](../database/README.md).

| Columna | Restricción |
|---|---|
| `email_encrypted` / `email_hash` | AES-256-GCM + HMAC; `UNIQUE` sobre el hash ([ADR-005](../architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md)) |
| `password_hash` | Argon2id; `NOT NULL` siempre — un admin sin contraseña no existe |
| `role` | `OWNER`, `ADMIN` (`chk_admin_users_role`) |
| `totp_secret_encrypted` / `totp_enabled` | Reseteables por el `OWNER` (regla 3.5) |
| `totp_last_used_step` | `V11`; se limpia junto al secreto |
| `password_change_required` | `V12`; `true` mientras la contraseña la fijó otro (reglas 3.2 y 3.4) |
| `active` | Baja lógica; no hay borrado (regla 3.6) |
| `version` | Bloqueo optimista ([ADR-009](../architecture/ADR/ADR-009-optimistic-locking.md)) |

---

## 3. Reglas de negocio

### 3.1 Solo el `OWNER` gestiona administradores

Todos los endpoints de la sección 4 exigen `OWNER`, salvo los dos de `/admin/me`, que son de cualquier
administrador sobre sí mismo. Un `ADMIN` que llame a `/admin/users` recibe 403: aquí sí es un 403 y no
un 404, porque no es un problema de pertenencia de un recurso —no se filtra la existencia de nada—,
sino de atribución del rol (00-security, regla 2).

### 3.2 Alta: el `OWNER` fija una contraseña provisional

`POST /admin/users` con email, rol y una contraseña que teclea el `OWNER`. La fila nace con
`active = true`, `totp_enabled = false` y `password_change_required = true` (`V12`).

Esa contraseña viaja al nuevo administrador por un canal humano —en persona, por teléfono— y es el
punto más débil de todo el flujo de acceso al panel. El diseño lo asume y lo acota: es provisional,
solo sirve para un primer acceso, y ese primer acceso **no da acceso a nada** hasta que el admin
enrola su TOTP ([`auth.md`](auth.md), regla 3.4) y sustituye la contraseña (regla 3.4 de este
documento).

**Por qué no un enlace de invitación por email.** `verification_tokens.customer_id` es
`UUID NOT NULL REFERENCES customers`: la tabla es exclusiva de clientes, y un enlace de invitación
para administradores exigiría generalizarla a los dos sujetos, con la misma forma que
`refresh_tokens` (`customer_id` anulable, `admin_user_id`, `CHECK` de exactamente uno). Se descartó
por ahora — el panel lo usan dos o tres personas que se conocen, y la migración solo tendría sentido
si `notification.md` acaba justificando un flujo de invitación completo.

### 3.3 Email

El email es el identificador de acceso. Un administrador **no puede cambiarse el suyo**; solo el
`OWNER` puede, con `PUT /admin/users/{id}`.

Esto es lo contrario de lo que hace [`customer.md`](customer.md) (regla 3.7), donde un administrador
tiene prohibido tocar el email de un cliente, y la asimetría es deliberada. Allí el email cambia sin
que el dueño de la cuenta se entere y sin pasar por la verificación que existe para los clientes; aquí
no hay verificación que saltarse, y quien lo cambia es el `OWNER`, que ya puede hacer cualquier cosa
en el panel — no gana ningún privilegio que no tuviera. Queda auditado como todo lo demás (regla 3.8).

### 3.4 Contraseña

| Quién | Endpoint | Efecto |
|---|---|---|
| El propio admin | `POST /admin/me/password` | Exige `currentPassword`; deja `password_change_required = false` |
| El `OWNER` | `POST /admin/users/{id}/password-reset` | Fija una provisional y pone `password_change_required = true` |

No hay reseteo por email, por la misma razón que no hay invitación (regla 3.2). Un administrador que
olvida su contraseña se lo pide al `OWNER`.

**Mientras `password_change_required` sea `true`, la sesión no sirve para nada más que cambiarla**
([`auth.md`](auth.md), regla 3.9): el administrador se autentica con normalidad, TOTP incluido, pero
cualquier otro endpoint del panel responde 403 hasta que la sustituya.

El reseteo por el `OWNER` **revoca todas las sesiones** de ese administrador
([`auth.md`](auth.md), regla 3.8): si se resetea porque alguien accedió a su cuenta, dejar viva la
sesión del intruso vaciaría la operación de sentido.

### 3.5 Reseteo de TOTP

`POST /admin/users/{id}/totp-reset`, solo `OWNER`. Pone `totp_secret_encrypted`,
`totp_last_used_step` y `totp_enabled` a su estado inicial, de modo que el siguiente acceso vuelve a
pasar por el enrolamiento de [`auth.md`](auth.md) (regla 3.4).

Es la única salida cuando un administrador pierde el móvil donde tenía la app de códigos: sin esto se
queda fuera del panel para siempre, porque el segundo factor es obligatorio y nadie más conoce su
secreto.

Revoca también todas sus sesiones: el motivo típico del reseteo es un dispositivo perdido o robado, y
las sesiones abiertas en ese dispositivo son exactamente lo que hay que cerrar.

La contraseña **no** se toca: son dos factores independientes, y resetear los dos a la vez cuando solo
se perdió uno amplía el problema sin necesidad.

### 3.6 Baja: `active = false`, nunca borrado

`PATCH /admin/users/{id}/status`. Sin `DELETE`, y no por comodidad: `order_status_history`,
`stock_movements`, `audit_log` e `inventory_alerts` referencian al administrador con
`ON DELETE SET NULL`, así que un borrado real no fallaría — dejaría en blanco la autoría de cada
cambio de estado de pedido, cada movimiento de stock y cada acción auditada que esa persona hizo.
`active = false` conserva la traza y cumple lo que la baja realmente significa: que ya no entra.

La baja revoca todas sus sesiones ([`auth.md`](auth.md), regla 3.8). Sin eso, un administrador
desactivado seguiría dentro del panel hasta que caducara su token, que es justo lo que la baja
pretende impedir.

Reactivar (`active = true`) devuelve el acceso tal y como estaba: mismo rol, misma contraseña, mismo
TOTP.

### 3.7 Siempre queda un `OWNER` activo

Se rechaza cualquier acción que dejara la tabla sin ningún `OWNER` con `active = true`:

- Desactivar al último `OWNER` activo.
- Cambiarle el rol a `ADMIN`.

Un `OWNER` **sí** puede desactivarse o degradarse a sí mismo, siempre que quede otro `OWNER` activo —
es lo que necesita quien deja la empresa y ya ha traspasado el puesto.

Sin esta regla, un solo clic deja el panel sin nadie capaz de dar de alta a un administrador, y la
única salida sería tocar la base de datos a mano. La comprobación va dentro de la transacción, sobre
el recuento real de `OWNER` activos, no sobre lo que el frontend creyera saber.

### 3.8 Auditoría

Toda acción de este documento se registra en `audit_log`
([ADR-010](../architecture/ADR/ADR-010-admin-audit-log.md)): quién la hizo, sobre quién, y qué campos
cambiaron.

**Solo `changed_fields`, nunca valores.** `admin_users` no está en la lista blanca de
`chk_audit_log_changes_pii_free`, y la propia base de datos rechazaría un `changes` no nulo para esta
entidad. Es lo correcto: sus columnas son un email cifrado, un hash de contraseña y un secreto TOTP,
y ninguno de los tres tiene por qué existir en claro en una segunda tabla.

### 3.9 Arranque: el primer `OWNER`

Nadie puede crear al primer `OWNER` desde el panel — hacen falta credenciales de `OWNER` para llamar a
`POST /admin/users`. Lo crea una tarea de arranque de la aplicación, que al levantar comprueba si
existe algún `OWNER` activo y, si no, lo crea leyendo email y contraseña de variables de entorno.

**No es una migración de Flyway**, aunque conceptualmente ocupe ese hueco: una migración SQL no puede
leer las variables de entorno de la aplicación, y la contraseña tiene que pasar por Argon2id, que vive
en Java. Es un `ApplicationRunner` en `infrastructure/config`, idempotente por construcción — si ya
hay un `OWNER` activo no hace nada, así que reiniciar la aplicación no tiene efecto.

Ese `OWNER` nace igual que cualquier otro: `password_change_required = true` y `totp_enabled = false`,
así que su primer acceso pasa por enrolar TOTP y sustituir la contraseña de entorno. Ninguna
credencial queda escrita en el repositorio, ni en una migración, ni en un fichero de configuración
versionado — mismo criterio que las claves de cifrado (00-security, regla 3).

---

## 4. Endpoints

Prefijo `/api/v1`.

### Gestión (`OWNER`)

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/admin/users` | 200 — lista; filtra por `active` y `role` |
| `GET` | `/admin/users/{id}` | 200 |
| `POST` | `/admin/users` | 201 |
| `PUT` | `/admin/users/{id}` | 200 — email y rol |
| `PATCH` | `/admin/users/{id}/status` | 200 — activa o desactiva |
| `POST` | `/admin/users/{id}/password-reset` | 204 |
| `POST` | `/admin/users/{id}/totp-reset` | 204 |

`/admin/users` son los usuarios **del panel**; los clientes son `/admin/customers`
([`customer.md`](customer.md)). Sin paginación: son un puñado de filas y no crecen.

`password-reset` y `totp-reset` son acciones con efectos propios —revocar sesiones, forzar un
reenrolamiento—, no la edición de un campo, así que tienen su propio verbo en vez de esconderse en el
`PUT`. Mismo criterio que `receive` y `revert` en [`purchasing.md`](purchasing.md).

### Propios (cualquier administrador)

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/admin/me` | 200 |
| `POST` | `/admin/me/password` | 204 |

`POST /admin/me/password` es el único endpoint del panel accesible con
`password_change_required = true` ([`auth.md`](auth.md), regla 3.9).

---

## 5. Request DTOs

### `CreateAdminRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `email` | String | `@NotBlank`, `@Email`, `@Size(max = 255)`; normalizado antes de cifrar y de calcular el HMAC (00-security, regla 4) |
| `password` | String | `@NotBlank`, misma política que el registro de cliente (validador propio) |
| `role` | Enum | `@NotNull`: `OWNER` o `ADMIN` |

La contraseña provisional cumple la política completa: es corta de vida, pero está activa hasta que el
admin entre, y una contraseña débil durante ese tiempo es una contraseña débil.

### `UpdateAdminRequest`

`email` y `role`, mismas reglas. No incluye contraseña ni ninguna columna de TOTP: cada una tiene su
propio endpoint (regla 4).

### `ChangeAdminStatusRequest`

`active`: `@NotNull`.

### `ChangeOwnPasswordRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `currentPassword` | String | `@NotBlank` |
| `newPassword` | String | `@NotBlank`, misma política; distinta de `currentPassword` |

`POST /admin/users/{id}/password-reset` y `/totp-reset` no llevan cuerpo. El primero devuelve la
contraseña generada en la respuesta (sección 6); el segundo no devuelve nada porque no genera ningún
secreto — el secreto TOTP nuevo lo crea el propio administrador al reenrolarse.

---

## 6. Response DTOs

### `AdminResponse`

`id`, `email`, `role`, `active`, `totpEnabled`, `passwordChangeRequired`, `createdAt`, `updatedAt`.

Email descifrado: quien lee esto es el `OWNER`, o el propio administrador en `/admin/me`. Nunca
`password_hash`, `totp_secret_encrypted` ni `totp_last_used_step`.

`totpEnabled` y `passwordChangeRequired` no son detalles internos: le dicen al `OWNER` si esa persona
ya terminó de configurar su acceso o sigue a medias.

### `PasswordResetResponse`

`temporaryPassword`.

Se devuelve **una sola vez**, en la respuesta del reseteo, para que el `OWNER` se la comunique. No se
guarda en claro en ningún sitio: en `admin_users` solo queda su hash Argon2id, y `audit_log` registra
que hubo un reseteo, nunca el valor (regla 3.8).

---

## 7. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `CreateAdminUseCase` | `CreateAdminService` | Sí |
| `UpdateAdminUseCase` | `UpdateAdminService` | Sí — comprueba la regla 3.7 al cambiar el rol |
| `ChangeAdminStatusUseCase` | `ChangeAdminStatusService` | Sí — regla 3.7, y revoca sesiones al desactivar |
| `ResetAdminPasswordUseCase` | `ResetAdminPasswordService` | Sí |
| `ResetAdminTotpUseCase` | `ResetAdminTotpService` | Sí |
| `ChangeOwnPasswordUseCase` | `ChangeOwnPasswordService` | Sí |
| `GetAdminsUseCase` / `GetAdminUseCase` | — | No |
| `BootstrapOwnerUseCase` | `BootstrapOwnerService` | Sí — tarea de arranque, sin controlador (regla 3.9) |

`UpdateAdminService` y `ChangeAdminStatusService` comparten la comprobación del último `OWNER`
(regla 3.7), cada uno desde su transacción. No se extrae a un servicio común: son dos reglas de dos
casos de uso, y un `LastOwnerValidator` compartido sería la clase de utilidad que
[`00-project-principles`](../architecture/00-project-principles.md) desaconseja sin justificación.

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `AdminReadPort` | `findById`, `findByEmailHash`, `findAll`, `countActiveOwners` |
| `AdminWritePort` | `save` |
| `RevokeTokenFamilyPort` | `revokeAllForSubject` — definido en [`auth.md`](auth.md) |
| `PasswordHasherPort` | `hash`, `matches` — definido en [`auth.md`](auth.md) |

`countActiveOwners` es una capacidad explícita, no un `findAll` filtrado en memoria: la regla 3.7 se
comprueba dentro de la transacción y sobre la base de datos, que es la única que sabe cuántos `OWNER`
activos hay de verdad en ese instante.

Este módulo no define puertos propios de sesión ni de cifrado de contraseña: reutiliza los de
`auth.md`, que es donde vive el mecanismo. Persistencia
([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): JPA. El listado no está paginado y no
necesita JDBC.

---

## 9. Errores

Enum `AdminErrorCode` en `domain/exception/admin/`
([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `ADMIN_NOT_FOUND` | 404 | No existe |
| `ADMIN_EMAIL_ALREADY_EXISTS` | 409 | `uq_admin_users_email_hash` |
| `LAST_OWNER_CANNOT_BE_REMOVED` | 409 | Desactivar o degradar al último `OWNER` activo (regla 3.7) |
| `INVALID_CURRENT_PASSWORD` | 401 | `currentPassword` no coincide |
| `PASSWORD_UNCHANGED` | 422 | La contraseña nueva es igual a la actual |
| `RESOURCE_MODIFIED` | 409 | Conflicto de bloqueo optimista (ADR-009) |
| `ADMIN_VALIDATION_FAILED` | 422 | Bean Validation; con `errors[]` |

`PASSWORD_CHANGE_REQUIRED` (403, sesión limitada a cambiar la contraseña) vive en `AuthErrorCode`
([`auth.md`](auth.md)): lo emite el filtro de seguridad, no un caso de uso de este módulo.

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| El único `OWNER` intenta desactivarse | 409 `LAST_OWNER_CANNOT_BE_REMOVED` (regla 3.7) |
| El único `OWNER` intenta cambiarse el rol a `ADMIN` | 409 `LAST_OWNER_CANNOT_BE_REMOVED`: degradar es quedarse sin `OWNER` igual que desactivar |
| Un `OWNER` se desactiva habiendo otro `OWNER` activo | Permitido; sus sesiones caen de inmediato y queda fuera del panel |
| Desactivar a un `OWNER` inactivo que era el segundo | La cuenta se hace sobre `OWNER` **activos**; un `OWNER` desactivado no cuenta para la regla 3.7 |
| Crear un admin con un email ya usado por otro admin | 409 `ADMIN_EMAIL_ALREADY_EXISTS` |
| Crear un admin con el email de un **cliente** | Permitido: son tablas distintas, y una persona puede ser clienta de la tienda y trabajar en ella |
| Un `ADMIN` llama a `/admin/users` | 403: no filtra la existencia de ningún recurso, solo falta el rol (regla 3.1) |
| Admin desactivado en mitad de una sesión activa | Sus familias se revocan al desactivarlo; el access token que tuviera en memoria caduca en 5 minutos como mucho (ADR-008) |
| `OWNER` resetea la contraseña de un admin que nunca llegó a entrar | Válido; se sobrescribe la provisional anterior y `password_change_required` sigue `true` |
| `OWNER` resetea el TOTP de un admin que aún no lo había enrolado | Válido y sin efecto observable: ya estaba en el estado inicial |
| Arranque de la aplicación con un `OWNER` activo ya existente | La tarea de la regla 3.9 no hace nada; las variables de entorno se ignoran |
| Arranque sin `OWNER` activo y sin variables de entorno | La aplicación arranca y lo registra como error: sin `OWNER` el panel es inaccesible, pero la tienda pública sigue funcionando y tumbar el arranque agravaría el problema |

---

## 11. Alcance ajeno

- **Login, TOTP, sesiones y su revocación** — [`auth.md`](auth.md); este documento escribe las
  columnas que aquel lee.
- **Qué puede hacer un administrador ya dentro** — cada documento de módulo: catálogo
  ([`product.md`](product.md)), pedidos ([`order.md`](order.md)), stock
  ([`inventory.md`](inventory.md)), compras ([`purchasing.md`](purchasing.md)), clientes
  ([`customer.md`](customer.md)).
- **Formato y contenido de `audit_log`** —
  [ADR-010](../architecture/ADR/ADR-010-admin-audit-log.md).
- **Invitación y reseteo por email** — descartados por ahora (reglas 3.2 y 3.4); volverían a estar
  sobre la mesa si `notification.md` justificara generalizar `verification_tokens` a los dos sujetos.
