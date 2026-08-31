# Auth

Inicio de sesión, renovación y cierre, para los dos sujetos que tiene el sistema: cliente
(`customers`) y administrador (`admin_users`). Implementa el mecanismo de tokens que
[ADR-008](../architecture/ADR/ADR-008-refresh-token-rotation.md) definió, y es el punto donde se
verifica el segundo factor del panel.

No incluye el alta de la cuenta ni la recuperación de contraseña —eso es
[`customer.md`](customer.md)— ni la gestión de administradores como entidad (alta, baja, cambio de
rol, reseteo de TOTP), que vive en `admin.md`. Reglas transversales en
[`00-security-validation-integrity.md`](00-security-validation-integrity.md).

---

## 1. Resumen

Un login produce dos tokens con vidas y transportes distintos:

| Token | Vida | Dónde vive | Se persiste |
|---|---|---|---|
| Access (JWT) | 15 min cliente, 5 min admin | Memoria del frontend, viaja en `Authorization: Bearer` | No |
| Refresh | 30 días cliente, 12 h admin (tope de familia) | Cookie `httpOnly`, el frontend nunca lo ve | Sí, `refresh_tokens.token_hash` |

El access token es stateless y no revocable: por eso dura minutos. El refresh token es el que
sostiene la sesión de verdad, y es el único revocable — cerrar sesión, detectar un reuso o cambiar la
contraseña actúan sobre él, nunca sobre el access token, que simplemente caduca solo.

El login de administrador tiene un paso más que el de cliente: TOTP obligatorio (regla 3.4).

---

## 2. Tablas implicadas

`refresh_tokens`, `customers` (lectura de `password_hash`, `status`, `email_verified_at`),
`admin_users` (lectura de `password_hash`, `active`, `totp_*`; escritura de `totp_*` solo durante el
enrolamiento). Esquema en [`../database/README.md`](../database/README.md).

| Columna de `refresh_tokens` | Restricción |
|---|---|
| `token_hash` | `UNIQUE`; SHA-256, nunca en claro ([ADR-005](../architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md)) |
| `customer_id` / `admin_user_id` | Exactamente uno de los dos (`chk_refresh_tokens_subject`) |
| `family_id` | `V2`; agrupa la cadena de rotación de un mismo login |
| `expires_at` | Vencimiento de la familia, copiado en cada rotación, nunca extendido |
| `revoked_at` | `NULL` mientras el token es usable |

| Columna de `admin_users` | Restricción |
|---|---|
| `totp_secret_encrypted` | AES-256-GCM; se escribe al empezar el enrolamiento (regla 3.4) |
| `totp_enabled` | `false` solo en la ventana entre crear el admin y completar su enrolamiento |
| `totp_last_used_step` | `V11`; último paso TOTP consumido, impide reutilizar un código |

---

## 3. Reglas de negocio

### 3.1 Transporte de los tokens

El **refresh token** viaja en una cookie con `HttpOnly`, `Secure`, `SameSite=Strict` y
`Path=/api/v1/auth`. Las tres propiedades importan por motivos distintos:

- `HttpOnly` — un XSS en la tienda no puede leer la cookie, que es el peor caso de guardar el refresh
  token en `localStorage`.
- `SameSite=Strict` — el navegador no envía la cookie en ninguna petición originada en otro sitio,
  que es exactamente el vector CSRF. Es la protección CSRF de este endpoint; no se añade además un
  token anti-CSRF, que sería una segunda barrera contra el mismo ataque ya bloqueado.
- `Path=/api/v1/auth` — la cookie solo se envía a los endpoints que la necesitan (`refresh`,
  `logout`), no en cada llamada a la API.

El **access token** viaja en el cuerpo de la respuesta de login y de refresco, y el frontend lo
mantiene solo en memoria (nunca `localStorage`, nunca cookie), enviándolo en `Authorization: Bearer`.
Al recargar la página se pierde, y se recupera llamando a `refresh` — que es precisamente para lo que
sirve la cookie.

