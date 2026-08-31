# Payment

Cobro, reembolso y tarjetas guardadas. Implementa `OrderPaymentPort` (`charge`, `refund`), definido en
[`order.md`](order.md) — `order` pide "cobra esto" y "reembolsa esto" sin saber si detrás hay una
pasarela real, efectivo de mostrador o un acuerdo de facturación con una red externa. Cierra dos
pendientes que `order.md` dejó abiertos: la mecánica de pago de `STORE`/`INTERFLORA` y qué cuenta como
"cuerpo distinto" para la `Idempotency-Key` del checkout.

Reglas transversales en [`00-security-validation-integrity.md`](00-security-validation-integrity.md).

---

## 1. Resumen

`payments` es la fuente de verdad del pago de un pedido, independiente de `orders.status`
(cumplimiento) — ver `order.md`, regla 1. Este módulo:

- Implementa `OrderPaymentPort` para que `order` cobre y reembolse sin conocer la pasarela.
- Expone la gestión de tarjetas guardadas del cliente (`customer_payment_methods`).
- Expone el reembolso manual del administrador, independiente del automático que dispara `order`.

Cobro y reembolso son siempre **síncronos**: la respuesta de la pasarela es definitiva en el momento
de la llamada. No hay endpoint de webhook para eventos posteriores (disputas, contracargos) — decisión
explícita, sección 11.

---

## 2. Tablas implicadas

`payments`, `customer_payment_methods`. Esquema base en
[`../database/README.md`](../database/README.md); columnas añadidas por `V10`.

| Columna de `payments` | Restricción |
|---|---|
| `order_id` | `RESTRICT` — un pago no puede quedar huérfano de su pedido |
| `method` | `CARD_ONLINE`, `CASH`, `DATAPHONE`, `INTERFLORA` (`V10`) |
| `provider` | `STRIPE` (o equivalente) para `CARD_ONLINE`; `MANUAL` para el resto |
| `provider_payment_intent_id` | Id de la operación de cobro en la pasarela; solo `CARD_ONLINE` |
| `provider_reference` | Id de la operación de reembolso en la pasarela; solo reembolsos de `CARD_ONLINE` |
| `status` | `PENDING`, `AUTHORIZED`, `CAPTURED`, `CANCELED`, `FAILED` |
| `refunded_amount` / `refunded_at` | Soportan reembolso parcial; `refunded_amount <= amount` |
| `refund_reason` | `V10`; solo se rellena en el reembolso manual (regla 3.4) — el automático no lo necesita, la propia transición de `orders.status` ya lo explica |

`customer_payment_methods` no cambia: `payment_method_token`, `provider_customer_id`, `card_brand`,
`last_four`, `exp_month`, `exp_year`, `is_default` (índice único parcial, un solo `true` por cliente).

---

## 3. Reglas de negocio

### 3.1 Quién captura cada método

`CARD_ONLINE` es el único que llama a una pasarela real. El resto se captura directamente, en la misma
transacción que crea el pedido, sin ventana de espera externa:

| `method` | Captura | `provider` |
|---|---|---|
| `CARD_ONLINE` | Llamada síncrona a la pasarela | `STRIPE` (o equivalente) |
| `CASH`, `DATAPHONE` | Directa, personal de tienda | `MANUAL` |
| `INTERFLORA` | Directa, garantizada por la red | `MANUAL` |

### 3.2 `OrderPaymentPort.charge(order, method, ...)`

Una sola implementación que ramifica por `method`:

- **`CARD_ONLINE`**: llama a la pasarela con `paymentMethodToken` (tarjeta nueva) o
  `paymentMethodId` + `provider_customer_id` (tarjeta guardada, regla 3.5). Si la pasarela rechaza el
  cobro, se propaga como fallo — `order.md` lo traduce a `CHECKOUT_PAYMENT_DECLINED` (402) y ejecuta su
  Fase 3b.
- **`CASH`, `DATAPHONE`, `INTERFLORA`**: no llama a nada externo. Inserta directamente la fila en
  `payments` con `status = CAPTURED`, `provider = MANUAL`. No puede fallar por causas de pasarela; solo
  por lo que ya cubre la transacción de base de datos.

Este es el método que usa tanto el checkout de `WEB` (Fase 2, `order.md` regla 3.2) como
`POST /orders/counter` (regla 3.6) — mismo puerto, mismo método, la única diferencia es qué rama de
`method` se ejecuta.

### 3.3 `OrderPaymentPort.refund(order, ...)` — automático

Disparado por `order.md` cuando un pedido pasa a `REJECTED` (desde `PENDING`) o `CANCELLED` (desde
`PENDING` o `ACCEPTED`, regla 3.9 de `order.md`). En ambos casos el pedido no llegó a prepararse ni
entregarse, así que **siempre es reembolso total**: `refunded_amount = amount`, sin decisión que tomar.
`refund_reason` queda `NULL` — la transición de estado del pedido ya es el motivo.

