-- V18__checkout_redsys_and_order_status_simplification.sql
-- ADR-017: cobro CARD_ONLINE por redireccion (TPV Virtual Redsys), y simplificacion del ciclo
-- de vida del pedido (se elimina ACCEPTED, order.md 3.9).

-- idempotency_keys gana el payload cifrado que necesita el checkout por redireccion mientras
-- el cliente paga en Redsys: nunca una segunda copia indefinida, se limpia junto con la fila
-- (release de la reserva incluido) a los 10 minutos sin notificacion (scheduled-tasks.md 3.3).
ALTER TABLE idempotency_keys ADD COLUMN checkout_payload_encrypted BYTEA;

-- ACCEPTED deja de existir: un pedido pagado entra directo en PENDING, listo para prepararse
-- (order.md 3.9). CANCELLED pasa a alcanzar tambien PREPARING (ya lo asumia order.md 3.10 antes
-- de que el grafo lo permitiera).
ALTER TABLE orders DROP CONSTRAINT chk_orders_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('PENDING', 'PREPARING', 'OUT_FOR_DELIVERY', 'DELIVERED', 'REJECTED', 'CANCELLED'));