Claims del JWT: sujeto, tipo de sujeto, rol si es administrador, emisión y expiración. **Nunca PII**
(00-security, regla 1): un JWT va en cabeceras y acaba en logs de proxy.

### 3.2 Login de cliente

`POST /auth/login`. Se rechaza, con respuesta y tiempo uniformes (00-security, regla 7), si:

- El email no existe, o la contraseña no coincide (Argon2id).
- El cliente está `ARCHIVED` — no tiene `password_hash` ni `email_hash`
  (`chk_customers_archived_no_pii`), así que la búsqueda ni siquiera lo encuentra.
- El cliente es `GUEST` — no tiene contraseña, no es una cuenta ([`customer.md`](customer.md), regla
  3.2).

La única excepción a la respuesta uniforme es `email_verified_at IS NULL`: 403 `EMAIL_NOT_VERIFIED`,
explícito ([`customer.md`](customer.md), regla 3.1). Revela que la cuenta existe, sí — pero un
usuario que acaba de registrarse y no entiende por qué no puede entrar es un problema real, y quien
llega hasta aquí ya ha acertado la contraseña.

**Fusión del carrito.** Un login correcto invoca `MergeCartUseCase`
([`cart.md`](cart.md), regla 3.2) con la cookie de carrito de invitado, si la hay. Va en la misma
transacción que la emisión de tokens: si la fusión falla, el login falla — no se deja al cliente
dentro con el carrito a medio fusionar.

### 3.3 Login de administrador: dos pasos

El panel siempre exige segundo factor, así que el login se parte en dos peticiones:

```
POST /auth/admin/login          email + contraseña
    correcto  ->  mfaToken (JWT efímero, 5 min, claim "mfa_pending")
                  + indicación de si toca verificar o enrolar

POST /auth/admin/mfa            mfaToken + código de 6 dígitos
    correcto  ->  access token + cookie de refresh
```

El `mfaToken` **no da acceso a nada**: su único claim útil es a qué administrador pertenece y que
está a medio autenticar. Cualquier endpoint del panel lo rechaza igual que a un anónimo.

Se rechaza el primer paso, con respuesta uniforme, si el email no existe, la contraseña no coincide o
`active = false`.

### 3.4 TOTP: obligatorio, enrolado en el primer acceso

Un administrador recién creado (`admin.md`) tiene `totp_enabled = false`. Esa es la **única** ventana
en la que un admin existe sin segundo factor, y no da acceso al panel:

1. `POST /auth/admin/login` con su contraseña devuelve `mfaToken` y `enrollmentRequired: true`.
2. `POST /auth/admin/totp/enrollment` (con el `mfaToken`) genera el secreto, lo guarda cifrado en
   `totp_secret_encrypted` **con `totp_enabled` todavía a `false`**, y devuelve el `otpauth://` URI
   para el código QR. El secreto en claro se devuelve una sola vez, aquí, y nunca más.
3. `POST /auth/admin/mfa` con un código válido confirma que la app quedó bien configurada, pone
   `totp_enabled = true` y ya sí emite los tokens.

Guardar el secreto en el paso 2 con `totp_enabled = false` evita una tabla temporal: el propio par de
columnas expresa "secreto generado, aún sin confirmar". Repetir el paso 2 antes de confirmar
sobrescribe el secreto — es lo que necesita quien escaneó el QR en un móvil que ya no tiene.

**Un código no se acepta dos veces.** Cada verificación calcula el paso actual
(`unix_time / 30`) y lo rechaza si es menor o igual que `totp_last_used_step` (`V11`); si es válido,
lo guarda. Sin esto, un código interceptado sería reutilizable durante el resto de su ventana, que es
justo lo que RFC 6238 exige impedir. Se acepta una tolerancia de ±1 paso por desfase de reloj, y el
guardado del paso hace que esa tolerancia no abra la puerta a repetir el mismo código.

### 3.5 Renovación

`POST /auth/refresh`. Lee la cookie, calcula su SHA-256, busca la fila:

