# Customer

Cuenta de cliente: alta, verificación de email, perfil, direcciones guardadas, contraseña y baja.
Cubre también el cliente `GUEST` que nace de un checkout web sin sesión — no tiene cuenta ni puede
autenticarse, pero es lo que permite, más adelante, relacionar ese pedido con la cuenta que esa misma
persona cree.

No incluye login ni sesión: eso vive en `auth.md`, que se escribe a continuación y consume las
columnas que este documento define (`password_hash`, `email_verified_at`,
`chk_customers_archived_no_pii`). Reglas transversales en
[`00-security-validation-integrity.md`](00-security-validation-integrity.md).

---

## 1. Resumen

`customers` es una sola tabla para dos tipos de fila con un ciclo de vida muy distinto:

- **`GUEST`**: sin contraseña, nace sola durante un checkout web sin sesión (regla 3.2). No es una
  cuenta — es un ancla por email para el día en que esa persona decida registrarse.
- **`REGISTERED`**: con contraseña, nace por registro explícito (regla 3.1) o por la conversión en
  sitio de un `GUEST` que se registra con el mismo email (regla 3.2).

Ambas comparten `status` (`ACTIVE`/`ARCHIVED`, [ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md))
y el mismo mecanismo de baja (regla 3.6).

---

## 2. Tablas implicadas

`customers`, `customer_addresses`, `verification_tokens`. Esquema en
[`../database/README.md`](../database/README.md).

| Columna de `customers` | Restricción |
|---|---|
| `type` | `GUEST`, `REGISTERED`; `GUEST` nunca tiene `password_hash` (`chk_customers_guest_no_password`) |
| `status` | `ACTIVE`, `ARCHIVED`; `ARCHIVED` exige `email_hash`/`password_hash` en `NULL` y `anonymized_at` relleno |
| `email_hash` | `UNIQUE`; `NULL` en `ARCHIVED`, así que un email puede reutilizarse tras una baja (regla 3.6) |
| `email_verified_at` | `NULL` hasta verificar; bloquea el login (`auth.md`) mientras lo esté (regla 3.1) |
| `version` | Bloqueo optimista ([ADR-009](../architecture/ADR/ADR-009-optimistic-locking.md)) |

`customer_addresses`: sin cambios de esquema; `is_default` con índice único parcial, igual patrón que
`customer_payment_methods` en [`payment.md`](payment.md).

---

## 3. Reglas de negocio

### 3.1 Registro y verificación de email

`POST /customers/register` crea una fila `REGISTERED`, `status = ACTIVE`, `email_verified_at = NULL`.
Envía un `verification_tokens` (`purpose = EMAIL_VERIFICATION`) por email.

**El login queda bloqueado hasta verificar** (`auth.md` responde 403 `EMAIL_NOT_VERIFIED` si
`email_verified_at IS NULL`) — decisión explícita: verificar es la puerta de entrada, no un paso
opcional posterior. `POST /customers/verify-email` consume el token y rellena
`email_verified_at`; `POST /customers/resend-verification` reenvía uno nuevo si el anterior caducó,
invalidando el anterior.

Si el email ya pertenece a un `REGISTERED`, 409 `EMAIL_ALREADY_REGISTERED`. Si pertenece a un `GUEST`,
no es un conflicto — es la regla 3.2.

### 3.2 Cliente `GUEST`: alta automática y conversión

Un checkout web sin sesión ([`order.md`](order.md), regla 3.11) siempre lleva `buyer.email`. Al
confirmar el pedido (Fase 3a), se busca `customers` por `email_hash`:

- **No existe**: se crea una fila `GUEST`, sin contraseña, con nombre/teléfono/email del `BuyerRequest`
  del pedido. `orders.customer_id` apunta a ella.
- **Existe un `GUEST`**: se reutiliza — `orders.customer_id` apunta a la misma fila, y su
  nombre/teléfono se actualizan con lo último recibido (un `GUEST` no tiene perfil propio que
  proteger, es simplemente el dato del pedido más reciente).
