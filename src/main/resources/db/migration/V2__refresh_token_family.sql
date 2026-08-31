-- V2__refresh_token_family.sql
-- Agrupa los refresh_tokens de un mismo login bajo family_id, para poder revocar toda
-- la cadena cuando se detecta el reuso de un refresh token ya rotado. Ver ADR-008.
--
-- family_id es NOT NULL sin backfill manual: la tabla no tiene filas en producción
-- todavia (V1 aun no se ha desplegado con trafico real), asi que el ALTER se hace en
-- un solo paso. Si en el futuro existieran filas previas a esta migracion, un backfill
-- con gen_random_uuid() por fila seria seguro (una familia de un solo token por fila
-- huerfana), pero no es necesario aqui.
--
-- expires_at (columna ya existente) NO cambia de significado: sigue siendo la
-- expiracion de ESTA fila. La vida maxima de la familia (12h admin / 30 dias cliente)
-- es un invariante de aplicacion, no de base de datos: el caso de uso que rota el
-- token copia el expires_at original de la familia en cada fila nueva, en vez de
-- extenderlo. Un CHECK no puede comparar contra otras filas de la misma tabla, y
-- este proyecto ya evita triggers de sincronizacion (ver products.stock en
-- docs/database/README.md), asi que el mismo criterio aplica aqui.
ALTER TABLE refresh_tokens ADD COLUMN family_id UUID NOT NULL;

CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