| Estado de la fila | Respuesta |
|---|---|
| No existe | 401 `INVALID_REFRESH_TOKEN` |
| `expires_at` pasado | 401 `TOKEN_EXPIRED` — login normal |
| `revoked_at` no nulo | 401 `SESSION_REVOKED` + **se revoca toda la familia** (regla 3.6) |
| Válida | 200: par nuevo, y la fila presentada queda `revoked_at = now()` |

La rotación copia el `expires_at` de la familia en la fila nueva, **nunca lo extiende**
([ADR-008](../architecture/ADR/ADR-008-refresh-token-rotation.md)). Es un invariante de aplicación:
ningún `CHECK` puede compararlo con las filas hermanas, así que lo sostiene este caso de uso, que es
el único punto de escritura — mismo criterio que `products.stock` en
[`inventory.md`](inventory.md).

Todo ocurre en una transacción: revocar la presentada e insertar la nueva van juntas, o dos
peticiones simultáneas podrían dejar dos tokens vivos de la misma familia.

### 3.6 Reuso: se cae toda la familia

Presentar un refresh token ya revocado tiene dos explicaciones —el cliente legítimo reintentó tras
perder la respuesta, o alguien está replicando un token robado— y el sistema **no puede
distinguirlas**. Trata las dos como la mala: revoca todas las filas con ese `family_id` y obliga a
reautenticarse.

Por eso `SESSION_REVOKED` es un código distinto de `TOKEN_EXPIRED`: el frontend debe reaccionar
distinto. Ante `TOKEN_EXPIRED`, login normal. Ante `SESSION_REVOKED`, logout completo y **aviso
visible** al usuario, porque puede que le hayan robado la sesión.

### 3.7 Cierre de sesión

| Endpoint | Efecto |
|---|---|
| `POST /auth/logout` | Revoca la familia de **este** dispositivo. Borra la cookie |
| `POST /auth/logout-all` | Revoca **todas** las familias del sujeto, este dispositivo incluido |

`logout` es idempotente: sobre una familia ya revocada responde 204 igual, sin error. Cerrar una
sesión que ya estaba cerrada es exactamente lo que el cliente quería.

`logout-all` existe para quien sospecha que le han robado la cuenta y quiere echar a todo el mundo sin
cambiar la contraseña.

### 3.8 Qué más revoca sesiones

No todo lo que revoca una sesión es un logout. Estas acciones, definidas en otros documentos, revocan
**todas** las familias del sujeto a través de `RevokeTokenFamilyPort`:

| Acción | Documento | Por qué |
|---|---|---|
| Cambio de contraseña | [`customer.md`](customer.md), regla 3.5 | Si el cambio es por sospecha de robo, dejar viva la sesión del ladrón lo vacía de sentido |
| Reseteo de contraseña | [`customer.md`](customer.md), regla 3.5 | Igual, y con más motivo: se llega ahí sin saber la contraseña anterior |
| Baja de cliente | [`customer.md`](customer.md), regla 3.6 | El `CASCADE` de `customers` ya se lleva las filas |
| Desactivar un administrador | `admin.md` | `active = false` debe echarle del panel ya, no cuando caduque su token |

El cambio de email **no** revoca: el sujeto sigue siendo el mismo y ya está autenticado
([`customer.md`](customer.md), regla 3.3).

---

## 4. Endpoints

Prefijo `/api/v1`. Todos públicos: son la puerta de entrada, no puede exigirse sesión para
autenticarse. `logout` y `logout-all` solo hacen algo con una cookie o sesión válida.

| Método | Ruta | Devuelve |
|---|---|---|
| `POST` | `/auth/login` | 200 — cliente; access token + cookie |
| `POST` | `/auth/admin/login` | 200 — paso 1; `mfaToken` |
| `POST` | `/auth/admin/totp/enrollment` | 200 — `otpauth://` URI, solo si `totp_enabled = false` |
| `POST` | `/auth/admin/mfa` | 200 — paso 2; access token + cookie |
| `POST` | `/auth/refresh` | 200 — par nuevo |
| `POST` | `/auth/logout` | 204 |
| `POST` | `/auth/logout-all` | 204 |

