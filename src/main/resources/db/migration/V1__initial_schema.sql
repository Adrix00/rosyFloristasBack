-- V1__initial_schema.sql
-- Esquema inicial de Rosy Floristas. Documentado en /docs/database/README.md.
-- Fuente unica de verdad ejecutable: no existe copia manual de este DDL en docs/.
--
-- Requisito de despliegue: pg_trgm y btree_gist requieren privilegios de superusuario
-- o CREATEDB/rol con permiso de CREATE EXTENSION. Si el usuario con el que corre Flyway
-- no los tiene, crear ambas extensiones manualmente antes de ejecutar esta migracion.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- =====================================================================================
-- CATALOGO
-- =====================================================================================

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(170) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    image_s3_key VARCHAR(500),
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_categories_slug UNIQUE (slug),
    CONSTRAINT chk_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- products.status: ACTIVE (visible/comprable) | INACTIVE (oculto, reversible)
-- | DISCONTINUED (baja definitiva, no vuelve al catalogo). Ver docs/database/README.md.
-- products.stock = NULL significa "sin gestion de inventario" (no "desconocido"):
-- la venta no comprueba disponibilidad y no genera stock_movements para ese producto.
-- products.stock >= 0 significa "inventario gestionado": toda venta comprueba
-- disponibilidad y todo cambio genera un movimiento (ver bloque INVENTARIO).
-- Esta distincion es un invariante de aplicacion, no lo garantiza la base de datos.
CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    description TEXT,
    price NUMERIC(10, 2) NOT NULL,
    stock INTEGER,
    status VARCHAR(20) NOT NULL,
    is_extra BOOLEAN NOT NULL DEFAULT false,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- search_text: version normalizada (minusculas, sin tildes) de nombre + descripcion +
    -- valores de texto de "attributes", rellenada por el adaptador de persistencia en Java
    -- (java.text.Normalizer). No se usa unaccent(): es STABLE y PostgreSQL 16 rechaza una
    -- columna generada que la invoque. Ver ADR-006.
    search_text TEXT,
    -- to_tsvector(regconfig, text) es IMMUTABLE (la forma de un solo argumento no lo es,
    -- porque depende de default_text_search_config): valida en columna generada STORED.
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('spanish', coalesce(search_text, ''))) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_slug UNIQUE (slug),
    CONSTRAINT chk_products_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED')),
    CONSTRAINT chk_products_price CHECK (price >= 0),
    CONSTRAINT chk_products_stock CHECK (stock IS NULL OR stock >= 0)
);

-- Full-text (no hace prefijos: plainto_tsquery('spanish','ros') NO encuentra "rosas").
CREATE INDEX ix_products_search_vector ON products USING gin (search_vector);
-- Autocompletado, prefijos y erratas: LIKE 'ros%' o similarity(search_text, 'ros').
CREATE INDEX ix_products_search_text_trgm ON products USING gin (search_text gin_trgm_ops);
-- Filtros por atributo declarado en product_attribute_definitions.
CREATE INDEX ix_products_attributes ON products USING gin (attributes jsonb_path_ops);

CREATE TABLE product_categories (
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, category_id)
);

CREATE INDEX ix_product_categories_category ON product_categories (category_id);

CREATE TABLE product_images (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    s3_key VARCHAR(500) NOT NULL,
    alt_text VARCHAR(300),
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_product_images_product ON product_images (product_id);

CREATE TABLE product_suggestions (
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    suggested_product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, suggested_product_id),
    CONSTRAINT chk_product_suggestions_not_self CHECK (product_id <> suggested_product_id)
);

CREATE INDEX ix_product_suggestions_suggested ON product_suggestions (suggested_product_id);

