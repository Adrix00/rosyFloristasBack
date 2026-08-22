-- V4__audit_log.sql
-- Registro de acciones administrativas que el esquema V1 no cubria ya.
-- order_status_history y stock_movements siguen siendo la fuente de verdad de sus
-- respectivos cambios (llevan admin_user_id propio) y NO se duplican aqui.
-- Ver ADR-010.
--
-- admin_user_id ON DELETE SET NULL: el registro de auditoria sobrevive a la baja del
-- administrador, mismo criterio que order_status_history.changed_by_admin_id.
--
-- changed_fields: nombres de los campos modificados. Se rellena siempre.
--
-- changes: valores "antes/despues", SOLO para entidades sin datos personales.
-- En una entidad con PII (customers, orders, order_deliveries, customer_addresses,
-- customer_payment_methods, admin_users) guardar los valores dejaria en claro, en esta
-- tabla, lo que ADR-005 cifra en la tabla de origen, y obligaria a la purga de
-- retencion de ADR-007 a perseguir PII dentro de JSONB arbitrario. Para esas entidades
-- solo se registra que campos cambiaron, nunca su contenido.
-- La lista blanca vive en el CHECK y no solo en el codigo a proposito: una entidad
-- nueva no entra aqui por descuido, hay que anadirla explicitamente en una migracion.
CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    admin_user_id UUID REFERENCES admin_users (id) ON DELETE SET NULL,
    action VARCHAR(30) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id UUID,
    changed_fields TEXT[] NOT NULL DEFAULT '{}',
    changes JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_log_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGIN_FAILED')),
    CONSTRAINT chk_audit_log_changes_pii_free CHECK (
        changes IS NULL OR entity_type IN (
            'product',
            'category',
            'product_discount',
            'product_image',
            'product_suggestion',
            'product_attribute_definition',
            'shipping_rate',
            'supplier',
            'purchase',
            'purchase_item'
        )
    )
);

CREATE INDEX ix_audit_log_admin_user ON audit_log (admin_user_id);
CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_log_created_at ON audit_log (created_at);