- **Existe un `REGISTERED`**: no aplica — un checkout sin sesión con el email de una cuenta ya
  registrada no vincula el pedido a esa cuenta; sigue como pedido de invitado sin `customer_id`, para
  no atribuir un pedido a una cuenta sin que su dueño haya iniciado sesión.

**Conversión a `REGISTERED`.** Si `POST /customers/register` encuentra un `GUEST` con ese
`email_hash`, no crea una fila nueva — la actualiza en el sitio: `type = REGISTERED`,
`password_hash` con la contraseña elegida, `email_verified_at = NULL` (sigue exigiendo verificación,
regla 3.1). El `id` no cambia, así que todos los pedidos de invitado que ya apuntaban a esa fila pasan
a ser, automáticamente, historial de la cuenta nueva. Es la razón de ser de `GUEST`: sin esta
conversión en el sitio, `uq_customers_email_hash` impediría registrarse con un email que ya generó un
pedido de invitado.

Esta funcionalidad es exclusiva del canal `WEB`. Una venta de mostrador (`POST /orders/counter`,
`STORE`/`INTERFLORA`, [`payment.md`](payment.md)) no exige ni nombre ni email del cliente — si el
administrador lo introduce igualmente, se guarda como snapshot en el propio pedido
(`orders.buyer_*`), sin crear ni buscar ninguna fila en `customers`. Un cliente de mostrador nunca
tiene fila propia.

### 3.3 Perfil

`GET/PUT /customers/me`: `firstName`, `lastName`, `phone`. Cambiar `email` es un flujo aparte
(`PUT /customers/me/email`): actualiza `email_encrypted`/`email_hash` de inmediato y pone
`email_verified_at = NULL` — un email no verificado no debe quedar marcado como verificado por
herencia del anterior. Consecuencia: la sesión sigue viva, pero un futuro login queda bloqueado
(regla 3.1) hasta reverificar la dirección nueva. Rechazado con 409 `EMAIL_ALREADY_REGISTERED` si el
nuevo email ya pertenece a otra cuenta.

Un `GUEST` no tiene perfil que editar — no puede autenticarse, así que estos endpoints no le son
accesibles.

### 3.4 Direcciones guardadas

CRUD sobre `customer_addresses`, solo `REGISTERED` con sesión. Máximo 20 direcciones por cliente —
límite defensivo (00-security, regla 4), no de negocio. La primera dirección creada se marca por
defecto sola; eliminar la default no promociona otra automáticamente (mismo criterio que las tarjetas
guardadas, [`payment.md`](payment.md), regla 3.5).

### 3.5 Contraseña

**Cambio autenticado** (`POST /customers/me/password`): exige `currentPassword` correcta.

**Reseteo sin sesión** (`POST /customers/password-reset/request` +
`POST /customers/password-reset/confirm`): `verification_tokens` con `purpose = PASSWORD_RESET`.
Respuesta uniforme exista o no la cuenta con ese email — mismo criterio que el login (00-security,
regla 7), para no convertir el endpoint en un enumerador de cuentas.

Ninguno de los dos aplica a `GUEST`: no tiene contraseña que cambiar.

### 3.6 Baja de cliente

`customers.status = ARCHIVED` ([ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md)):
`anonymized_at = now()`, `email_hash`/`password_hash`/`email_encrypted`/`phone_encrypted`/nombre a
`NULL`, y `ON DELETE CASCADE` se lleva por delante direcciones, tarjetas guardadas, carritos, tokens
de verificación y de refresco.

Dos vías, mismo efecto:

- **`DELETE /customers/me`** — el propio cliente, con sesión, sin necesitar el motivo.
- **`POST /admin/customers/{id}/archive`** — el administrador, motivo obligatorio (auditado,
  `changed_fields` únicamente — `customers` no está en la lista blanca de valores de
  [ADR-010](../architecture/ADR/ADR-010-admin-audit-log.md), igual que `payments` en
  [`payment.md`](payment.md)).