-- Un descuento es un precio promocional con vigencia y limite de unidades opcional.
-- original_price se guarda en la fila (no se lee de products) porque un CHECK no puede
-- consultar otra tabla, y ademas congela el "antes" que muestra la web.
-- Rango semiabierto '[)': 10:00-12:00 y 12:00-14:00 son consecutivos, no solapan;
-- 10:00-12:00 y 11:00-13:00 si solapan y el EXCLUDE los rechaza.
CREATE TABLE product_discounts (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    original_price NUMERIC(10, 2) NOT NULL,
    sale_price NUMERIC(10, 2) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    quantity_limit INTEGER,
    quantity_sold INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_product_discounts_period CHECK (starts_at < ends_at),
    CONSTRAINT chk_product_discounts_price CHECK (sale_price >= 0 AND sale_price < original_price),
    CONSTRAINT chk_product_discounts_limit CHECK (quantity_limit IS NULL OR quantity_limit > 0),
    CONSTRAINT chk_product_discounts_sold
        CHECK (quantity_sold >= 0 AND (quantity_limit IS NULL OR quantity_sold <= quantity_limit)),
    CONSTRAINT ex_product_discounts_no_overlap EXCLUDE USING gist (
        product_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    )
);

CREATE TABLE product_attribute_definitions (
    id UUID PRIMARY KEY,
    attribute_key VARCHAR(100) NOT NULL,
    label VARCHAR(150) NOT NULL,
    data_type VARCHAR(20) NOT NULL,
    filterable BOOLEAN NOT NULL DEFAULT true,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_attribute_definitions_key UNIQUE (attribute_key),
    CONSTRAINT chk_product_attribute_definitions_type CHECK (data_type IN ('TEXT', 'NUMBER', 'BOOLEAN'))
);

-- =====================================================================================
-- CLIENTES Y ACCESO
-- =====================================================================================

-- customer.type: GUEST (invitado, sin password_hash) | REGISTERED (con cuenta).
-- customer.status: ACTIVE | ARCHIVED (baja: PII a NULL, no puede autenticarse).
-- Combinaciones validas: GUEST+ACTIVE, GUEST+ARCHIVED, REGISTERED+ACTIVE, REGISTERED+ARCHIVED.
-- email_hash UNIQUE admite multiples NULL en PostgreSQL: varios clientes archivados
-- conviven y uno puede volver a registrarse con el mismo correo.
CREATE TABLE customers (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_encrypted BYTEA,
    email_hash BYTEA,
    phone_encrypted BYTEA,
    phone_hash BYTEA,
    first_name_encrypted BYTEA,
    last_name_encrypted BYTEA,
    password_hash VARCHAR(255),
    email_verified_at TIMESTAMPTZ,
    anonymized_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_customers_email_hash UNIQUE (email_hash),
    CONSTRAINT chk_customers_type CHECK (type IN ('GUEST', 'REGISTERED')),
    CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_customers_guest_no_password CHECK (type <> 'GUEST' OR password_hash IS NULL),
    CONSTRAINT chk_customers_registered_password
        CHECK (type <> 'REGISTERED' OR status = 'ARCHIVED' OR password_hash IS NOT NULL),
    CONSTRAINT chk_customers_archived_no_pii
        CHECK (status <> 'ARCHIVED' OR (password_hash IS NULL AND email_hash IS NULL)),
    CONSTRAINT chk_customers_archived_has_timestamp CHECK (status <> 'ARCHIVED' OR anonymized_at IS NOT NULL),
    CONSTRAINT chk_customers_active_has_email CHECK (status = 'ARCHIVED' OR email_hash IS NOT NULL)
);

