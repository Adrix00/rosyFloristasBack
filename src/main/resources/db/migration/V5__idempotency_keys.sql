-- V5__idempotency_keys.sql
-- Soporte del header Idempotency-Key en las operaciones que mueven dinero
-- (POST de checkout y de pagos). Ver ADR-011.
--
-- No se guarda el cuerpo de la respuesta: solo el recurso creado (resource_id) y el
-- estado HTTP. Un pedido confirmado lleva PII del comprador, y guardarla aqui seria una
-- segunda copia en claro fuera de las columnas cifradas de orders (ADR-005) que ademas
-- la purga de retencion (ADR-007) tendria que perseguir. El reintento se responde
-- releyendo el recurso por su id, con el mismo control de acceso que la lectura normal.
--
-- request_fingerprint (SHA-256 del cuerpo): la misma clave con un cuerpo distinto es un
-- error del cliente, no un reintento; se rechaza en vez de devolver el resultado del
-- primer envio.
--
-- status: la fila se inserta PENDING antes de ejecutar la operacion. El UNIQUE actua de
-- cerrojo: una segunda peticion simultanea con la misma clave choca con la fila PENDING
-- y se le responde "en curso", en vez de ejecutar el cobro dos veces.
--
-- expires_at: las filas caducadas las borra una tarea de limpieza, no la base de datos.
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    endpoint VARCHAR(120) NOT NULL,
    request_fingerprint BYTEA NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_status SMALLINT,
    resource_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_keys_key_endpoint UNIQUE (idempotency_key, endpoint),
    CONSTRAINT chk_idempotency_keys_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_idempotency_keys_completed_has_response
        CHECK (status <> 'COMPLETED' OR response_status IS NOT NULL)
);

CREATE INDEX ix_idempotency_keys_expires_at ON idempotency_keys (expires_at);
