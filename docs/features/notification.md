# Notification

Los correos que envía el sistema, y el mecanismo que garantiza que salen. Ningún otro módulo habla con
un proveedor de correo: registran una notificación y siguen con lo suyo.

Mecanismo y alternativas descartadas en
[ADR-015](../architecture/ADR/ADR-015-transactional-outbox-for-notifications.md). Reglas transversales
en [`00-security-validation-integrity.md`](00-security-validation-integrity.md).

---

## 1. Resumen

Enviar un correo es una llamada externa, así que no puede ir dentro de una transacción — el mismo
problema que la pasarela de pago en [`order.md`](order.md). La solución es una **bandeja de salida
transaccional**: el caso de uso inserta una fila en `notifications` dentro de su propia transacción, y
una tarea programada la envía después.

Eso da la garantía que importa en las dos direcciones: un pedido que se confirma no puede quedarse sin
su correo, y un pedido que no llega a confirmarse no puede generar uno.

**La fila guarda una referencia, nunca el contenido.** Ni destinatario, ni asunto, ni cuerpo. El
remitente renderiza en el momento de enviar, leyendo las tablas de origen (regla 3.3).

---

## 2. Tablas implicadas

`notifications` (`V15`). Lee `orders`, `order_items`, `order_deliveries`, `customers`, `payments` y
`order_status_history` para renderizar; no escribe en ninguna. Esquema en
[`../database/README.md`](../database/README.md).

| Columna | Restricción |
|---|---|
| `type` | Los nueve tipos de la regla 3.1 (`chk_notifications_type`) |
| `order_id` / `customer_id` | Al menos uno (`chk_notifications_reference`) — no hay fila sin nada que renderizar |
| `status` | `PENDING`, `SENT`, `FAILED` |
| `attempts` / `next_attempt_at` | Política de reintentos (regla 3.5) |
| `last_error` | Motivo del último fallo, para el panel |
| `sent_at` | Existe si y solo si `status = 'SENT'` |

`V14` añade `order_status_history.reason`, que hasta ahora no existía: [`order.md`](order.md) pedía el
motivo al rechazar o cancelar y decía que el cliente lo vería «potencialmente», pero no había dónde
guardarlo. Aquí ese motivo es el cuerpo del correo, así que deja de ser potencial.

---

## 3. Reglas de negocio

### 3.1 Catálogo de notificaciones

| Tipo | Para quién | Cuándo | Lo dispara |
|---|---|---|---|
| `EMAIL_VERIFICATION` | Cliente | Al registrarse, o al pedir un reenvío | [`customer.md`](customer.md), regla 3.1 |
| `PASSWORD_RESET` | Cliente | Al pedir un reseteo | [`customer.md`](customer.md), regla 3.5 |
| `ORDER_CONFIRMED` | Cliente | Fase 3a del checkout | [`order.md`](order.md), regla 3.2 |
| `STAFF_NEW_ORDER` | Floristería | Fase 3a del checkout | [`order.md`](order.md), regla 3.2 |
| `ORDER_REJECTED` | Cliente | El administrador rechaza | [`order.md`](order.md), regla 3.9 |
| `ORDER_CANCELLED` | Cliente | El administrador cancela | [`order.md`](order.md), regla 3.9 |
| `STAFF_ORDER_CANCELLED_BY_CUSTOMER` | Floristería | El cliente cancela | [`order.md`](order.md), regla 3.9 |
| `ORDER_DELIVERED` | Cliente | El pedido pasa a `DELIVERED` | [`order.md`](order.md), regla 3.9 |
| `REFUND_ISSUED` | Cliente | Reembolso automático o manual | [`payment.md`](payment.md), reglas 3.3 y 3.4 |

**`ORDER_CONFIRMED` es el único comprobante que recibe un invitado**, que no tiene cuenta donde
consultar el pedido después.

`ORDER_REJECTED` y `ORDER_CANCELLED` llevan el motivo que escribió el administrador
(`order_status_history.reason`, `V14`), obligatorio en ambos casos. `STAFF_ORDER_CANCELLED_BY_CUSTOMER`
no lleva motivo: un cliente que cancela su pedido no tiene que justificarse
([`order.md`](order.md), `CancelOrderRequest`).

