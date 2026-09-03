# Plan de implementación — `feature/auth` (alcance administrador)

Destinatario: IMPLEMENTER. Revisor posterior: REVIEWER (y luego SECURITY-REVIEWER).
Documento fuente: `docs/features/auth.md`. Transversal: `docs/features/00-security-validation-integrity.md`.
ADRs que gobiernan esta rama: **ADR-008** (rotación de refresh token), **ADR-005** (PII, hashes),
**ADR-010** (auditoría `LOGIN`/`LOGIN_FAILED`), **ADR-012** (contrato de error), **ADR-002**
(JPA/JDBC), **ADR-003** (puertos por capacidad), **ADR-016** (rate limiting, creado para esta rama).

Rama: `feature/auth`, creada desde `main` actualizado (PR #12 ya mergeada).

---

## 0. Por qué este módulo es el siguiente

No es una preferencia, es un bloqueo real:

- `AdminController` ya resuelve el actor desde `Authentication#getName()` y el actor de `audit_log`
  depende de ello. **Sin filtro JWT, todos los endpoints de `/admin` fallan con una petición
  anónima.** El módulo admin está codificado pero no es utilizable.
- `SecurityConfig` es un placeholder `permitAll()` que loguea `WARN` al arrancar: catálogo,
  productos, descuentos, inventario y admin están abiertos. Es el hueco documentado en
  `dev-plan.md` para `category`, `product` y `admin`.
- `admin.md` (tablas `admin_users`, `refresh_tokens`, `PasswordHasherPort`,
  `RevokeTokenFamilyPort`, `PiiCryptoPort`) ya dejó puesta la mitad de las dependencias de `auth`.

## 1. Alcance — decisiones tomadas antes de codificar

| Decisión | Valor | Motivo |
|---|---|---|
| Sujeto | **Solo administrador** | `LoginCustomerUseCase` se difiere a `feature/customer`: no existe registro de cliente ni `MergeCartUseCase` (auth.md 3.2 los exige), así que hoy sería código muerto e intesteable |
| Access token | `spring-boot-starter-oauth2-resource-server` (Nimbus) | Aporta `JwtEncoder`/`JwtDecoder` y `BearerTokenAuthenticationFilter`; no se escribe filtro de parseo propio |
| Firma | **HS256**, secreto en `app.security.jwt.secret` | Un solo servicio verifica sus propios tokens; RS256 solo aporta si un tercero verifica sin el secreto. Comentario en el adaptador con el techo y la vía de subida a RS256 |
| TOTP | A mano con `javax.crypto.Mac` (HmacSHA1, RFC 6238) | ~30 líneas de JDK; ninguna dependencia nueva. BouncyCastle ya está para lo demás |
| Rate limiting | **Dentro de esta rama**, según ADR-016 | Los números y el tratamiento de `CF-Connecting-IP` los fija ADR-016, ya escrito |
| Migraciones | **Ninguna** | `refresh_tokens` (V1) + `family_id` (V2) + `totp_last_used_step` (V11) + `password_change_required` (V12) cubren todo |

**Fuera de alcance (déjalo documentado, no lo implementes):** login de cliente, fusión de carrito,
`/customers/*` (sus límites ya están declarados en ADR-016 pero no hay endpoints donde aplicarlos),
purga programada de `refresh_tokens` caducados (`scheduled-tasks.md`).

## 2. Conflicto documental a resolver antes de tocar `SecurityConfig`

`auth.md` regla 3.1 dice que `SameSite=Strict` **es** la protección CSRF del endpoint de refresco y
que «no se añade además un token anti-CSRF». `CLAUDE.md` exige CSRF global activo con
`CookieCsrfTokenRepository.withHttpOnlyFalse()`, que sí exige `X-XSRF-TOKEN` en todo `POST`,
`/auth/refresh` incluido.

**Resolución (aplícala, no la reabras):** el CSRF global se mantiene. `auth.md` 3.1 explica por qué
la cookie no necesita *por sí misma* una segunda barrera, no prohíbe la que el proyecto ya aplica a
todo. En la fase de documentación añades una frase a `auth.md` 3.1 dejándolo escrito: el filtro CSRF
global también cubre `/auth/refresh`, `/auth/logout` y `/auth/logout-all`, y el SPA debe enviar la
cabecera también ahí. Si al implementarlo aparece un motivo técnico para excluir esas rutas,
**para y explícalo** antes de escribir la excepción.

---

## 3. Fases, en el orden de `CLAUDE.md`

### Fase 1 — Dependencias

`pom.xml`: `spring-boot-starter-oauth2-resource-server` y `com.bucket4j:bucket4j_jdk17-core`
(artefacto para JDK 17+; verifica el nombre exacto de la última versión estable antes de fijarla).
Nada más. Versiones gestionadas por el parent cuando exista `dependencyManagement`; si no, fija la
versión explícita como se hizo con `versions-maven-plugin`.

### Fase 2 — REST API

`infrastructure/web/controller/auth/AuthController.java`, prefijo `/api/v1/auth`, todos públicos:

| Método | Ruta | Devuelve |
|---|---|---|
| `POST` | `/auth/admin/login` | 200 `AdminLoginResponse` |
| `POST` | `/auth/admin/totp/enrollment` | 200 `TotpEnrollmentResponse` |
| `POST` | `/auth/admin/mfa` | 200 `AuthResponse` + `Set-Cookie` de refresco |
| `POST` | `/auth/refresh` | 200 `AuthResponse` + cookie rotada |
| `POST` | `/auth/logout` | 204 + cookie borrada |
| `POST` | `/auth/logout-all` | 204 + cookie borrada |

La cookie se construye en el controlador (es transporte HTTP, no lógica): `ResponseCookie`
`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`, `Max-Age` = vida de la familia; el
borrado es la misma cookie con `Max-Age=0`. El servicio devuelve el token en claro en su DTO de
salida y **nunca** lo escribe en un log.

`logout` y `logout-all` leen la cookie vía `@CookieValue(required = false)`; ausente ⇒ 204 sin
tocar nada (auth.md, caso borde).

Logging de controlador: entrada/salida a `DEBUG` con el `adminId` (UUID) cuando se conoce. **Nunca**
email, `mfaToken`, código TOTP ni refresh token — ADR-005 y `CLAUDE.md`.

### Fase 3 — Request DTOs

`infrastructure/web/request/auth/`: `AdminLoginRequest` (`email` `@NotBlank` `@Email`, `password`
`@NotBlank`), `AdminMfaRequest` (`mfaToken` `@NotBlank`, `code` `@NotBlank` `@Pattern("\\d{6}")`),
`TotpEnrollmentRequest` (`mfaToken` `@NotBlank`).

Sin `@Size` ni política de complejidad en `password`: aquí se comprueba, no se establece (auth.md 5).

### Fase 4 — Response DTOs

`infrastructure/web/response/auth/`: `AuthResponse` (`accessToken`, `expiresIn`, `subjectType`,
`role`, `passwordChangeRequired`), `AdminLoginResponse` (`mfaToken`, `expiresIn`,
`enrollmentRequired`), `TotpEnrollmentResponse` (`otpauthUri`, `secret`).

`AuthResponse` **nunca** incluye el refresh token.

### Fase 5 — Commands

`application/auth/command/`: `AdminLoginCommand`, `EnrollAdminTotpCommand`, `VerifyAdminMfaCommand`,
`RefreshTokenCommand`, `LogoutCommand`, `LogoutAllCommand`. `subjectType` se deduce del token, no
llega del cliente.

### Fase 6 — Queries

Ninguna: este módulo no tiene lecturas paginadas ni proyecciones. No crees el paquete vacío.

### Fase 7 — Input ports

`application/auth/port/in/`: `AdminLoginUseCase`, `EnrollAdminTotpUseCase`, `VerifyAdminMfaUseCase`,
`RefreshTokenUseCase`, `LogoutUseCase`, `LogoutAllUseCase`. Un método `execute` por caso de uso
(ADR-001).

### Fase 8 — Services

`application/auth/service/`, uno por caso de uso.

**`AdminLoginService`** — sin escritura salvo auditoría:
1. Normaliza el email, calcula `PiiCryptoPort#hmac`, `AdminReadPort#findByEmailHash`.
2. Rechaza con `INVALID_CREDENTIALS` si no existe, si `active = false` o si la contraseña no casa.
3. **Tiempo uniforme (00-security regla 7):** cuando el email no existe, ejecuta igualmente una
   verificación Argon2id contra un hash señuelo constante antes de responder. Sin esto, el tiempo de
   respuesta distingue "email desconocido" de "contraseña incorrecta" y el endpoint vuelve a ser un
   enumerador de cuentas. Es el punto que un revisor de seguridad va a mirar primero.
4. Registra `LOGIN_FAILED` en `AuditLogPort` (`entityType = "admin_user"`, `entityId` = id del admin
   o `null` si el email no existe — la columna `admin_user_id` es nullable, V4).
5. Éxito ⇒ `AccessTokenPort#issue` de un JWT efímero de 5 min con `typ = "mfa"` y el id del admin
   como `sub`; `enrollmentRequired = !totpEnabled`.

**`EnrollAdminTotpService`** — valida el `mfaToken` (`typ = "mfa"`, no caducado), 409
`TOTP_ALREADY_ENROLLED` si `totpEnabled`, `TotpPort#generateSecret`, cifra con `PiiCryptoPort` en
`totp_secret_encrypted`, deja `totpEnabled = false`, devuelve secreto en claro + `otpauth://` URI
**una sola vez**. Repetir antes de confirmar sobrescribe el secreto (auth.md 3.4).

**`VerifyAdminMfaService`** — transaccional:
1. Valida el `mfaToken`; 401 `INVALID_MFA_TOKEN` si falta, caduca o no lleva `typ = "mfa"`.
2. 409 `TOTP_ENROLLMENT_REQUIRED` si no hay secreto guardado.
3. `TotpPort#verify(secret, code, totpLastUsedStep)`: ventana ±1 paso, y el paso resultante debe ser
   **estrictamente mayor** que `totpLastUsedStep`; si no, 401 `INVALID_TOTP_CODE` (auth.md 3.4, V11).
4. Éxito ⇒ `totpEnabled = true`, guarda `totpLastUsedStep`, crea la familia de refresco
   (`family_id` nuevo, `expires_at = now + 12h`), emite access token (5 min, `typ = "access"`,
   claims `sub`, `subject_type`, `role`, y `pwd_change_required` solo si es `true`).
5. Registra `LOGIN` en `AuditLogPort`. Fallo de código ⇒ `LOGIN_FAILED`.

**`RefreshTokenService`** — único punto de escritura de la rotación (auth.md 7), transaccional:
tabla de estados de auth.md 3.5 tal cual; en reuso, revoca **toda** la familia y responde
`SESSION_REVOKED`; en rotación, **copia** el `expires_at` de la fila presentada, nunca lo extiende
(ADR-008; es el invariante que ningún `CHECK` puede sostener). Revocar la presentada e insertar la
nueva van en la misma transacción.

**`LogoutService`** (revoca la familia de esa cookie, idempotente, 204 aunque ya estuviera revocada)
y **`LogoutAllService`** (`RevokeTokenFamilyPort#revokeAllForSubject`).

Logging en todos: entrada/salida a `DEBUG` con UUIDs; ningún valor de token, código o email.

### Fase 9 — Output ports

`application/auth/port/out/`:

| Port | Estado | Capacidad |
|---|---|---|
| `PasswordHasherPort` | **ya existe** | `hash`, `matches` |
| `RevokeTokenFamilyPort` | **existe, se amplía** | añade `revokeFamily(UUID familyId)` junto al `revokeAllForSubject` actual |
| `RefreshTokenReadPort` | nuevo | `findByHash(byte[] tokenHash)` |
| `RefreshTokenWritePort` | nuevo | `save(RefreshToken)`, `revoke(RefreshTokenId, Instant)` |
| `TotpPort` | nuevo | `generateSecret()`, `verify(secret, code, lastUsedStep)`, `otpauthUri(...)` |
| `AccessTokenPort` | nuevo | `issue(...)`, `parse(String)` |

No crees un repositorio genérico (ADR-003). `AdminReadPort`/`AdminWritePort` se reutilizan tal
cual: **no dupliques lectura de `admin_users` en este módulo.**

### Fase 10 — Domain

`domain/model/auth/`: `RefreshToken` (id, `tokenHash`, `subjectId`, `subjectType`, `familyId`,
`expiresAt`, `revokedAt`) con los métodos de estado (`isExpired`, `isRevoked`, `rotate` que produce
la fila hija copiando `expiresAt`), `SubjectType` (`CUSTOMER`, `ADMIN` — `CUSTOMER` existe en el
enum porque la columna lo exige, aunque no haya login de cliente todavía),
`valueobject/RefreshTokenId`.

`domain/exception/auth/`: `AuthErrorCode` con los 11 códigos de auth.md 9, y una excepción por
familia HTTP siguiendo el patrón de `admin`.

**Falta una base de excepción 403:** hoy existen `NotFoundException`, `ConflictException`,
`UnauthorizedException`, `UnprocessableException`, pero ninguna 403. Crea
`domain/exception/ForbiddenException.java` con el mismo estilo y añade su `@ExceptionHandler` en
`GlobalExceptionHandler`. La necesitan `EMAIL_NOT_VERIFIED` (futuro) y `PASSWORD_CHANGE_REQUIRED`.

El dominio no importa Spring, JPA ni HTTP (ArchUnit lo comprueba).

### Fase 11 — Persistencia (JPA, ADR-002)

`infrastructure/persistence/entity/auth/RefreshTokenEntity`, `jpa/auth/RefreshTokenJpaRepository`,
`mapper/auth/RefreshTokenPersistenceMapper`, `adapter/auth/RefreshTokenPersistenceAdapter`.

`RevokeTokenFamilyPersistenceAdapter` **ya existe** (JDBC): amplíalo con `revokeFamily`, no crees un
segundo adaptador. El `UPDATE` masivo es la única sentencia escrita a mano de este módulo.

`token_hash` es SHA-256 del token en claro (`MessageDigest`, no Argon2: es un valor de alta entropía
generado por el servidor, no una contraseña). El token en claro se genera con `SecureRandom`,
mínimo 32 bytes, codificado en Base64 URL-safe.

### Fase 12 — Adaptadores de seguridad

`infrastructure/security/jwt/JwtAccessTokenAdapter` (implementa `AccessTokenPort`): `NimbusJwtEncoder`
con `ImmutableSecret` y `NimbusJwtDecoder.withSecretKey`, HS256. Claims: `sub` (UUID del admin, **es
lo que `AdminController` lee de `Authentication#getName()`**), `typ` (`access`|`mfa`),
`subject_type`, `role`, `pwd_change_required`, `iat`, `exp`. Nunca PII.

`infrastructure/security/totp/TotpAdapter` (implementa `TotpPort`): HMAC-SHA1 sobre el contador
`unix_time / 30` con `javax.crypto.Mac`, truncamiento dinámico de RFC 4226, 6 dígitos, secreto de
20 bytes de `SecureRandom` en Base32, URI `otpauth://totp/Rosy%20Floristas:{email}?secret=...&issuer=...`.
Comparación del código en **tiempo constante** (`MessageDigest.isEqual` sobre los bytes).

`infrastructure/security/config/SecurityConfig` reescrito:
- Mantiene CSRF y `SessionCreationPolicy.STATELESS` tal cual están (ver sección 2).
- `oauth2ResourceServer(oauth2 -> oauth2.jwt(...))` con un `JwtAuthenticationConverter` que mapea
  `role = OWNER` a **`ROLE_OWNER` + `ROLE_ADMIN`** y `role = ADMIN` a `ROLE_ADMIN`. `OWNER` es
  `ADMIN` en todo salvo gestionar administradores (admin.md 1); concederle las dos autoridades evita
  que cada `@PreAuthorize` tenga que enumerarlas.
- **Validador que rechaza cualquier token sin `typ = "access"`.** Sin él, el `mfaToken` sirve como
  sesión y el segundo factor se puede saltar entero: es el fallo más grave que puede tener esta
  rama. Un `OAuth2TokenValidator<Jwt>` propio dentro del `DelegatingOAuth2TokenValidator` del decoder.
- `authenticationEntryPoint` y `accessDeniedHandler` que delegan en el
  `@Qualifier("handlerExceptionResolver") HandlerExceptionResolver`, para que un 401/403 de la capa
  de seguridad salga con el mismo `ProblemDetail` de ADR-012 que el resto de la API.
- `@EnableMethodSecurity`.
- Borra el `LOGGER.warn` de placeholder y el Javadoc que anuncia el hueco.

`infrastructure/security/filter/PasswordChangeRequiredFilter`: si el JWT autenticado lleva
`pwd_change_required = true`, rechaza con 403 `PASSWORD_CHANGE_REQUIRED` todo salvo
`POST /api/v1/admin/me/password` y `POST /api/v1/auth/logout` (auth.md 3.9). **Una sola regla en el
filtro**, nunca una comprobación repetida en cada caso de uso.

### Fase 13 — `@PreAuthorize` en los módulos ya mergeados

Cierra el hueco que `category`, `product`, `discount`, `inventory` y `admin` dejaron documentado.
La anotación va en el **servicio** (caso de uso), que es donde `dev-plan.md` dijo que iría, no en el
controlador; con `permitAll` a nivel de filtro y method security como única fuente de verdad, una
petición anónima a un servicio anotado sale como 401 (la traduce `ExceptionTranslationFilter`) y una
autenticada sin rol como 403.

Roles por documento — **léelos de la tabla §4 de cada `docs/features/*.md`, no de memoria**:

- `category.md` §4: `GET /categories`, `GET /categories/{idOrSlug}` públicos; los otros 7, `ADMIN`.
- `product.md` §4: los 4 `GET` públicos de catálogo y `GET /product-attributes` públicos; los 10
  de administración más los 3 de `product-attributes`, `ADMIN`.
- `product-discounts.md` §4: los 5, `ADMIN`.
- `inventory.md` §4: los 6, `ADMIN`.
- `admin.md` §3.1: todo `/admin/users*` exige **`OWNER`**; los dos `/admin/me`, cualquier admin
  autenticado.

Un `ADMIN` que llame a `/admin/users` recibe 403, no 404 (admin.md 3.1, decisión explícita).

### Fase 14 — Rate limiting (ADR-016)

`infrastructure/security/filter/RateLimitFilter` + `RateLimitProperties`
(`@ConfigurationProperties("app.rate-limit")`) + resolución de IP cliente
(`CF-Connecting-IP` solo si la IP del socket cae en `app.rate-limit.trusted-proxies`, lista **vacía
por defecto**).

Aplica solo a las rutas de la tabla de ADR-016 que existen en esta rama: `/auth/admin/login`,
`/auth/admin/mfa`, `/auth/refresh`. Deja los valores de las demás en `application.yml` sin filtro
que los use todavía (los activará `feature/customer`).

Dos buckets por petición (identificador + IP), gana el más restrictivo, ambos se consumen. Clave de
identificador = HMAC del email (`PiiCryptoPort#hmac`), nunca el email. Rechazo: 429, `ProblemDetail`
con `RATE_LIMIT_EXCEEDED`, cabecera `Retry-After`. El filtro va **antes** de la verificación Argon2id.
Mapa de buckets con expulsión de claves inactivas.

`application.yml`: `app.security.jwt.secret` (con default de desarrollo, mismo patrón que
`pii-encryption-key`), `app.security.jwt.access-token-ttl`, `admin-refresh-ttl`, `mfa-token-ttl`, y
el bloque `app.rate-limit.*` con los números de ADR-016.

### Fase 15 — Tests

Sigue el patrón ya establecido (Surefire solo recoge `*Test.java`, sin sufijo `IT`):

- Dominio: `RefreshTokenTest` — rotación copia `expiresAt`, no lo extiende; expirado/revocado.
- Servicios (Mockito), uno por caso de uso, cubriendo **cada fila** de la tabla de estados de 3.5 y
  **cada caso borde** de auth.md §10.
- `TotpAdapterTest`: **vectores de prueba de RFC 6238** (los del apéndice B), ±1 paso, rechazo del
  paso ya consumido, rechazo del mismo código dos veces.
- `JwtAccessTokenAdapterTest`: emisión/parseo, expiración, firma inválida, y que un `typ = "mfa"`
  no valida como access token.
- `AuthControllerTest` (`@WebMvcTest`): **todo `POST` necesita `.with(csrf())`** o da 403 contra el
  `SecurityConfig` real que el test importa. Verifica atributos de la cookie (`HttpOnly`, `Secure`,
  `SameSite=Strict`, `Path`) y que el refresh token **no** aparece en el cuerpo.
- `RefreshTokenPersistenceAdapterTest` con Testcontainers + Postgres real + las 16 migraciones.
- Test de integración de seguridad: anónimo a un endpoint `ADMIN` ⇒ 401; `ADMIN` a `/admin/users`
  ⇒ 403; `OWNER` ⇒ 200; sesión con `pwd_change_required` ⇒ 403 salvo en los dos endpoints permitidos.
- `RateLimitFilterTest`: agotar el bucket ⇒ 429 con `Retry-After`; misma respuesta para identificador
  existente e inexistente.

`mvn verify` en verde (Checkstyle + tests + ArchUnit + SpotBugs) antes de declarar nada terminado.

### Fase 16 — Documentación

- `auth.md`: refleja **lo implementado**. Marca explícitamente el login de cliente como no
  implementado todavía, y añade la frase de CSRF de la sección 2 de este plan.
- `00-security-validation-integrity.md`: sección 7 remite a ADR-016 para los números; sección 12
  elimina el punto 3 (queda cerrado) y renumera.
- `.claude/dev-plan.md`: fila 08 a "Mergeado en `main`", con la nota de qué quedó fuera; retira el
  hueco de `@PreAuthorize` de las notas de `category` y `admin`, que esta rama cierra.
- `docs/database/README.md`: sin cambios (no hay migración).

---

## 4. Trampas conocidas de este repositorio

1. **CodeQL log-injection (CWE-117):** cualquier `String` controlado por la petición que llegue a un
   log necesita `Encode.forJava(...)` **en la propia llamada al log**, no a través de
   `LogSanitizer`. En esta rama casi todo lo logueado son UUID y enums, que no lo necesitan.
2. **CodeQL CWE-190:** ya mordió en `PiiCrypto`. Si aparece aritmética sobre un tamaño de array,
   elimina la aritmética antes que intentar guardarla con una comprobación de rango.
3. **`@Query` propio en Spring Data no es transaccional por sí solo** — el `UPDATE` de `revokeFamily`
   necesita `@Modifying` + `@Transactional` explícitos (mismo tropiezo que
   `countActiveOwnersForUpdate` en admin).
4. **Sin PII en logs** — email, `mfaToken`, refresh token, código y secreto TOTP: ninguno se loguea
   jamás, ni en `DEBUG`. Loguea el UUID del admin.
5. **Javadoc en todos los métodos**, incluidos privados, y `@param` en los records.

## 5. Definición de terminado

- Los 6 endpoints de auth funcionan y sus tests pasan.
- Ningún endpoint de administración responde a una petición anónima.
- `AdminController` funciona de verdad por primera vez (actor resuelto desde el JWT).
- `SecurityConfig` ya no loguea el `WARN` de placeholder.
- `mvn verify` en verde.
- Documentación actualizada según la fase 16.