Aplicable también a un `GUEST`: aunque no tiene cuenta ni login, un administrador puede archivarlo a
petición (alguien que compró como invitado y pide que se olvide su email).

`orders.customer_id` no se toca — sigue apuntando a la fila, ahora anonimizada
([ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md)). El propio pedido
se purga más tarde, y con condiciones propias — [`scheduled-tasks.md`](scheduled-tasks.md).

Un email dado de baja queda libre: `email_hash = NULL` no choca con `uq_customers_email_hash`, así que
la misma persona puede volver a registrarse desde cero, sin heredar nada de la cuenta archivada.

### 3.7 Panel de administración

Búsqueda (email exacto, teléfono exacto, número de pedido) ya definida en 00-security, regla 3.
Además:

- **Ver detalle** (`GET /admin/customers/{id}`): perfil, direcciones, resumen de pedidos.
- **Editar** (`PUT /admin/customers/{id}`): solo `firstName`, `lastName`, `phone` — nunca `email`. El
  email es el identificador de login y de búsqueda por HMAC; cambiarlo desde el panel abriría una vía
  de apropiación de cuenta que no pasa por la verificación de la regla 3.3. Un cambio de email
  reportado por soporte lo hace el propio cliente por su cauce, o se gestiona como baja y alta nueva.
- **Archivar** (regla 3.6).

---

## 4. Endpoints

Prefijo `/api/v1`.

### Cuenta (público / `CLIENTE`)

| Método | Ruta | Rol | Devuelve |
|---|---|---|---|
| `POST` | `/customers/register` | Público | 201 |
| `POST` | `/customers/verify-email` | Público | 200 |
| `POST` | `/customers/resend-verification` | Público | 200 |
| `POST` | `/customers/password-reset/request` | Público | 200 — siempre, exista o no la cuenta |
| `POST` | `/customers/password-reset/confirm` | Público | 200 |
| `GET` | `/customers/me` | `CLIENTE` | 200 |
| `PUT` | `/customers/me` | `CLIENTE` | 200 |
| `PUT` | `/customers/me/email` | `CLIENTE` | 200 |
| `POST` | `/customers/me/password` | `CLIENTE` | 200 |
| `DELETE` | `/customers/me` | `CLIENTE` | 204 |
| `GET` | `/customers/me/addresses` | `CLIENTE` | 200 |
| `POST` | `/customers/me/addresses` | `CLIENTE` | 201 |
| `PUT` | `/customers/me/addresses/{id}` | `CLIENTE` | 200 |
| `PATCH` | `/customers/me/addresses/{id}/default` | `CLIENTE` | 200 |
| `DELETE` | `/customers/me/addresses/{id}` | `CLIENTE` | 204 |

### Administración (`ADMIN`)

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/admin/customers` | 200 — paginado, filtros de 00-security regla 3 |
| `GET` | `/admin/customers/{id}` | 200 |
| `PUT` | `/admin/customers/{id}` | 200 |
| `POST` | `/admin/customers/{id}/archive` | 200 |

---

## 5. Request DTOs

### `RegisterCustomerRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `firstName`, `lastName` | String | `@NotBlank`, `@Size(max = 150)` |
| `email` | String | `@NotBlank`, `@Email`, normalizado antes de cifrar/HMAC (00-security, regla 4) |
| `phone` | String | `@NotBlank`, formato nacional (validador propio, 00-security regla 4) |
| `password` | String | `@NotBlank`, longitud mínima y fuera de lista de contraseñas comunes (validador propio) |

### `VerifyEmailRequest` / `ResendVerificationRequest`

`token` (`VerifyEmailRequest`) o `email` (`ResendVerificationRequest`), `@NotBlank`.

### `UpdateProfileRequest`

`firstName`, `lastName`, `phone` — mismas reglas que en el registro.

### `ChangeEmailRequest`

`newEmail`: `@NotBlank`, `@Email`.

### `ChangePasswordRequest`

