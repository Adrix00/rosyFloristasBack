-- V10__payment_interflora_and_refund_reason.sql
-- Anade el metodo de pago INTERFLORA: los pedidos de esa red se facturan a mes vencido,
-- no se cobran el dia del pedido. Se registran igualmente como CAPTURED al crearse (la
-- red garantiza el pago), sin llamada a pasarela -- mismo patron que CASH/DATAPHONE,
-- capturados directamente con provider='MANUAL'. Ver docs/features/payment.md, seccion 3.6.
ALTER TABLE payments DROP CONSTRAINT chk_payments_method;
ALTER TABLE payments ADD CONSTRAINT chk_payments_method
    CHECK (method IN ('CARD_ONLINE', 'CASH', 'DATAPHONE', 'INTERFLORA'));

-- Motivo del reembolso manual (POST /orders/{id}/refund, ADMIN). Los reembolsos
-- automaticos (REJECTED/CANCELLED) no lo necesitan: el motivo ya es la propia transicion
-- de estado del pedido. Mismo patron que purchases.revert_reason (V8) y
-- stock_movements.note.
ALTER TABLE payments ADD COLUMN refund_reason VARCHAR(500);

ALTER TABLE payments ADD CONSTRAINT chk_payments_refund_reason_consistency
    CHECK (refund_reason IS NULL OR refunded_amount > 0);