CREATE TABLE customer_addresses (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    alias VARCHAR(100) NOT NULL,
    recipient_name_encrypted BYTEA NOT NULL,
    street_encrypted BYTEA NOT NULL,
    number_encrypted BYTEA NOT NULL,
    detail_encrypted BYTEA,
    city_encrypted BYTEA NOT NULL,
    postal_code VARCHAR(10) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_customer_addresses_customer ON customer_addresses (customer_id);
CREATE UNIQUE INDEX ux_customer_addresses_default ON customer_addresses (customer_id) WHERE is_default;

-- El PAN de la tarjeta nunca se almacena. Solo el token que devuelve la pasarela.
CREATE TABLE customer_payment_methods (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_customer_id VARCHAR(255) NOT NULL,
    payment_method_token VARCHAR(255) NOT NULL,
    card_brand VARCHAR(30) NOT NULL,
    last_four CHAR(4) NOT NULL,
    exp_month SMALLINT NOT NULL,
    exp_year SMALLINT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_payment_methods_token UNIQUE (customer_id, payment_method_token),
    CONSTRAINT chk_customer_payment_methods_month CHECK (exp_month BETWEEN 1 AND 12)
);

CREATE UNIQUE INDEX ux_customer_payment_methods_default ON customer_payment_methods (customer_id) WHERE is_default;

-- Tabla separada de customers: datos disjuntos (2FA, sin direcciones ni tarjetas ni
-- pedidos). Un fallo en el registro publico no puede crear un admin. Sin roles finos
-- por ahora (solo OWNER/ADMIN, mismas operativas); ver docs/database/README.md.
CREATE TABLE admin_users (
    id UUID PRIMARY KEY,
    email_encrypted BYTEA NOT NULL,
    email_hash BYTEA NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    totp_secret_encrypted BYTEA,
    totp_enabled BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_admin_users_email_hash UNIQUE (email_hash),
    CONSTRAINT chk_admin_users_role CHECK (role IN ('OWNER', 'ADMIN'))
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    token_hash BYTEA NOT NULL,
    customer_id UUID REFERENCES customers (id) ON DELETE CASCADE,
    admin_user_id UUID REFERENCES admin_users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_refresh_tokens_subject CHECK (num_nonnulls(customer_id, admin_user_id) = 1)
);

CREATE INDEX ix_refresh_tokens_customer ON refresh_tokens (customer_id);
CREATE INDEX ix_refresh_tokens_admin_user ON refresh_tokens (admin_user_id);

CREATE TABLE verification_tokens (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    purpose VARCHAR(30) NOT NULL,
    token_hash BYTEA NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_verification_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_verification_tokens_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
);

CREATE INDEX ix_verification_tokens_customer ON verification_tokens (customer_id);

-- =====================================================================================
-- CARRITO
-- =====================================================================================

CREATE TABLE carts (
    id UUID PRIMARY KEY,
    customer_id UUID REFERENCES customers (id) ON DELETE CASCADE,
    session_token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_carts_session_token UNIQUE (session_token)
);

CREATE INDEX ix_carts_customer ON carts (customer_id);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT chk_cart_items_quantity CHECK (quantity > 0)
);

CREATE INDEX ix_cart_items_product ON cart_items (product_id);

-- =====================================================================================
-- PEDIDOS
-- =====================================================================================

