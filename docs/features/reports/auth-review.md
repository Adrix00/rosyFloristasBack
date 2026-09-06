# Informe de revisión — `auth`

> Fecha: 2026-09-03 · Rama: mergeado en `main` (PR #13) · Spec: `auth.md` · ADRs: 005, 008, 010, 012, 016

## 1. Veredicto

El módulo auth está sólido: los dos fallos de seguridad corregidos previamente (401 vs 403 para anónimos y el 403 de CSRF) se mantienen, y la implementación sigue fielmente `auth.md` y los ADR-008/012/016/005. No hay hallazgos CRITICAL ni HIGH. Quedan dos MEDIUM —uno de auditoría (ADR-010) y una condición de carrera en la rotación de refresh tokens— y tres LOW de robustez y calidad de test.

## 2. Fortalezas

- La semántica 401/403 es correcta de extremo a extremo: `GlobalExceptionHandler.handleAccessDenied` distingue anónimo (401) de rol incorrecto (403) vía `AuthenticationTrustResolverImpl`, y excluye `CsrfException` del chequeo de anonimato, preservando el 403 de CSRF. Verificado también descompilando Spring Security 7.1.0: el `CsrfFilter` invoca el `accessDeniedHandler` configurado, que enruta al `handlerExceptionResolver`.
- El re-chequeo de `admin.active()` tras el paso MFA (fix f991bf4) está presente en `VerifyAdminMfaService` y `EnrollAdminTotpService`, con tests que lo protegen.
- TOTP impecable: vectores RFC 6238 del Apéndice B como fixtures, ventana de ±1 paso, comparación en tiempo constante (`MessageDigest.isEqual`) y protección de replay por `totp_last_used_step`.
- El login de dos pasos usa un hash Argon2id señuelo para email desconocido, manteniendo el tiempo de respuesta uniforme (sin enumeración de cuentas por timing).
- Rotación de refresh tokens conforme a ADR-008: un solo uso, `family_id`, detección de reuso que revoca toda la familia, `expiresAt` copiado sin extender.
- Cookies con `HttpOnly`, `Secure`, `SameSite=Strict` y `Path=/api/v1/auth`; el refresh token nunca aparece en el cuerpo de la respuesta.
- Rate limiting conforme a ADR-016: doble bucket (identificador HMAC + IP), `refillGreedy`, `CF-Connecting-IP` solo desde proxies de confianza, evicción programada y 429 con `Retry-After`.
- Los tests son especificaciones ejecutables: protegen reglas de negocio reales (reuso de familia, replay de TOTP, claims del JWT, atributos de cookie), no cobertura por cobertura.

## 3. Hallazgos

| # | Severidad | Archivo | Hallazgo |
|---|---|---|---|
| 1 | 🟡 **Medio** | `AdminLoginService.java:109-111`, `VerifyAdminMfaService.java:108-111` (con `AuditLogPersistenceAdapter.java:59`) | **Los intentos de login fallidos no dejan fila en el audit log: la transacción la revierte.** `auditLogPort.record(LOGIN_FAILED)` se ejecuta dentro del servicio `@Transactional` e inmediatamente después se lanza la excepción de dominio; el `save` del adaptador no fuerza flush y no usa `REQUIRES_NEW`, así que el rollback descarta la fila de auditoría. `POST /api/v1/auth/admin/login` con contraseña incorrecta (o `POST /api/v1/auth/admin/mfa` con código TOTP inválido) → el cliente recibe 401, pero `audit_log` no contiene ninguna fila `LOGIN_FAILED`. ADR-010 exige auditar los intentos fallidos; hoy el código lo intenta y la base de datos lo pierde. Fix: persistir la auditoría en una transacción `REQUIRES_NEW` (o flush explícito antes del throw) en el adaptador de auditoría. |
| 2 | 🟡 **Medio** | `RefreshTokenJpaRepository.java:33-34` (usado en `RefreshTokenService.java:98-99`) | **Condición de carrera en la rotación de refresh tokens: dos refrescos simultáneos pueden emitir dos tokens vivos de la misma familia.** El `UPDATE ... SET revokedAt = :revokedAt WHERE r.id = :id` no incluye `AND revokedAt IS NULL` y el servicio ignora el número de filas actualizadas, así que dos peticiones concurrentes con la misma cookie pueden leer ambas la fila sin revocar (READ COMMITTED) y rotar las dos. Dos pestañas llaman a `POST /api/v1/auth/refresh` a la vez con la misma cookie → ambas leen la fila no revocada, ambas rotan y persisten su sucesor → dos tokens vivos en una misma familia. `auth.md` §10 afirma que la segunda "encuentra la fila ya revocada y cae toda la familia"; la implementación solo lo garantiza si la segunda lectura ocurre tras el commit de la primera. Fix: revocación condicional (`AND revokedAt IS NULL`) y tratar 0 filas actualizadas como reuso (revocar familia + `SESSION_REVOKED`). |
| 3 | 🟢 **Bajo** | `PasswordChangeRequiredFilter.java:31-32,89` | **El filtro de cambio de contraseña bloquea el refresh mientras el access token antiguo siga en el header.** Tras cambiar la contraseña, el access token emitido antes del cambio sigue llevando `pwd_change_required=true` hasta 5 minutos; si el frontend llama a `POST /auth/refresh` con ese token en `Authorization`, el filtro responde 403 porque solo permite `/admin/me/password` y `/auth/logout`. Admin cambia su contraseña y el SPA refresca la sesión inmediatamente enviando el access token antiguo → 403 `PASSWORD_CHANGE_REQUIRED` en lugar del nuevo access token sin el claim que promete `auth.md` regla 3.9. La especificación asume implícitamente que el refresh se llama sin el header; conviene documentarlo en `auth.md` (o permitir el refresh en el filtro). |
| 4 | 🟢 **Bajo** | `RateLimitFilter.java:52`, `PasswordChangeRequiredFilter.java:28` | **Ambos filtros se registran dos veces como filtros de servlet.** Son beans `@Component` de tipo `Filter`, así que Spring Boot los auto-registra en el contenedor de servlets además de en la cadena de seguridad; hoy la doble ejecución solo se evita por el atributo de `OncePerRequestFilter`. Si alguien cambia uno de estos filtros para no extender `OncePerRequestFilter`, cada petición lo ejecutaría dos veces —el rate limiter consumiría el doble de tokens por petición. Fix: un `FilterRegistrationBean` con `setEnabled(false)` para cada uno, dejando solo el registro en la cadena de seguridad. |
| 5 | 🟢 **Bajo** | `AuthControllerTest.java:198-204` | **Nombre de test engañoso: afirma que no llama al use case y lo verifica llamado.** `logoutWithoutACookieReturns204AndDoesNotCallTheUseCase` termina con `verify(logoutUseCase).execute(any())`, es decir, verifica exactamente lo contrario de lo que su nombre anuncia. El contrato real es que el controlador siempre lo invoca (con token nulo) y el 204 sale del servicio. Fix: renombrar a algo como `logoutWithoutACookieStillInvokesTheUseCaseAndReturns204`. |

## 4. Recomendaciones

1. **Antes de producción:** #1 (auditoría `REQUIRES_NEW` — ADR-010 es explícito sobre auditar intentos fallidos) y #2 (revocación condicional + tratar 0 filas como reuso).
2. **Opcional:** #3 (documentar en `auth.md` o permitir el refresh en el filtro), #4 (deshabilitar el auto-registro de servlet), #5 (renombrar el test).