Para `CARD_ONLINE`, llama a la pasarela y guarda su `provider_reference`. Para `CASH`/`DATAPHONE`/
`INTERFLORA`, no hay pasarela que llamar: se actualiza `refunded_amount`/`refunded_at` directamente:
el dinero físico o la nota de crédito la gestiona el personal de tienda o la propia red Interflora, el
backend solo registra que el pedido quedó saldado a cero.

### 3.4 Reembolso manual (administrador)

Independiente del automático — cubre casos como una reclamación tras la entrega. `POST
/orders/{id}/refund`, `ADMIN`. Importe libre entre 1 céntimo y lo que quede por reembolsar
(`amount - refunded_amount`), motivo obligatorio (`refund_reason`). Solo sobre pagos `CAPTURED`
(`REFUND_NOT_ALLOWED` en cualquier otro estado). Auditado en `audit_log`
([ADR-010](../architecture/ADR/ADR-010-admin-audit-log.md)) — solo `changed_fields`, sin valores:
`payment` no está en la lista blanca de `chk_audit_log_changes_pii_free` y no hace falta añadirla, el
propio `refund_reason` en `payments` ya conserva el motivo.

Reembolsar tras la entrega **no** devuelve unidades de descuento reservadas
([`product-discounts.md`](product-discounts.md), ya cerrado) ni genera movimiento de stock — es
dinero, no inventario.

Exige cabecera `Idempotency-Key`, igual que `POST /checkout` y `POST /orders/counter`
([ADR-011](../architecture/ADR/ADR-011-idempotent-money-operations.md),
[`00-security-validation-integrity.md`](00-security-validation-integrity.md), sección 6): un timeout
de red tras confirmar el reembolso no debe permitir reembolsarlo dos veces con un reintento. El
`request_fingerprint` es el hash por defecto del cuerpo (`amount` + `reason`), sin excepción de
carrito — este `POST` ya lleva en el cuerpo todo lo necesario para identificar la operación.

### 3.5 Tarjetas guardadas

Añadir una tarjeta es un flujo de dos pasos frente a la pasarela (SetupIntent o equivalente): el
frontend obtiene un `setupToken` directamente de la pasarela (el backend nunca ve el número de tarjeta,
[ADR-005](../architecture/ADR/ADR-005-pii-protection-and-payment-tokenization.md)) y se lo pasa al
backend, que lo cambia por un `payment_method_token` persistente y los metadatos de la tarjeta
(`card_brand`, `last_four`, `exp_month`, `exp_year`). Si es la primera tarjeta del cliente, se crea
también su `provider_customer_id`.

`is_default`: al añadir la primera tarjeta se marca por defecto automáticamente; las siguientes no. Al
eliminar la tarjeta por defecto no se promociona otra automáticamente — el cliente elige explícitamente
en su próximo checkout si tiene más de una. Menos estado que gestionar, sin ambigüedad sobre cuál se
eligió.

El checkout (`order.md`, `CheckoutRequest`) acepta `paymentMethodId` (tarjeta guardada) como
alternativa a `paymentMethodToken` (tarjeta nueva) — ver regla 3.8 y el cambio en `order.md` sección 5.

### 3.6 Venta de mostrador (`STORE`, `INTERFLORA`)

`POST /orders/counter`, `ADMIN`. Mismo patrón que el checkout con `CASH`/`DATAPHONE` de `order.md`
(Fases 1+3a fundidas, regla 3.2 de `order.md`): reserva de stock + `orders`/`order_items`/
`order_deliveries` (si aplica) + `payments` `CAPTURED` + `order_status_history`, todo en una sola
transacción, sin Fase 2 externa porque ningún método de este endpoint llama a una pasarela.

`channel` determina qué métodos son válidos: `STORE` acepta `CASH`/`DATAPHONE`; `INTERFLORA` acepta
únicamente `INTERFLORA` (regla 3.7).

El comprador es opcional: nombre y email no son obligatorios en `POST /orders/counter`, a diferencia
del checkout web. Si se informan, se guardan como snapshot en el propio pedido, igual que cualquier
otro `buyer_*` — nunca crean ni buscan una fila en `customers`; la vinculación automática por email es
exclusiva del canal `WEB` ([`customer.md`](customer.md), regla 3.2).

### 3.7 `INTERFLORA`: facturación diferida

Los pedidos de la red Interflora se liquidan a mes vencido — el dinero no llega el día del pedido. Aun
así, el pago se registra como `CAPTURED` de inmediato: la red garantiza el cobro, y modelarlo como
"pendiente" durante semanas ensuciaría cualquier informe de ventas con pagos que en la práctica siempre
se cobran. La liquidación real (la floristería recibiendo el neto mensual de la red) es un proceso
contable externo al backend — no genera ningún movimiento en `payments`.

