-- V8__purchase_status.sql
-- Ciclo de vida de una compra a proveedor: ORDERED -> RECEIVED -> REVERTED.
-- V1 no tenia estado; crear una compra y que generara stock de inmediato no distinguia
-- "he pedido esto" de "esto ya esta en la tienda". Ver ADR-014 y docs/features/purchasing.md.
--
-- received_at/reverted_at/revert_reason siguen el mismo patron que
-- payments.refunded_amount/refunded_at: la consistencia entre columnas es un CHECK, no
-- una columna derivada.
ALTER TABLE purchases ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ORDERED';
ALTER TABLE purchases ADD COLUMN received_at TIMESTAMPTZ;
ALTER TABLE purchases ADD COLUMN reverted_at TIMESTAMPTZ;
ALTER TABLE purchases ADD COLUMN revert_reason VARCHAR(500);

ALTER TABLE purchases ADD CONSTRAINT chk_purchases_status
    CHECK (status IN ('ORDERED', 'RECEIVED', 'REVERTED'));

-- received_at existe si y solo si la compra paso por RECEIVED en algun momento
-- (RECEIVED o REVERTED, nunca ORDERED).
ALTER TABLE purchases ADD CONSTRAINT chk_purchases_received_at_consistency
    CHECK ((status = 'ORDERED') = (received_at IS NULL));

ALTER TABLE purchases ADD CONSTRAINT chk_purchases_reverted_consistency
    CHECK ((status = 'REVERTED') = (reverted_at IS NOT NULL AND revert_reason IS NOT NULL));

-- Una factura no puede cargarse dos veces del mismo proveedor. NULL conviven sin limite
-- (varias compras sin numero de factura registrado), igual que
-- customers.email_hash con clientes archivados.
ALTER TABLE purchases ADD CONSTRAINT uq_purchases_supplier_invoice UNIQUE (supplier_id, invoice_number);
