-- V6__order_item_discount.sql
-- Enlaza cada linea de pedido con el descuento que se le aplico.
--
-- V1 guardaba discount_price pero no de que descuento venia. Sin ese dato, devolver las
-- unidades a product_discounts.quantity_sold al cancelar un pedido (comportamiento que
-- docs/database/README.md ya describia) no es fiable: el descuento pudo terminar, y un
-- mismo producto puede haber tenido varias promociones consecutivas. Buscarlo por
-- producto y fecha obligaria a acertar el rango sobre filas que ya no existen.
--
-- ON DELETE SET NULL: una promocion borrada no puede llevarse por delante una linea de
-- pedido, que es un registro contable. En la practica el borrado casi no ocurre (las
-- promociones se cierran poniendo ends_at, no se borran) y product_discounts solo se
-- borra en cascada al borrar el producto, que RESTRICT ya impide si tiene ventas.
--
-- El CHECK es unidireccional a proposito: con discount_id hay siempre discount_price,
-- pero lo contrario no se exige, porque un SET NULL deja discount_price sin su origen.
ALTER TABLE order_items ADD COLUMN discount_id UUID REFERENCES product_discounts (id) ON DELETE SET NULL;

ALTER TABLE order_items ADD CONSTRAINT chk_order_items_discount_consistency
    CHECK (discount_id IS NULL OR discount_price IS NOT NULL);

CREATE INDEX ix_order_items_discount ON order_items (discount_id);
