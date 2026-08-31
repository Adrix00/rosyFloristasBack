-- V14__order_status_reason.sql
-- order.md pide `reason` obligatorio cuando un administrador rechaza o cancela un
-- pedido, y dice que ese motivo "es lo que vera el equipo y, potencialmente, el propio
-- cliente en una comunicacion". No habia donde guardarlo: order_status_history solo
-- registraba estado, quien y cuando.
--
-- Sale a la luz al escribir notification.md, donde ese motivo pasa de "potencialmente"
-- a ser el cuerpo del correo que recibe el cliente (ORDER_REJECTED, ORDER_CANCELLED).
--
-- Nullable: la mayoria de transiciones no llevan motivo (aceptar, preparar, enviar,
-- entregar), y un cliente que cancela su propio pedido tampoco tiene que justificarse.
ALTER TABLE order_status_history ADD COLUMN reason VARCHAR(500);

-- changed_by_admin_id NULL significa que la transicion la hizo el cliente: solo puede
-- ser una cancelacion desde PENDING (ver order.md, regla 3.9), y esa no exige motivo.
-- Con administrador detras, rechazar y cancelar si lo exigen.
ALTER TABLE order_status_history ADD CONSTRAINT chk_order_status_history_reason_required
    CHECK (
        status NOT IN ('REJECTED', 'CANCELLED')
        OR changed_by_admin_id IS NULL
        OR reason IS NOT NULL
    );
