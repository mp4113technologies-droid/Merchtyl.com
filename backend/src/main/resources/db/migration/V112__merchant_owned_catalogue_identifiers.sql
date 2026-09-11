ALTER TABLE tenants ADD COLUMN identifier_prefix VARCHAR(12);

CREATE OR REPLACE FUNCTION merchant_identifier_prefix(value TEXT) RETURNS VARCHAR
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    result TEXT;
BEGIN
    SELECT string_agg(left(word, 1), '') INTO result
    FROM unnest(regexp_split_to_array(upper(regexp_replace(coalesce(value, ''), '[^A-Za-z0-9]+', ' ', 'g')), '\s+')) word
    WHERE word <> '';
    result := regexp_replace(coalesce(result, ''), '[^A-Z0-9]', '', 'g');
    IF result = '' THEN result := 'M'; END IF;
    RETURN left(result, 8);
END;
$$;

UPDATE tenants
SET identifier_prefix = merchant_identifier_prefix(display_name)
WHERE identifier_prefix IS NULL;

ALTER TABLE tenants
    ALTER COLUMN identifier_prefix SET NOT NULL,
    ADD CONSTRAINT ck_tenants_identifier_prefix CHECK (identifier_prefix ~ '^[A-Z0-9]{1,12}$');

CREATE OR REPLACE FUNCTION assign_tenant_identifier_prefix() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.identifier_prefix IS DISTINCT FROM OLD.identifier_prefix THEN
        RAISE EXCEPTION 'Tenant identifier prefix is immutable';
    END IF;
    IF NEW.identifier_prefix IS NULL THEN
        NEW.identifier_prefix := merchant_identifier_prefix(NEW.display_name);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_tenants_identifier_prefix
BEFORE INSERT OR UPDATE ON tenants
FOR EACH ROW EXECUTE FUNCTION assign_tenant_identifier_prefix();

ALTER TABLE categories ADD COLUMN tenant_id UUID REFERENCES tenants(id) ON DELETE RESTRICT;
ALTER TABLE brands ADD COLUMN tenant_id UUID REFERENCES tenants(id) ON DELETE RESTRICT;

UPDATE categories category
SET tenant_id = usage.tenant_id
FROM (
    SELECT category_id, min(tenant_id::text)::uuid tenant_id
    FROM products
    WHERE category_id IS NOT NULL AND tenant_id IS NOT NULL
    GROUP BY category_id
    HAVING count(DISTINCT tenant_id) = 1
) usage
WHERE usage.category_id = category.id;

UPDATE brands brand
SET tenant_id = usage.tenant_id
FROM (
    SELECT brand_id, min(tenant_id::text)::uuid tenant_id
    FROM products
    WHERE brand_id IS NOT NULL AND tenant_id IS NOT NULL
    GROUP BY brand_id
    HAVING count(DISTINCT tenant_id) = 1
) usage
WHERE usage.brand_id = brand.id;

ALTER TABLE categories DROP CONSTRAINT IF EXISTS uq_categories_code;
ALTER TABLE brands DROP CONSTRAINT IF EXISTS uq_brands_code;
DROP INDEX IF EXISTS uq_categories_code_lower;
DROP INDEX IF EXISTS uq_brands_code_lower;

CREATE UNIQUE INDEX uq_categories_tenant_code_lower
    ON categories(tenant_id, lower(code)) WHERE tenant_id IS NOT NULL;
CREATE UNIQUE INDEX uq_brands_tenant_code_lower
    ON brands(tenant_id, lower(code)) WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_categories_tenant ON categories(tenant_id);
CREATE INDEX idx_brands_tenant ON brands(tenant_id);

CREATE TABLE tenant_identifier_sequences (
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    namespace VARCHAR(24) NOT NULL,
    sequence_key VARCHAR(60) NOT NULL,
    next_value BIGINT NOT NULL CHECK (next_value > 0),
    PRIMARY KEY (tenant_id, namespace, sequence_key)
);

