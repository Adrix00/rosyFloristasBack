-- V3__optimistic_locking.sql
-- Anade control de concurrencia optimista (JPA @Version) a las raices de agregado con
-- escritura concurrente plausible desde el panel admin o desde flujos paralelos de
-- cliente. Ver ADR-009.
--
-- No se anade a customer_addresses, order_items, order_deliveries, product_images ni
-- otras entidades hijas: se gestionan siempre a traves de su raiz de agregado
-- (products, orders, customers), que ya lleva su propio version.
-- No sustituye el UPDATE condicional de products.stock ni product_discounts.quantity_sold
-- (chk/EXCLUDE ya cubren esos casos, ver docs/database/README.md): son mecanismos para
-- problemas distintos y coexisten en la misma fila.
--
-- DEFAULT 0 sin backfill especial: tablas sin filas en produccion todavia.
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE customer_payment_methods ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE admin_users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