-- orders.customer_id: NULL en ventas de mostrador (STORE/INTERFLORA) y tambien cuando
-- el cliente se archiva (ON DELETE SET NULL solo si la fila del cliente se borra
-- fisicamente, lo cual no ocurre en el flujo normal). La regla "todo pedido WEB debe
-- tener customer_id al crearse" es responsabilidad del caso de uso (PlaceOrderService),
-- no de un CHECK: es una regla de creacion, no un invariante permanente de la fila.
--
-- Snapshot del comprador: mismo criterio que product_name en order_items. El pedido es
-- un documento autocontenido y sobrevive a la baja del cliente. buyer_email_hash y
-- buyer_phone_hash permiten localizar el pedido en el panel mientras dure la retencion.
--
-- retention_until / personal_data_purged_at: la purga de PII la ejecuta
-- PurgeExpiredOrderPersonalDataService (fuera de esta migracion). Ningun plazo de
-- retencion se fija aqui: lo determina el requisito legal aplicable
-- (app.retention.orders-period).
--
-- Los pedidos NO se eliminan en ningun caso de uso normal. Los ON DELETE CASCADE de
-- order_items / order_deliveries / order_status_history solo protegen la integridad
-- referencial ante un borrado excepcional/administrativo, no forman parte del ciclo de
-- vida habitual.
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(30) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    fulfillment VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    customer_id UUID REFERENCES customers (id) ON DELETE SET NULL,
    buyer_first_name_encrypted BYTEA,
    buyer_last_name_encrypted BYTEA,
    buyer_email_encrypted BYTEA,
    buyer_email_hash BYTEA,
    buyer_phone_encrypted BYTEA,
    buyer_phone_hash BYTEA,
    subtotal NUMERIC(10, 2) NOT NULL,
    shipping_cost NUMERIC(10, 2) NOT NULL DEFAULT 0,
    total NUMERIC(10, 2) GENERATED ALWAYS AS (subtotal + shipping_cost) STORED,
    placed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    retention_until DATE,
    personal_data_purged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    CONSTRAINT chk_orders_channel CHECK (channel IN ('WEB', 'STORE', 'INTERFLORA')),
    CONSTRAINT chk_orders_fulfillment CHECK (fulfillment IN ('DELIVERY', 'PICKUP', 'NONE')),
    CONSTRAINT chk_orders_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'PREPARING', 'OUT_FOR_DELIVERY', 'DELIVERED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT chk_orders_subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_orders_shipping_cost CHECK (shipping_cost >= 0),
    CONSTRAINT chk_orders_pickup_no_shipping CHECK (fulfillment <> 'PICKUP' OR shipping_cost = 0),
    CONSTRAINT chk_orders_none_no_shipping CHECK (fulfillment <> 'NONE' OR shipping_cost = 0)
);

CREATE INDEX ix_orders_customer ON orders (customer_id);
CREATE INDEX ix_orders_placed_at_channel ON orders (placed_at, channel);
CREATE INDEX ix_orders_buyer_email_hash ON orders (buyer_email_hash);
CREATE INDEX ix_orders_buyer_phone_hash ON orders (buyer_phone_hash);
CREATE INDEX ix_orders_retention_pending_purge ON orders (retention_until) WHERE personal_data_purged_at IS NULL;

-- Snapshot historico: product_name y product_attributes se copian del producto en el
-- momento de la venta para que el pedido siga siendo interpretable aunque el producto
-- cambie de nombre, cambie de atributos, se desactive o se de de baja.
-- line_total generada: imposible de desincronizar de unit_price/discount_price/quantity.
CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    product_name VARCHAR(200) NOT NULL,
    product_attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    unit_price NUMERIC(10, 2) NOT NULL,
    discount_price NUMERIC(10, 2),
    quantity INTEGER NOT NULL,
    line_total NUMERIC(10, 2) GENERATED ALWAYS AS (COALESCE(discount_price, unit_price) * quantity) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_order_items_discount_price
        CHECK (discount_price IS NULL OR (discount_price >= 0 AND discount_price < unit_price))
);

CREATE INDEX ix_order_items_order ON order_items (order_id);
CREATE INDEX ix_order_items_product ON order_items (product_id);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    changed_by_admin_id UUID REFERENCES admin_users (id) ON DELETE SET NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_order_status_history_order ON order_status_history (order_id);

-- 1:1 con orders. Vale tanto para DELIVERY (todos los campos) como para PICKUP (solo
-- delivery_date/slot_from/slot_to, sin direccion). No existe fila para fulfillment=NONE.
-- latitude/longitude cifradas: dejarlas en claro identifica el portal exacto y anula el
-- cifrado de la calle. postal_code y distance_km en claro: ver docs/database/README.md.
CREATE TABLE order_deliveries (
    order_id UUID PRIMARY KEY REFERENCES orders (id) ON DELETE CASCADE,
    recipient_name_encrypted BYTEA,
    recipient_phone_encrypted BYTEA,
    street_encrypted BYTEA,
    number_encrypted BYTEA,
    detail_encrypted BYTEA,
    postal_code VARCHAR(10),
    latitude_encrypted BYTEA,
    longitude_encrypted BYTEA,
    distance_km NUMERIC(5, 2),
    delivery_date DATE NOT NULL,
    slot_from TIME NOT NULL,
    slot_to TIME NOT NULL,
    card_message_encrypted BYTEA,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_order_deliveries_distance CHECK (distance_km IS NULL OR distance_km >= 0),
    CONSTRAINT chk_order_deliveries_slot CHECK (slot_from < slot_to)
);