CREATE OR REPLACE FUNCTION next_tenant_identifier_sequence(
    p_tenant_id UUID,
    p_namespace VARCHAR,
    p_sequence_key VARCHAR
) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE allocated BIGINT;
BEGIN
    INSERT INTO tenant_identifier_sequences(tenant_id, namespace, sequence_key, next_value)
    VALUES (p_tenant_id, p_namespace, p_sequence_key, 2)
    ON CONFLICT (tenant_id, namespace, sequence_key)
    DO UPDATE SET next_value = tenant_identifier_sequences.next_value + 1
    RETURNING next_value - 1 INTO allocated;
    RETURN allocated;
END;
$$;

INSERT INTO tenant_identifier_sequences(tenant_id, namespace, sequence_key, next_value)
SELECT tenant_id, 'CATEGORY', '', coalesce(max(substring(code from '([0-9]+)$')::bigint), 0) + 1
FROM categories WHERE tenant_id IS NOT NULL GROUP BY tenant_id
ON CONFLICT DO NOTHING;

INSERT INTO tenant_identifier_sequences(tenant_id, namespace, sequence_key, next_value)
SELECT tenant_id, 'BRAND', '', coalesce(max(substring(code from '([0-9]+)$')::bigint), 0) + 1
FROM brands WHERE tenant_id IS NOT NULL GROUP BY tenant_id
ON CONFLICT DO NOTHING;

INSERT INTO tenant_identifier_sequences(tenant_id, namespace, sequence_key, next_value)
SELECT tenant_id, 'SKU', regexp_replace(sku, '-[0-9]{3,}$', ''),
       max(substring(sku from '([0-9]{3,})$')::bigint) + 1
FROM (
    SELECT tenant_id, upper(sku) sku FROM products WHERE tenant_id IS NOT NULL
    UNION ALL
    SELECT tenant_id, upper(sku) sku FROM product_variants WHERE tenant_id IS NOT NULL
) identifiers
WHERE sku ~ '-[0-9]{3,}$'
GROUP BY tenant_id, regexp_replace(sku, '-[0-9]{3,}$', '')
ON CONFLICT DO NOTHING;

CREATE TABLE tenant_sku_registry (
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sku_lower VARCHAR(64) NOT NULL,
    entity_type VARCHAR(16) NOT NULL CHECK (entity_type IN ('PRODUCT', 'VARIANT')),
    entity_id UUID NOT NULL,
    CONSTRAINT uq_tenant_sku_registry UNIQUE (tenant_id, sku_lower),
    CONSTRAINT uq_tenant_sku_registry_entity UNIQUE (entity_type, entity_id)
);

CREATE OR REPLACE FUNCTION maintain_tenant_sku_registry() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_kind VARCHAR;
BEGIN
    v_kind := CASE WHEN TG_TABLE_NAME = 'products' THEN 'PRODUCT' ELSE 'VARIANT' END;
    IF TG_OP = 'DELETE' THEN
        -- Keep the registry row as a tombstone so an explicitly supplied SKU cannot
        -- be reused after its Product or Variant has been deleted.
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.sku IS DISTINCT FROM OLD.sku THEN
        RAISE EXCEPTION 'SKU is immutable';
    END IF;
    IF NEW.tenant_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM tenant_sku_registry WHERE entity_type = v_kind AND entity_id = NEW.id
    ) THEN
        INSERT INTO tenant_sku_registry(tenant_id, sku_lower, entity_type, entity_id)
        VALUES (NEW.tenant_id, lower(NEW.sku), v_kind, NEW.id);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_products_sku_registry
AFTER INSERT OR UPDATE OR DELETE ON products
FOR EACH ROW EXECUTE FUNCTION maintain_tenant_sku_registry();
CREATE TRIGGER trg_product_variants_sku_registry
AFTER INSERT OR UPDATE OR DELETE ON product_variants
FOR EACH ROW EXECUTE FUNCTION maintain_tenant_sku_registry();

CREATE OR REPLACE FUNCTION immutable_catalogue_code() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.code IS DISTINCT FROM OLD.code THEN RAISE EXCEPTION 'Catalogue code is immutable'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_categories_immutable_code BEFORE UPDATE ON categories
FOR EACH ROW EXECUTE FUNCTION immutable_catalogue_code();
CREATE TRIGGER trg_brands_immutable_code BEFORE UPDATE ON brands
FOR EACH ROW EXECUTE FUNCTION immutable_catalogue_code();