Límite de peticiones obligatorio (00-security, regla 7) en `login`, `admin/login` y `admin/mfa`.
`refresh` también lo lleva: sin él, un refresh token robado permite renovar en bucle sin coste.

---

## 5. Request DTOs

### `LoginRequest` / `AdminLoginRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `email` | String | `@NotBlank`, `@Email`; normalizado antes del HMAC (00-security, regla 4) |
| `password` | String | `@NotBlank` |

Sin `@Size` ni reglas de complejidad en la contraseña: aquí se **comprueba**, no se establece. Aplicar
la política de contraseñas en el login filtraría cuentas antiguas y, peor, revelaría la política a
quien solo está probando.

### `AdminMfaRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `mfaToken` | String | `@NotBlank` |
| `code` | String | `@NotBlank`, `@Pattern("\\d{6}")` |

`refresh`, `logout` y `logout-all` no llevan cuerpo: el refresh token va en la cookie.

---

## 6. Response DTOs

### `AuthResponse`

`accessToken`, `expiresIn` (segundos), `subjectType` (`CUSTOMER`/`ADMIN`), `role` (solo administrador).

Nunca el refresh token: va en la cookie, y devolverlo también en el cuerpo anularía el `HttpOnly` que
justifica todo el diseño de la regla 3.1.

### `AdminLoginResponse`

`mfaToken`, `expiresIn`, `enrollmentRequired` (booleano).

### `TotpEnrollmentResponse`

`otpauthUri` (para el QR), `secret` (texto, para entrada manual si la cámara falla).

Se devuelve una sola vez. Una segunda llamada genera un secreto nuevo; no hay forma de volver a leer
el anterior.

---

## 7. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `LoginCustomerUseCase` | `LoginCustomerService` | Sí — crea la familia, fusiona el carrito |
| `AdminLoginUseCase` | `AdminLoginService` | No — solo emite el `mfaToken` |
| `EnrollAdminTotpUseCase` | `EnrollAdminTotpService` | Sí — `totp_secret_encrypted` |
| `VerifyAdminMfaUseCase` | `VerifyAdminMfaService` | Sí — `totp_enabled`, `totp_last_used_step`, familia |
| `RefreshTokenUseCase` | `RefreshTokenService` | Sí — rota, o revoca la familia si detecta reuso |
| `LogoutUseCase` | `LogoutService` | Sí |
| `LogoutAllUseCase` | `LogoutAllService` | Sí |

`RefreshTokenService` es el único punto de escritura de la rotación, y de él depende el invariante de
`expires_at` de la regla 3.5. Cualquier otra vía que emitiera un refresh token —un script, una
utilidad de pruebas— lo rompería sin que nada lo detecte.

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `RefreshTokenReadPort` | `findByHash` |
| `RefreshTokenWritePort` | `save`, `revoke` |
| `RevokeTokenFamilyPort` | `revokeFamily(familyId)`, `revokeAllForSubject(subjectId)` |
| `PasswordHasherPort` | `matches`, `hash` — Argon2id |
| `TotpPort` | `generateSecret`, `verify(secret, code, lastUsedStep)` |
| `AccessTokenPort` | `issue`, `parse` |

`RevokeTokenFamilyPort` es el puerto que [ADR-008](../architecture/ADR/ADR-008-refresh-token-rotation.md)
anticipó, y lo consumen tanto este módulo (reuso, `logout-all`) como `customer.md` y `admin.md`
(regla 3.8) — es una capacidad entre módulos, igual que `RegisterStockMovementUseCase` en
[`inventory.md`](inventory.md).

`PasswordHasherPort` y `TotpPort` aíslan las dos dependencias criptográficas. El dominio no sabe si
detrás hay Spring Security, BouncyCastle o cualquier otra cosa.