-- Tarifas por distancia en linea recta (haversine, calculado en Java al geocodificar).
-- Rango semiabierto: tramos consecutivos no se consideran solapados.
CREATE TABLE shipping_rates (
    id UUID PRIMARY KEY,
    min_km NUMERIC(5, 2) NOT NULL,
    max_km NUMERIC(5, 2) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    free_from_amount NUMERIC(10, 2),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_shipping_rates_range CHECK (min_km >= 0 AND max_km > min_km),
    CONSTRAINT chk_shipping_rates_price CHECK (price >= 0),
    CONSTRAINT chk_shipping_rates_free_from CHECK (free_from_amount IS NULL OR free_from_amount > 0),
    CONSTRAINT ex_shipping_rates_no_overlap EXCLUDE USING gist (
        numrange(min_km, max_km, '[)') WITH &&
    ) WHERE (active)
);

-- Mas de 10 km no se reparte a domicilio: lo rechaza la aplicacion, no hay fila para ese caso.
INSERT INTO shipping_rates (id, min_km, max_km, price, free_from_amount, active) VALUES
    ('00000000-0000-0000-0000-000000000001', 0, 1, 5.00, 50.00, true),
    ('00000000-0000-0000-0000-000000000002', 1, 5, 8.00, 70.00, true),
    ('00000000-0000-0000-0000-000000000003', 5, 10, 10.00, 90.00, true);

-- =====================================================================================
-- PAGOS
-- =====================================================================================

-- Cardinalidad 1:N con orders: cada intento de pago es una fila (util para soporte ante
-- fallos). Un pago que llega a CAPTURED permanece CAPTURED para siempre: el reembolso NO
-- cambia el estado, se expresa con refunded_amount/refunded_at. Un reembolso parcial
-- tambien deja el pago en CAPTURED. Son dos ejes distintos: el estado describe en que
-- punto acabo el intento; refunded_amount describe cuanto se devolvio despues.
-- El indice unico parcial sobre (order_id) WHERE status='CAPTURED' expresa "un pedido
-- solo puede tener un cobro CAPTURED en toda su historia". PENDING/AUTHORIZED/FAILED/
-- CANCELED conviven sin limite (reintentos).
-- El PAN de la tarjeta nunca se almacena, en ninguna forma.
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    method VARCHAR(20) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_payment_intent_id VARCHAR(255),
    provider_reference VARCHAR(255),
    amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    refunded_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    authorized_at TIMESTAMPTZ,
    captured_at TIMESTAMPTZ,
    canceled_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payments_method CHECK (method IN ('CARD_ONLINE', 'CASH', 'DATAPHONE')),
    CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'AUTHORIZED', 'CAPTURED', 'CANCELED', 'FAILED')),
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_refunded_amount CHECK (refunded_amount >= 0),
    CONSTRAINT chk_payments_refunded_le_amount CHECK (refunded_amount <= amount),
    CONSTRAINT chk_payments_refund_requires_captured CHECK (refunded_amount = 0 OR status = 'CAPTURED'),
    CONSTRAINT chk_payments_refund_at_consistency CHECK ((refunded_amount = 0) = (refunded_at IS NULL)),
    CONSTRAINT uq_payments_provider_reference UNIQUE (provider, provider_reference)
);

CREATE INDEX ix_payments_order ON payments (order_id);
CREATE UNIQUE INDEX ux_payments_captured_per_order ON payments (order_id) WHERE status = 'CAPTURED';

