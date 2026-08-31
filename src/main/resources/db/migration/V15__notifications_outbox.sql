-- V15__notifications_outbox.sql
-- Bandeja de salida transaccional para los correos. La fila se escribe en la misma
-- transaccion que el cambio de negocio que debe el correo, y una tarea programada la
-- vacia. Ver ADR-015 y docs/features/notification.md.
--
-- La fila guarda una REFERENCIA, nunca contenido: ni destinatario, ni asunto, ni cuerpo.
-- Un correo de confirmacion de pedido lleva nombre, direccion y telefono del
-- destinatario, y guardarlo aqui seria una segunda copia en claro de lo que ADR-005
-- cifra en orders y order_deliveries, fuera del alcance de la purga de ADR-007. El
-- remitente renderiza en el momento de enviar, leyendo las tablas de origen. Es el mismo
-- motivo por el que idempotency_keys no guarda el cuerpo de la respuesta (ADR-011).

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    type VARCHAR(40) NOT NULL,
    order_id UUID REFERENCES orders (id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customers (id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(500),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_notifications_type CHECK (type IN (
        'EMAIL_VERIFICATION',
        'PASSWORD_RESET',
        'ORDER_CONFIRMED',
        'ORDER_REJECTED',
        'ORDER_CANCELLED',
        'ORDER_DELIVERED',
        'REFUND_ISSUED',
        'STAFF_NEW_ORDER',
        'STAFF_ORDER_CANCELLED_BY_CUSTOMER'
    )),
    CONSTRAINT chk_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),

    -- Toda notificacion habla de un pedido o de un cliente; sin ninguna de las dos
    -- referencias no hay nada que renderizar.
    CONSTRAINT chk_notifications_reference CHECK (order_id IS NOT NULL OR customer_id IS NOT NULL),

    CONSTRAINT chk_notifications_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_notifications_sent_at_consistency CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);

-- Consulta del remitente: lo pendiente que ya toca, mas antiguo primero. Indice parcial
-- porque las filas SENT son la inmensa mayoria y no se consultan nunca por esta via.
CREATE INDEX ix_notifications_due ON notifications (next_attempt_at) WHERE status = 'PENDING';

-- Consulta del panel: los fallos definitivos, que son la alerta (ADR-015).
CREATE INDEX ix_notifications_failed ON notifications (created_at) WHERE status = 'FAILED';

CREATE INDEX ix_notifications_order ON notifications (order_id);