**Qué no se notifica, a propósito.** Aceptar, preparar y salir a reparto no generan correo: son tres
avisos más por pedido para decir que todo va según lo previsto. Las alertas de inventario tampoco
([`inventory.md`](inventory.md), decisión ya cerrada); se consultan en el panel. Y ningún cambio de
`admin_users` genera correo — el `OWNER` comunica las contraseñas provisionales en persona
([`admin.md`](admin.md), regla 3.2).

### 3.2 Los destinatarios de la floristería salen de configuración

`STAFF_NEW_ORDER` y `STAFF_ORDER_CANCELLED_BY_CUSTOMER` van a un buzón de la tienda, declarado en
configuración (`app.notification.staff-recipients`), no a los administradores de `admin_users`. Son
dos cosas distintas: quién puede entrar al panel y a quién le llegan los avisos operativos. Añadir un
administrador no debería suscribirle a nada, y quitar a uno no debería dejar el buzón vacío.

### 3.3 La fila no guarda el contenido

Ni destinatario, ni asunto, ni cuerpo, ni HTML renderizado. Solo `type` y las referencias.

Un `ORDER_CONFIRMED` renderizado lleva el nombre, la dirección y el teléfono del destinatario;
guardarlo sería una segunda copia en claro de lo que
[ADR-005](../architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md) cifra en `orders` y
`order_deliveries`, en una tabla que la purga de
[ADR-007](../architecture/ADR/ADR-007-historical-integrity-and-data-lifecycle.md) no conoce. Es
exactamente el motivo por el que `idempotency_keys` tampoco guarda el cuerpo de la respuesta
([ADR-011](../architecture/ADR/ADR-011-idempotent-money-operations.md)).

El remitente renderiza al enviar, descifrando por el mismo camino que cualquier otra lectura.
Consecuencia buscada: una plantilla corregida alcanza también a los correos que aún no han salido.

### 3.4 La notificación se registra en la transacción del negocio

`RegisterNotificationUseCase` es una capacidad que consumen otros módulos, sin endpoint propio — mismo
patrón que `RegisterStockMovementUseCase` en [`inventory.md`](inventory.md).

Se invoca **dentro** de la transacción que hace el cambio. Si el checkout se deshace, la fila de
`notifications` se va con él; si confirma, el correo está comprometido. No hay ventana entre las dos
cosas, y no hace falta ningún mecanismo de reconciliación
([ADR-015](../architecture/ADR/ADR-015-transactional-outbox-for-notifications.md)).

Lo que **nunca** hace un caso de uso de negocio es esperar a que el correo salga. Un proveedor de
correo lento o caído no puede retrasar ni tumbar un checkout.

### 3.5 Envío y reintentos

Una tarea programada toma las filas `PENDING` con `next_attempt_at <= now()`, más antiguas primero, y
las envía. Por cada fallo incrementa `attempts`, guarda `last_error` y aleja `next_attempt_at`:

| Intento | Espera desde el fallo anterior |
|---|---|
| 2.º | 1 minuto |
| 3.º | 5 minutos |
| 4.º | 30 minutos |
| 5.º | 2 horas |

Tras el quinto fallo la fila queda `FAILED` y no se reintenta más. La progresión cubre desde un
parpadeo de red hasta una caída de varias horas del proveedor, sin insistir indefinidamente sobre algo
que ya está roto de otra manera.

Cada envío es independiente: un fallo no bloquea la cola. Que un correo no salga **nunca** afecta al
flujo de negocio que lo originó — el pedido está confirmado y cobrado, pase lo que pase con el aviso.

### 3.6 Un fallo definitivo es una alerta visible

`GET /admin/notifications/failed` lista las filas `FAILED` con su tipo, su referencia y `last_error`.
Eso es la alerta.

**No se avisa por correo de que un correo falló**, que sería circular: lo único que se sabe roto es
justo el canal. Mismo criterio que las alertas de [`inventory.md`](inventory.md), que se consultan en
el panel y no se empujan a ningún sitio.

Desde ahí, `POST /admin/notifications/{id}/retry` devuelve la fila a `PENDING` con los intentos a cero,
para cuando el problema de fondo ya está resuelto. Es la única forma de reintentar un `FAILED`, y es
manual a propósito: si algo agotó cinco intentos escalonados, volver a intentarlo solo tiene sentido
cuando alguien ha arreglado la causa.

### 3.7 Los correos con token no se pueden reintentar

`EMAIL_VERIFICATION` y `PASSWORD_RESET` llevan un token de un solo uso cuyo texto en claro existe
únicamente en la petición que lo generó: `verification_tokens` guarda su SHA-256, por diseño
([ADR-005](../architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md)). **No son
reconstruibles desde la base de datos**, así que ninguna tarea posterior puede volver a componerlos.