-- =====================================================================================
-- INVENTARIO Y COMPRAS
-- =====================================================================================

-- Todo stock gestionado nace de un movimiento INITIAL (unico por producto, ver indice
-- abajo). El resto de correcciones son ADJUSTMENT. resulting_stock es un dato de
-- auditoria: la base de datos NO puede garantizar por si sola que coincida con
-- products.stock sin trigger, y no se introduce ninguno (ver docs/database/README.md,
-- consultas de reconciliacion). El unico camino de escritura es el caso de uso
-- (RegisterStockMovementService, transaccional), que hace el UPDATE condicional sobre
-- products.stock y el INSERT del movimiento en la misma transaccion.
CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    type VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    resulting_stock INTEGER NOT NULL,
    admin_user_id UUID REFERENCES admin_users (id) ON DELETE SET NULL,
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_stock_movements_type CHECK (type IN ('INITIAL', 'PURCHASE', 'SALE', 'WASTE', 'ADJUSTMENT')),
    CONSTRAINT chk_stock_movements_quantity_nonzero CHECK (quantity <> 0 OR type = 'INITIAL'),
    CONSTRAINT chk_stock_movements_initial_sign CHECK (type <> 'INITIAL' OR quantity >= 0),
    CONSTRAINT chk_stock_movements_purchase_sign CHECK (type <> 'PURCHASE' OR quantity > 0),
    CONSTRAINT chk_stock_movements_sale_sign CHECK (type <> 'SALE' OR quantity < 0),
    CONSTRAINT chk_stock_movements_waste_sign CHECK (type <> 'WASTE' OR quantity < 0),
    CONSTRAINT chk_stock_movements_resulting_stock CHECK (resulting_stock >= 0)
);

CREATE INDEX ix_stock_movements_product ON stock_movements (product_id);
CREATE UNIQUE INDEX ux_stock_movements_initial ON stock_movements (product_id) WHERE type = 'INITIAL';

-- Datos de contacto B2B, no PII de consumidor: sin cifrar (a diferencia de customers).
CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    contact_name VARCHAR(150),
    phone VARCHAR(30),
    email VARCHAR(255),
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- invoice_total es el importe del documento del proveedor (puede incluir portes,
-- impuestos u otros conceptos ausentes de purchase_items). NO es SUM(purchase_items.line_total)
-- y no se declara GENERATED: son dos cifras distintas por diseno, no un dato redundante.
CREATE TABLE purchases (
    id UUID PRIMARY KEY,
    supplier_id UUID NOT NULL REFERENCES suppliers (id) ON DELETE RESTRICT,
    purchase_date DATE NOT NULL,
    invoice_number VARCHAR(100),
    invoice_total NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_purchases_invoice_total CHECK (invoice_total >= 0)
);

CREATE INDEX ix_purchases_supplier ON purchases (supplier_id);

-- product_id nullable: NULL para material a granel (flor suelta, cinta, papel) que
-- nunca se vende como producto del catalogo; con product_id, la compra puede alimentar
-- stock_movements de tipo PURCHASE. quantity en NUMERIC porque el material a granel
-- puede comprarse por peso o longitud, no solo por unidades enteras.
CREATE TABLE purchase_items (
    id UUID PRIMARY KEY,
    purchase_id UUID NOT NULL REFERENCES purchases (id) ON DELETE CASCADE,
    product_id UUID REFERENCES products (id) ON DELETE RESTRICT,
    description VARCHAR(300) NOT NULL,
    quantity NUMERIC(10, 2) NOT NULL,
    unit_cost NUMERIC(10, 2) NOT NULL,
    line_total NUMERIC(10, 2) GENERATED ALWAYS AS (quantity * unit_cost) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_purchase_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_purchase_items_unit_cost CHECK (unit_cost >= 0)
);

CREATE INDEX ix_purchase_items_purchase ON purchase_items (purchase_id);
CREATE INDEX ix_purchase_items_product ON purchase_items (product_id);
