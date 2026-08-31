-- V7__inventory_alerts.sql
-- Umbral de stock bajo por producto, y tabla de alertas de inventario generada por una
-- tarea diaria. Cubre dos discrepancias distintas bajo un mismo mecanismo de historial:
-- LOW_STOCK (negocio: repone antes de agotarse) y RECONCILIATION_MISMATCH (integridad:
-- products.stock no coincide con la suma de sus movimientos, senal de un bug). Ver
-- ADR-013 y docs/features/inventory.md.

-- NULL significa "sin alerta configurada", igual que products.stock = NULL significa
-- "sin gestion de inventario". Solo tiene sentido cuando el producto lleva inventario
-- gestionado; no se impone con CHECK porque un CHECK no puede expresar "solo si otra
-- columna es NOT NULL" de forma legible, y es el mismo tipo de invariante de aplicacion
-- que ya documenta products.stock.
ALTER TABLE products ADD COLUMN low_stock_threshold INTEGER;
ALTER TABLE products ADD CONSTRAINT chk_products_low_stock_threshold
    CHECK (low_stock_threshold IS NULL OR low_stock_threshold >= 0);

-- observed_value / expected_value son genericos a proposito, para no repetir la tabla
-- por tipo de alerta: en LOW_STOCK son (stock actual, umbral); en
-- RECONCILIATION_MISMATCH son (products.stock, suma de stock_movements).
--
-- El indice unico parcial impide que la tarea diaria duplique una alerta ya abierta
-- para el mismo producto y tipo: si ya hay una OPEN, el dia siguiente no crea otra,
-- solo la deja donde esta. Cerrar la alerta (resolved o dismissed) libera el hueco.
CREATE TABLE inventory_alerts (
    id UUID PRIMARY KEY,
    type VARCHAR(30) NOT NULL,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    observed_value INTEGER NOT NULL,
    expected_value INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_by_admin_id UUID REFERENCES admin_users (id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_inventory_alerts_type CHECK (type IN ('LOW_STOCK', 'RECONCILIATION_MISMATCH')),
    CONSTRAINT chk_inventory_alerts_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT chk_inventory_alerts_resolved_consistency
        CHECK ((status = 'OPEN') = (resolved_at IS NULL))
);

CREATE UNIQUE INDEX ux_inventory_alerts_open ON inventory_alerts (product_id, type) WHERE status = 'OPEN';
CREATE INDEX ix_inventory_alerts_product ON inventory_alerts (product_id);
CREATE INDEX ix_inventory_alerts_status ON inventory_alerts (status);