### 3.8 Idempotencia del checkout

`POST /checkout` (`order.md`) no lleva productos en el cuerpo — el pedido se construye desde el
carrito del backend. Eso significa que dos peticiones con el mismo cuerpo (`Idempotency-Key` incluida)
pueden corresponder a carritos distintos si el carrito cambió entre medias.

Por eso el `request_fingerprint` de `POST /checkout`
([ADR-011](../architecture/ADR/ADR-011-idempotent-money-operations.md)) no es solo el hash del cuerpo
del `POST`: incluye también un hash del contenido del carrito en ese instante (producto, cantidad,
precio de cada línea). Si el carrito cambió entre el primer intento y un reintento con la misma clave,
el fingerprint cambia y se responde 422 `IDEMPOTENCY_KEY_REUSED` — obliga al frontend a generar una
clave nueva en vez de servir silenciosamente el resultado de un carrito que ya no es el actual. Se
prefiere este comportamiento más estricto: no puede asumirse que el frontend impida por sí solo que el
cliente dispare dos peticiones de checkout con carritos distintos bajo la misma clave.

---

## 4. Endpoints

Prefijo `/api/v1`.

### Tarjetas guardadas (`CLIENTE`, requiere sesión)

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/customers/me/payment-methods` | 200 — lista |
| `POST` | `/customers/me/payment-methods` | 201 |
| `PATCH` | `/customers/me/payment-methods/{id}/default` | 200 |
| `DELETE` | `/customers/me/payment-methods/{id}` | 204 |

### Pagos de pedido (`ADMIN`)

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/orders/{id}/payment` | 200 — detalle: método, importe, estado, reembolsos |
| `POST` | `/orders/{id}/refund` | 200 — reembolso manual (regla 3.4) |

`POST /orders/counter` vive en la superficie REST de `order.md` (regla 4 de ese documento); internamente
invoca `OrderPaymentPort.charge()` como cualquier otro checkout — no se redeclara aquí. Su exigencia de
`Idempotency-Key` sí se redeclara, en [`order.md`](order.md), sección 4.

---

## 5. Request DTOs

### `AddPaymentMethodRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `setupToken` | String | `@NotBlank` — token de la pasarela, nunca datos de tarjeta |

### `RefundRequest`

| Campo | Tipo | Validación |
|---|---|---|
| `amount` | BigDecimal | `@NotNull`, `@Positive`; validado contra `amount - refunded_amount` en el servicio |
| `reason` | String | `@NotBlank`, `@Size(max = 500)` |

`PATCH .../default` y `DELETE .../{id}` no llevan cuerpo — son acciones, no ediciones.

---

## 6. Response DTOs

### `PaymentMethodResponse`

`id`, `cardBrand`, `lastFour`, `expMonth`, `expYear`, `isDefault`, `createdAt`.

Nunca `payment_method_token` ni `provider_customer_id` — son credenciales frente a la pasarela, no
datos que el cliente necesite ver.

### `PaymentResponse`

`id`, `orderId`, `method`, `provider`, `amount`, `status`, `refundedAmount`, `authorizedAt`,
`capturedAt`, `refundedAt`, `refundReason`, `failureReason`, `createdAt`.

---

## 7. Casos de uso

| Use Case | Service | Escritura |
|---|---|---|
| `AddPaymentMethodUseCase` | `AddPaymentMethodService` | Sí |
| `ListPaymentMethodsUseCase` | `ListPaymentMethodsService` | No |
| `SetDefaultPaymentMethodUseCase` | `SetDefaultPaymentMethodService` | Sí |
| `DeletePaymentMethodUseCase` | `DeletePaymentMethodService` | Sí |
| `ChargeOrderPaymentUseCase` | `ChargeOrderPaymentService` | Sí — implementa `OrderPaymentPort.charge`, invocado por `order.md` |
| `RefundOrderPaymentUseCase` | `RefundOrderPaymentService` | Sí — implementa `OrderPaymentPort.refund`, invocado por `order.md` |
| `ManualRefundUseCase` | `ManualRefundService` | Sí — endpoint propio, regla 3.4 |
| `GetOrderPaymentUseCase` | `GetOrderPaymentService` | No |

`ChargeOrderPaymentService` y `RefundOrderPaymentService` no tienen controlador propio: se exponen
solo como implementación del puerto que `order.md` consume, igual que `RegisterStockMovementUseCase`
en `inventory.md` no tiene endpoint público.

---

## 8. Output Ports