Persistencia ([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): JPA. No hay listados
paginados en este módulo; el `UPDATE` masivo de `revokeFamily` es la única sentencia que se escribe a
mano.

---

## 9. Errores

Enum `AuthErrorCode` en `domain/exception/auth/`
([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 | Email o contraseña incorrectos, cuenta archivada, admin inactivo — la misma respuesta para todos |
| `EMAIL_NOT_VERIFIED` | 403 | `email_verified_at IS NULL` (regla 3.2) |
| `INVALID_REFRESH_TOKEN` | 401 | La cookie no corresponde a ninguna fila |
| `TOKEN_EXPIRED` | 401 | `expires_at` pasado; login normal |
| `SESSION_REVOKED` | 401 | Reuso detectado; familia revocada, logout con aviso (regla 3.6) |
| `INVALID_MFA_TOKEN` | 401 | `mfaToken` ausente, caducado o sin el claim `mfa_pending` |
| `INVALID_TOTP_CODE` | 401 | Código incorrecto, fuera de ventana, o ya usado (regla 3.4) |
| `TOTP_ENROLLMENT_REQUIRED` | 409 | `POST /auth/admin/mfa` sobre un admin que aún no generó su secreto |
| `TOTP_ALREADY_ENROLLED` | 409 | `POST /auth/admin/totp/enrollment` con `totp_enabled = true` |
| `AUTH_VALIDATION_FAILED` | 422 | Bean Validation; con `errors[]` |

`INVALID_CREDENTIALS` cubre a propósito cuatro causas distintas: distinguirlas convierte el endpoint
en un enumerador de cuentas (00-security, regla 7).

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| Login de un cliente `GUEST` (sin contraseña) | 401 `INVALID_CREDENTIALS`: no es una cuenta, no hay `password_hash` que comparar |
| Login de un cliente `ARCHIVED` | 401 `INVALID_CREDENTIALS`: sin `email_hash` no aparece ni en la búsqueda |
| Dos pestañas llaman a `refresh` a la vez con la misma cookie | Una rota; la otra encuentra la fila ya revocada y **cae toda la familia** (regla 3.6). Es el precio de no poder distinguir un reintento de un robo; el frontend debe serializar sus refrescos |
| `mfaToken` usado en un endpoint del panel | Rechazado como si fuera anónimo: no lleva el claim de sesión (regla 3.3) |
| Admin repite el enrolamiento antes de confirmarlo | Se sobrescribe el secreto; el QR anterior deja de servir (regla 3.4) |
| Admin intenta enrolar de nuevo ya con TOTP activo | 409 `TOTP_ALREADY_ENROLLED`; el reseteo lo hace el OWNER, en `admin.md` |
| Mismo código TOTP enviado dos veces seguidas | El segundo falla: `totp_last_used_step` ya lo consumió (regla 3.4) |
| Sesión de admin que llega a las 12 h en pleno uso | Deja de renovarse: `expires_at` es un tope absoluto, no una ventana deslizante (ADR-008) |
| Cliente cambia su contraseña desde el móvil | Todas sus familias caen, el ordenador incluido (regla 3.8) |
| Login con carrito de invitado cuando el cliente ya tenía uno | Se fusionan sumando cantidades ([`cart.md`](cart.md), regla 3.2), en la misma transacción del login |
| `logout` sin cookie | 204: no hay sesión que cerrar, y el resultado es el que se pedía |

---

## 11. Alcance ajeno

- **Registro, verificación de email, cambio y reseteo de contraseña, baja** —
  [`customer.md`](customer.md).
- **Alta, baja, cambio de rol y reseteo de TOTP de administradores** — `admin.md`, cuando se escriba.
- **Fusión de carritos** — [`cart.md`](cart.md), `MergeCartUseCase`; este documento solo la dispara.
- **Envío de correos** — `notification.md`, cuando se escriba. Este módulo no envía ninguno: no hay
  correo de "has iniciado sesión".
- **Valores concretos del límite de peticiones** —
  [`00-security-validation-integrity.md`](00-security-validation-integrity.md), sección 12.
- **Purga de `refresh_tokens` caducados** — [`scheduled-tasks.md`](scheduled-tasks.md); pendiente de
  frecuencia, igual que `idempotency_keys`.
