-- V13__images_registry.sql
-- V1 guardaba la clave de S3 como texto suelto en dos sitios (product_images.s3_key y
-- categories.image_s3_key), sin nada que dijera de donde salio esa clave ni que
-- permitiera saber que ficheros de S3 ya no usa nadie. product.md y category.md dicen
-- que una clave enviada por el cliente "se verifica que corresponda a un objeto subido
-- por ese flujo", y esta tabla es lo que hace verificable esa frase.
--
-- Ver docs/features/image.md.

CREATE TABLE images (
    id UUID PRIMARY KEY,
    s3_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    -- La imagen sobrevive al administrador que la subio, igual que stock_movements
    -- y audit_log.
    uploaded_by_admin_id UUID REFERENCES admin_users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_images_s3_key UNIQUE (s3_key),
    -- El tipo real se comprueba leyendo los bytes de cabecera en la subida, no la
    -- extension del nombre. Este CHECK es la ultima barrera, no la primera.
    CONSTRAINT chk_images_content_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_images_byte_size CHECK (byte_size > 0),
    CONSTRAINT chk_images_dimensions CHECK (width > 0 AND height > 0)
);

CREATE INDEX ix_images_created_at ON images (created_at);

-- product_images pasa de guardar la clave a referenciar la fila. NOT NULL sin backfill
-- por el mismo motivo que family_id en V2: la tabla no tiene filas en produccion
-- todavia. alt_text y position se quedan donde estan: son de este uso concreto de la
-- imagen, no de la imagen.
ALTER TABLE product_images ADD COLUMN image_id UUID NOT NULL REFERENCES images (id) ON DELETE RESTRICT;
ALTER TABLE product_images DROP COLUMN s3_key;

-- La misma imagen dos veces en la galeria de un producto no significa nada.
ALTER TABLE product_images ADD CONSTRAINT uq_product_images_product_image UNIQUE (product_id, image_id);

CREATE INDEX ix_product_images_image ON product_images (image_id);

ALTER TABLE categories ADD COLUMN image_id UUID REFERENCES images (id) ON DELETE RESTRICT;
ALTER TABLE categories DROP COLUMN image_s3_key;

CREATE INDEX ix_categories_image ON categories (image_id);

-- RESTRICT en ambas, y no por prudencia generica: es la base de datos garantizando que
-- solo se puede borrar del todo una imagen que ya no usa nadie. El caso de uso de
-- borrado no necesita comprobarlo por su cuenta, y no hay ventana entre la comprobacion
-- y el DELETE en la que alguien pudiera asociarla.