Estos dos tipos tienen su fila igual —para que un fallo quede registrado y aparezca en la lista de la
regla 3.6— pero los envía la propia petición que los origina, justo después de confirmar, con el token
que todavía tiene en memoria. Si falla, la fila queda `FAILED` de inmediato, sin reintentos.

El remedio ya existe y es del usuario: `POST /customers/resend-verification` y
`POST /customers/password-reset/request` ([`customer.md`](customer.md)) emiten un token nuevo. Por eso
`POST /admin/notifications/{id}/retry` los rechaza con 409: reintentar es imposible, no difícil.

Guardar el token en claro para hacerlos reintentables se descartó sin más: pondría una credencial
funcionando en una tabla, deshaciendo el hash que ADR-005 eligió a propósito, para ahorrarle un clic a
alguien.

### 3.8 Idioma y plantillas

Todo en español. No hay preferencia de idioma por cliente ni columna donde guardarla: la tienda es
local y el catálogo está solo en español.

Las plantillas viven en el repositorio, no en base de datos. Cambiarlas es un despliegue, que es
exactamente lo que debe ser: son código con formato de texto, se revisan en un *pull request* y no
tienen por qué ser editables desde el panel.

---

## 4. Endpoints

Prefijo `/api/v1`. Todos `ADMIN`; sin superficie pública. Un cliente no consulta ni gestiona sus
notificaciones — las recibe.

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/admin/notifications` | 200 — paginado; filtra por `status` y `type` |
| `GET` | `/admin/notifications/failed` | 200 — la alerta de la regla 3.6 |
| `POST` | `/admin/notifications/{id}/retry` | 200 — devuelve una `FAILED` a la cola |

`failed` es ruta propia y no un filtro del listado por el mismo motivo que `unattached` en
[`image.md`](image.md): es una vista con significado en el panel, no una consulta cualquiera.

Sin endpoint para enviar una notificación a mano. Las crean los casos de uso que las deben (regla 3.4);
un endpoint de envío arbitrario sería una vía para mandar correos en nombre de la tienda sin ningún
hecho de negocio detrás.

---

## 5. Request DTOs

Ninguno. `retry` no lleva cuerpo, y los dos `GET` solo llevan parámetros de consulta.

---

## 6. Response DTOs

### `NotificationResponse`

`id`, `type`, `orderId`, `orderNumber`, `customerId`, `status`, `attempts`, `nextAttemptAt`,
`lastError`, `sentAt`, `createdAt`.

`orderNumber` porque es lo que el administrador reconoce; el UUID no le dice nada.

**No incluye el destinatario ni ningún dato del cliente.** La fila no los guarda (regla 3.3), y
descifrarlos para pintarlos en una lista de diagnóstico expondría PII donde no hace falta: para saber
qué correo falló bastan el tipo y el pedido.

---

## 7. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `RegisterNotificationUseCase` | `RegisterNotificationService` | Sí — sin controlador; lo invocan otros módulos (regla 3.4) |
| `SendPendingNotificationsUseCase` | `SendPendingNotificationsService` | Sí — tarea programada (regla 3.5) |
| `SendTokenNotificationUseCase` | `SendTokenNotificationService` | Sí — envío inmediato de los tipos con token (regla 3.7) |
| `RetryNotificationUseCase` | `RetryNotificationService` | Sí |
| `GetNotificationsUseCase` / `GetFailedNotificationsUseCase` | — | No |

`SendPendingNotificationsService` **no envuelve el envío en una transacción**. Cada fila es su propia
unidad: se marca el intento, se envía fuera de transacción, y se escribe el resultado. Un lote entero
dentro de una transacción mantendría abierta una conexión durante todas las llamadas al proveedor, y
un fallo al final desharía el registro de los envíos que ya salieron de verdad.

Si el proceso muere entre enviar y registrar el resultado, esa fila se reintenta y el destinatario
recibe el correo dos veces. Es el compromiso aceptado: para estos avisos, un duplicado ocasional es
mejor que una pérdida — al revés que en el cobro, donde `Idempotency-Key` existe justo porque ahí el
duplicado es lo inaceptable ([ADR-011](../architecture/ADR/ADR-011-idempotent-money-operations.md)).

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `NotificationReadPort` | `findDue`, `findFailed`, `findAll`, `findById` |
| `NotificationWritePort` | `save`, `markSent`, `markFailed` |
| `EmailSenderPort` | `send(recipient, subject, body)` |
| `EmailTemplatePort` | `render(type, model)` |

`EmailSenderPort` es la frontera con el proveedor: detrás puede haber SMTP, SES o cualquier otro según
el entorno, y en desarrollo un adaptador que escriba en el log en vez de enviar nada. El dominio no
sabe cuál — mismo criterio que `PaymentGatewayPort` en [`payment.md`](payment.md). El proveedor
concreto es una decisión de despliegue, no de este documento.

Este módulo **lee** de otros a través de sus puertos de lectura (`OrderReadPort`, `CustomerReadPort`,
`PaymentReadPort`) para renderizar. Es la dirección correcta: `notification` depende de ellos, no al
revés — los módulos de negocio solo conocen `RegisterNotificationUseCase`.

Persistencia ([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): JPA para insertar y actualizar
el estado de una fila; JDBC para el listado paginado del panel y para tomar el lote pendiente, que es
una consulta con orden y límite sobre el índice parcial de `V15`.

---

## 9. Errores

Enum `NotificationErrorCode` en `domain/exception/notification/`
([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `NOTIFICATION_NOT_FOUND` | 404 | No existe |
| `NOTIFICATION_NOT_FAILED` | 409 | `retry` sobre una fila que no está `FAILED` |
| `NOTIFICATION_NOT_RETRYABLE` | 409 | `retry` sobre un tipo con token (regla 3.7) |

El fallo de envío en sí no es un error de API: nadie está esperando la respuesta. Queda en
`last_error` y en la lista de la regla 3.6.

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| El proveedor de correo está caído durante el checkout | El pedido se confirma y se cobra igual; el correo sale cuando el proveedor vuelva (regla 3.5) |
| El checkout se deshace después de registrar la notificación | La fila se va con la transacción; no se envía nada (regla 3.4) |
| El proceso muere entre enviar y marcar `SENT` | La fila se reintenta y el correo llega dos veces. Aceptado (regla 7) |
| `retry` sobre un `EMAIL_VERIFICATION` fallido | 409 `NOTIFICATION_NOT_RETRYABLE`; el cliente pide un reenvío, que emite un token nuevo (regla 3.7) |
| `retry` sobre una fila `PENDING` | 409 `NOTIFICATION_NOT_FAILED`: ya está en la cola |
| Un pedido se rechaza sin motivo | No ocurre: `reason` es `@NotBlank` para el administrador y `chk_order_status_history_reason_required` (`V14`) lo impide también en la base de datos |
| El correo del comprador ya no existe cuando la notificación se envía | El envío falla y agota reintentos; queda `FAILED` con el error del proveedor |
| Pedido de mostrador (`STORE`/`INTERFLORA`) sin email del comprador | No se registra `ORDER_CONFIRMED`: el comprador es opcional ahí ([`payment.md`](payment.md), regla 3.6) y no hay a quién escribir |
| PII del pedido ya purgada al renderizar | No ocurre en la práctica: la purga actúa años después ([`scheduled-tasks.md`](scheduled-tasks.md)) y las filas `PENDING` se vacían en minutos |
| `app.notification.staff-recipients` sin configurar | Las notificaciones de tipo `STAFF_*` fallan y quedan `FAILED`, visibles en el panel; nada más se ve afectado |

---

## 11. Alcance ajeno

- **Cuándo ocurre cada hecho que dispara un correo** — [`order.md`](order.md),
  [`payment.md`](payment.md), [`customer.md`](customer.md); este documento solo dice qué se manda.
- **Emisión y validación de los tokens** — [`customer.md`](customer.md); aquí solo se envían.
- **Proveedor de correo concreto y sus credenciales** — configuración de despliegue.
- **Limpieza de las filas `SENT`** — [`scheduled-tasks.md`](scheduled-tasks.md), sección 3.3, junto a
  la de tokens e `idempotency_keys`; sigue pendiente de decidir frecuencia.
- **Notificación de alertas de inventario** — fuera de alcance por decisión explícita de
  [`inventory.md`](inventory.md), no reabierta aquí.
- **Invitación de administradores por correo** — descartada en [`admin.md`](admin.md) (regla 3.2) y
  **confirmada aquí**: no se generaliza `verification_tokens` a los dos sujetos. El `OWNER` sigue
  fijando las contraseñas provisionales a mano.