`currentPassword`, `newPassword`: `@NotBlank`; `newPassword` con las mismas reglas que en el registro.

### `RequestPasswordResetRequest` / `ConfirmPasswordResetRequest`

`email` (petición) o `token` + `newPassword` (confirmación).

### `AddressRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `alias` | String | `@NotBlank`, `@Size(max = 100)` |
| `recipientName` | String | `@NotBlank`, `@Size(max = 200)` |
| `street`, `number` | String | `@NotBlank` |
| `detail` | String | Opcional |
| `city` | String | `@NotBlank` |
| `postalCode` | String | `@NotBlank`, formato español |

### `AdminUpdateCustomerRequest`

`firstName`, `lastName`, `phone` — igual que `UpdateProfileRequest`, sin `email` (regla 3.7).

### `ArchiveCustomerRequest`

`reason`: `@NotBlank`, `@Size(max = 500)`.

---

## 6. Response DTOs

### `CustomerResponse`

`id`, `type`, `status`, `firstName`, `lastName`, `email`, `phone`, `emailVerified`, `createdAt`.

`email`/`phone` descifrados solo para el propio dueño o para `ADMIN`; nunca en ninguna otra vista
(00-security, regla 3).

### `AddressResponse`

`id`, `alias`, `recipientName`, `street`, `number`, `detail`, `city`, `postalCode`, `isDefault`.

### `AdminCustomerDetailResponse`

`CustomerResponse` + `addresses` + `orderCount` + `lastOrderAt`. Sin resumen de tarjetas guardadas: eso
es de [`payment.md`](payment.md) y no aporta nada a la gestión de cuenta.

---

## 7. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `RegisterCustomerUseCase` | `RegisterCustomerService` | Sí — crea o convierte (regla 3.2) |
| `VerifyEmailUseCase` | `VerifyEmailService` | Sí |
| `ResendVerificationUseCase` | `ResendVerificationService` | Sí |
| `UpdateProfileUseCase` | `UpdateProfileService` | Sí |
| `ChangeEmailUseCase` | `ChangeEmailService` | Sí |
| `ChangePasswordUseCase` | `ChangePasswordService` | Sí |
| `RequestPasswordResetUseCase` | `RequestPasswordResetService` | Sí |
| `ConfirmPasswordResetUseCase` | `ConfirmPasswordResetService` | Sí |
| `DeactivateCustomerUseCase` | `DeactivateCustomerService` | Sí — self-service y admin comparten servicio (regla 3.6) |
| `FindOrCreateGuestCustomerUseCase` | `FindOrCreateGuestCustomerService` | Sí — invocado por `order.md`, sin controlador propio |
| `ManageAddressUseCase` (create/update/delete/set-default) | `ManageAddressService` | Sí |
| `GetProfileUseCase` / `ListAddressesUseCase` | — | No |
| `GetCustomersUseCase` / `GetCustomerUseCase` (admin) | — | No |
| `UpdateCustomerUseCase` (admin) | `UpdateCustomerService` | Sí |

`RegisterCustomerService` y `FindOrCreateGuestCustomerService` comparten la búsqueda por
`email_hash`, pero no el resto: uno exige contraseña y dispara verificación, el otro ni una cosa ni la
otra — se mantienen como servicios separados en vez de una rama condicional dentro del mismo, para no
mezclar dos casos de uso con quién los invoca y qué garantiza cada uno tan distintos.

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `CustomerReadPort` | `findById`, `findByEmailHash`, `findAllForAdmin` |
| `CustomerWritePort` | `save` |
| `AddressReadPort` | `findById`, `findAllByCustomer` |
| `AddressWritePort` | `save`, `delete` |
| `VerificationTokenPort` | `create`, `findValidByHash`, `markUsed` |
| `MailPort` | `sendVerificationEmail`, `sendPasswordResetEmail` — implementado fuera de este módulo, ver `notification.md` |