| Port | Capacidad |
|---|---|
| `PaymentMethodReadPort` | `findById`, `findAllByCustomer` |
| `PaymentMethodWritePort` | `save`, `delete` |
| `PaymentReadPort` | `findById`, `findByOrderId` |
| `PaymentWritePort` | `save` |
| `PaymentGatewayPort` | `charge`, `refund`, `attachPaymentMethod`, `detachPaymentMethod` — única frontera con la pasarela real |

`PaymentGatewayPort` es la capacidad que aísla Stripe (o el proveedor que sea) del resto del módulo:
`ChargeOrderPaymentService` y `RefundOrderPaymentService` lo llaman para `CARD_ONLINE` y lo ignoran por
completo para `CASH`/`DATAPHONE`/`INTERFLORA`.

Persistencia ([ADR-002](../architecture/ADR/ADR-002-jpa-and-jdbc.md)): JPA para todas las escrituras;
JDBC no hace falta aquí, no hay listados paginados propios de este módulo.

---

## 9. Errores

Enum `PaymentErrorCode` en `domain/exception/payment/`
([ADR-012](../architecture/ADR/ADR-012-api-error-contract.md)).

| Código | Estado | Cuándo |
|---|---|---|
| `PAYMENT_METHOD_NOT_FOUND` | 404 | No existe, o no pertenece a quien pregunta |
| `PAYMENT_NOT_FOUND` | 404 | El pedido no tiene pago asociado |
| `PAYMENT_GATEWAY_ERROR` | 502 | La pasarela falla por causa ajena al negocio (red, 5xx) — distinto de un cobro rechazado |
| `REFUND_EXCEEDS_CAPTURED` | 422 | El importe del reembolso manual supera `amount - refunded_amount` |
| `REFUND_NOT_ALLOWED` | 409 | El pago no está `CAPTURED` |
| `PAYMENT_VALIDATION_FAILED` | 422 | Bean Validation; con `errors[]` |

`CHECKOUT_PAYMENT_DECLINED` (cobro rechazado en el checkout) vive en `OrderErrorCode`
(`order.md`, sección 9) — es un error del flujo de pedido, no de este módulo.

---

## 10. Casos borde

| Situación | Comportamiento |
|---|---|
| Añadir una tarjeta con un `setupToken` ya usado o caducado | `PAYMENT_GATEWAY_ERROR` (502): la pasarela lo rechaza al intentar el `attach` |
| Eliminar la única tarjeta guardada, marcada por defecto | Se permite; el cliente queda sin tarjeta guardada, próximo checkout requiere token nuevo |
| Reembolso manual dos veces sobre el mismo pago hasta agotar el importe | Válido: cada llamada valida contra `amount - refunded_amount` en ese momento |
| Reembolso manual sobre un pago con `refunded_amount = amount` (ya agotado) | `REFUND_EXCEEDS_CAPTURED` (`amount - refunded_amount = 0`) |
| `POST /orders/counter` con `channel = INTERFLORA` y `method = CASH` | 422 `PAYMENT_VALIDATION_FAILED`: `INTERFLORA` solo acepta `method = INTERFLORA` (regla 3.6) |
| Reintento de `POST /checkout` con la misma `Idempotency-Key`, carrito sin cambios | Se responde con el pedido ya creado (ADR-011); mismo fingerprint |
| Reintento de `POST /checkout` con la misma `Idempotency-Key`, carrito modificado entretanto | 422 `IDEMPOTENCY_KEY_REUSED` (regla 3.8): fingerprint distinto |
| Pasarela cae a mitad del cobro (timeout) sin `Idempotency-Key` aún resuelta | La fila de `idempotency_keys` queda `PENDING`; un reintento con la misma clave recibe 409 `OPERATION_IN_PROGRESS` (ADR-011), no repite el cobro |
| Reintento de `POST /orders/{id}/refund` con la misma `Idempotency-Key` tras confirmarse | Se relee el reembolso ya aplicado; no se reembolsa una segunda vez (ADR-011) |

---

## 11. Alcance ajeno

- **Reserva y liberación de stock, y de unidades de descuento** durante el checkout —
  [`order.md`](order.md), [`inventory.md`](inventory.md), [`product-discounts.md`](product-discounts.md).
- **Webhooks de eventos asíncronos de la pasarela** (disputas, contracargos). Decisión explícita: el
  diseño actual asume cobro y reembolso síncronos en toda su superficie; un evento que la pasarela
  reporte por su cuenta, sin que el backend haya iniciado la llamada, no tiene ningún caso de uso
  definido todavía. Se documenta como descartado por ahora, no como pendiente sin cerrar.
- **Liquidación contable real con la red Interflora** (regla 3.7) — proceso externo al backend.
- **Frecuencia de purga de `idempotency_keys`** — `00-security-validation-integrity.md`, sección 12.
- **Correo de confirmación del reembolso** — [`notification.md`](notification.md), tipo
  `REFUND_ISSUED`; este documento solo registra la notificación al reembolsar.