`FindOrCreateGuestCustomerUseCase` es la capacidad que `order.md` consume directamente (regla 3.2),
igual que `order.md` consume `RegisterStockMovementUseCase` de `inventory.md` — no hay un puerto de
infraestructura para ello, es una capacidad de aplicación entre módulos.

Persistencia ([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): JPA para todo lo de este
documento — no hay listado paginado propio salvo el de administración, que sí usa JDBC.

---

## 9. Errores

Enum `CustomerErrorCode` en `domain/exception/customer/`
([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `CUSTOMER_NOT_FOUND` | 404 | No existe, o no pertenece a quien pregunta |
| `EMAIL_ALREADY_REGISTERED` | 409 | Registro o cambio de email con un email ya `REGISTERED` |
| `VERIFICATION_TOKEN_INVALID` | 422 | Token inexistente o ya usado (`EMAIL_VERIFICATION` o `PASSWORD_RESET`) |
| `VERIFICATION_TOKEN_EXPIRED` | 410 | `expires_at` pasado |
| `INVALID_CURRENT_PASSWORD` | 401 | `ChangePasswordRequest.currentPassword` no coincide |
| `ADDRESS_NOT_FOUND` | 404 | No existe, o no pertenece a quien pregunta |
| `ADDRESS_LIMIT_REACHED` | 422 | 21ª dirección de un mismo cliente |
| `CUSTOMER_ALREADY_ARCHIVED` | 409 | Baja o edición sobre un `status = ARCHIVED` |
| `CUSTOMER_VALIDATION_FAILED` | 422 | Bean Validation; con `errors[]` |

`EMAIL_NOT_VERIFIED` (403, login bloqueado por email sin verificar) vive en `AuthErrorCode`
(`auth.md`): es un error del flujo de login, no de este módulo, aunque la condición
(`email_verified_at IS NULL`) la fije aquí.

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| Registrarse con el email de un `GUEST` ya existente | Se convierte en el sitio (regla 3.2), no se crea fila nueva ni es 409 |
| Registrarse con el email de un `REGISTERED` ya existente | 409 `EMAIL_ALREADY_REGISTERED` |
| Dos checkouts de invitado seguidos con el mismo email, datos distintos | El segundo actualiza nombre/teléfono del `GUEST` con lo más reciente (regla 3.2) |
| Checkout de invitado con el email de una cuenta `REGISTERED` ya existente | No se vincula; sigue como pedido sin `customer_id`, sin iniciar sesión en nombre de nadie |
| Cambiar el email y no verificar el nuevo | La sesión activa sigue funcionando; el próximo login se bloquea hasta verificar (regla 3.3) |
| Reseteo de contraseña solicitado para un email que no existe | 200 igual que si existiera; no se envía ningún correo (regla 3.5) |
| Token de verificación reutilizado tras consumirse | `VERIFICATION_TOKEN_INVALID`: `used_at` ya tiene valor |
| Cliente se da de baja con direcciones y tarjetas guardadas | Todo desaparece por `CASCADE` (regla 3.6); los pedidos ya hechos no se tocan |
| Admin intenta cambiar el email de un cliente desde `PUT /admin/customers/{id}` | Campo ignorado o rechazado: el DTO de administración no lo admite (regla 3.7) |
| Volver a registrarse con el email de una cuenta archivada | Cuenta nueva desde cero; ningún dato de la archivada se hereda (regla 3.6) |

---

## 11. Alcance ajeno

- **Login, sesión, refresh token** — `auth.md`, cuando se escriba; consume `password_hash` y
  `email_verified_at` que este documento define.
- **Fusión de carritos al iniciar sesión** — [`cart.md`](cart.md), `MergeCartUseCase`, invocado por
  `auth.md` en el login, no aquí.
- **Tarjetas guardadas** — [`payment.md`](payment.md).
- **Purga de PII de pedidos y su condición sobre el `status` del cliente** —
  [`scheduled-tasks.md`](scheduled-tasks.md).
- **Envío real de los correos de verificación y reseteo** — `notification.md`, cuando se escriba; este
  documento solo define cuándo se dispara cada uno.
