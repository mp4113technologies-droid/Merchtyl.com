CREATE TABLE merchant_code_allocations (
    code_base VARCHAR(3) PRIMARY KEY,
    next_value BIGINT NOT NULL CHECK (next_value > 0)
);

CREATE OR REPLACE FUNCTION merchant_code_base(value TEXT) RETURNS VARCHAR
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE normalized TEXT;
BEGIN
    normalized := upper(translate(coalesce(value, ''),
        'ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝÆŒ',
        'AAAAAACEEEEIIIINOOOOOOUUUUYAO'));
    normalized := regexp_replace(normalized, '[^A-Z0-9]', '', 'g');
    IF normalized = '' THEN normalized := 'MER'; END IF;
    RETURN rpad(left(normalized, 3), 3, 'X');
END;
$$;

CREATE OR REPLACE FUNCTION next_merchant_code(value TEXT) RETURNS VARCHAR
LANGUAGE plpgsql AS $$
DECLARE base VARCHAR(3); allocated BIGINT;
BEGIN
    base := merchant_code_base(value);
    INSERT INTO merchant_code_allocations(code_base, next_value) VALUES (base, 2)
    ON CONFLICT (code_base) DO UPDATE SET next_value = merchant_code_allocations.next_value + 1
    RETURNING next_value - 1 INTO allocated;
    RETURN base || lpad(allocated::text, 2, '0');
END;
$$;

ALTER TABLE tenants ADD COLUMN merchant_code VARCHAR(16);

DO $$
DECLARE tenant RECORD;
BEGIN
    FOR tenant IN SELECT id, display_name FROM tenants ORDER BY created_at, id LOOP
        UPDATE tenants SET merchant_code = next_merchant_code(tenant.display_name) WHERE id = tenant.id;
    END LOOP;
END;
$$;

ALTER TABLE tenants
    ALTER COLUMN merchant_code SET NOT NULL,
    ADD CONSTRAINT uq_tenants_merchant_code UNIQUE (merchant_code),
    ADD CONSTRAINT ck_tenants_merchant_code CHECK (merchant_code ~ '^[A-Z0-9]{3}[0-9]{2,}$');

CREATE OR REPLACE FUNCTION assign_immutable_merchant_code() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.merchant_code IS DISTINCT FROM OLD.merchant_code THEN
        RAISE EXCEPTION 'Merchant code is immutable';
    END IF;
    IF NEW.merchant_code IS NULL THEN
        NEW.merchant_code := next_merchant_code(NEW.display_name);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_tenants_merchant_code
BEFORE INSERT OR UPDATE ON tenants
FOR EACH ROW EXECUTE FUNCTION assign_immutable_merchant_code();

ALTER TABLE categories ADD COLUMN system_type VARCHAR(32);
UPDATE categories SET system_type = 'LOTTERY'
WHERE system_managed = TRUE AND lower(code) = 'lottery';

DROP TRIGGER trg_categories_immutable_code ON categories;
DROP TRIGGER trg_brands_immutable_code ON brands;
DROP TRIGGER trg_products_sku_registry ON products;
DROP TRIGGER trg_product_variants_sku_registry ON product_variants;
ALTER TABLE products DROP CONSTRAINT chk_products_reference_format;

WITH numbered AS (
    SELECT category.id, tenant.merchant_code || '-CAT-' ||
           lpad(row_number() OVER (PARTITION BY category.tenant_id ORDER BY category.created_at, category.id)::text, 3, '0') new_code
    FROM categories category JOIN tenants tenant ON tenant.id = category.tenant_id
)
UPDATE categories category SET code = numbered.new_code, updated_at = now(), version = version + 1
FROM numbered WHERE numbered.id = category.id;

WITH numbered AS (
    SELECT brand.id, tenant.merchant_code || '-BR-' ||
           lpad(row_number() OVER (PARTITION BY brand.tenant_id ORDER BY brand.created_at, brand.id)::text, 3, '0') new_code
    FROM brands brand JOIN tenants tenant ON tenant.id = brand.tenant_id
)
UPDATE brands brand SET code = numbered.new_code, updated_at = now(), version = version + 1
FROM numbered WHERE numbered.id = brand.id;

WITH numbered AS (
    SELECT product.id, tenant.merchant_code || '-PRD-' ||
           lpad(row_number() OVER (PARTITION BY product.tenant_id ORDER BY product.created_at, product.id)::text, 6, '0') new_reference
    FROM products product JOIN tenants tenant ON tenant.id = product.tenant_id
)
UPDATE products product SET product_reference = numbered.new_reference
FROM numbered WHERE numbered.id = product.id;

CREATE OR REPLACE FUNCTION migrated_sku_base(product_name TEXT, variant_name TEXT) RETURNS VARCHAR
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE product_part TEXT; variant_part TEXT;
BEGIN
    product_part := regexp_replace(upper(translate(coalesce(product_name, ''),
        'ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝÆŒ',
        'AAAAAACEEEEIIIINOOOOOOUUUUYAO')), '[^A-Z0-9]+', '-', 'g');
    product_part := btrim(product_part, '-');
    variant_part := regexp_replace(upper(translate(coalesce(variant_name, ''),
        'ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝÆŒ',
        'AAAAAACEEEEIIIINOOOOOOUUUUYAO')), '[^A-Z0-9]+', '-', 'g');
    variant_part := regexp_replace(btrim(variant_part, '-'), '([0-9])-([A-Z])', '\1\2', 'g');
    IF product_part = '' THEN product_part := 'ITEM'; END IF;
    RETURN left(CASE WHEN variant_part = '' THEN product_part ELSE product_part || '-' || variant_part END, 60);
END;
$$;

DELETE FROM tenant_sku_registry;
CREATE TEMPORARY TABLE migrated_sku_assignments ON COMMIT DROP AS
WITH identifiers AS (
    SELECT product.id, product.tenant_id, 'PRODUCT' entity_type,
           tenant.merchant_code, migrated_sku_base(product.name, NULL) raw_base,
           product.created_at
    FROM products product JOIN tenants tenant ON tenant.id = product.tenant_id
    UNION ALL
    SELECT variant.id, variant.tenant_id, 'VARIANT', tenant.merchant_code,
           migrated_sku_base(product.name, variant.name), variant.created_at
    FROM product_variants variant JOIN products product ON product.id = variant.product_id
    JOIN tenants tenant ON tenant.id = variant.tenant_id
), numbered AS (
    SELECT *, left(merchant_code || '-' || raw_base, 60) base,
           row_number() OVER (PARTITION BY tenant_id, raw_base ORDER BY created_at, id) sequence
    FROM identifiers
)
SELECT id, entity_type, base || '-' || lpad(sequence::text, 3, '0') new_sku FROM numbered;

UPDATE products product SET sku = assigned.new_sku
FROM migrated_sku_assignments assigned
WHERE assigned.entity_type = 'PRODUCT' AND assigned.id = product.id;

UPDATE product_variants variant SET sku = assigned.new_sku
FROM migrated_sku_assignments assigned
WHERE assigned.entity_type = 'VARIANT' AND assigned.id = variant.id;

INSERT INTO tenant_sku_registry(tenant_id, sku_lower, entity_type, entity_id)
SELECT tenant_id, lower(sku), 'PRODUCT', id FROM products WHERE tenant_id IS NOT NULL
UNION ALL
SELECT tenant_id, lower(sku), 'VARIANT', id FROM product_variants WHERE tenant_id IS NOT NULL;

TRUNCATE tenant_identifier_sequences;
INSERT INTO tenant_identifier_sequences(tenant_id, namespace, sequence_key, next_value)
SELECT tenant_id, 'CATEGORY', '', count(*) + 1 FROM categories WHERE tenant_id IS NOT NULL GROUP BY tenant_id;
INSERT INTO tenant_identifier_sequences(tenant_id, namespace, sequence_key, next_value)
SELECT tenant_id, 'BRAND', '', count(*) + 1 FROM brands WHERE tenant_id IS NOT NULL GROUP BY tenant_id;
INSERT INTO tenant_identifier_sequences(tenant_id, namespace, sequence_key, next_value)
SELECT tenant_id, 'SKU', regexp_replace(sku, '-[0-9]{3,}$', ''),
       max(substring(sku from '([0-9]{3,})$')::bigint) + 1
FROM (
    SELECT tenant_id, upper(sku) sku FROM products WHERE tenant_id IS NOT NULL
    UNION ALL SELECT tenant_id, upper(sku) FROM product_variants WHERE tenant_id IS NOT NULL
) identifiers GROUP BY tenant_id, regexp_replace(sku, '-[0-9]{3,}$', '');

TRUNCATE tenant_product_reference_sequences;
INSERT INTO tenant_product_reference_sequences(tenant_id, next_value)
SELECT tenant_id, count(*) + 1 FROM products WHERE tenant_id IS NOT NULL GROUP BY tenant_id;

CREATE TRIGGER trg_categories_immutable_code BEFORE UPDATE ON categories
FOR EACH ROW EXECUTE FUNCTION immutable_catalogue_code();
CREATE TRIGGER trg_brands_immutable_code BEFORE UPDATE ON brands
FOR EACH ROW EXECUTE FUNCTION immutable_catalogue_code();
CREATE TRIGGER trg_products_sku_registry AFTER INSERT OR UPDATE OR DELETE ON products
FOR EACH ROW EXECUTE FUNCTION maintain_tenant_sku_registry();
CREATE TRIGGER trg_product_variants_sku_registry AFTER INSERT OR UPDATE OR DELETE ON product_variants
FOR EACH ROW EXECUTE FUNCTION maintain_tenant_sku_registry();

CREATE UNIQUE INDEX uq_categories_tenant_system_type
    ON categories(tenant_id, system_type) WHERE system_type IS NOT NULL;
CREATE UNIQUE INDEX uq_categories_generated_code_global
    ON categories(lower(code)) WHERE code ~ '^[A-Z0-9]{3}[0-9]{2,}-CAT-[0-9]{3,}$';
CREATE UNIQUE INDEX uq_brands_generated_code_global
    ON brands(lower(code)) WHERE code ~ '^[A-Z0-9]{3}[0-9]{2,}-BR-[0-9]{3,}$';
CREATE UNIQUE INDEX uq_generated_sku_registry_global
    ON tenant_sku_registry(sku_lower) WHERE sku_lower ~ '^[a-z0-9]{3}[0-9]{2,}-.+$';

CREATE OR REPLACE FUNCTION provision_default_lottery_category() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE allocated BIGINT;
BEGIN
    allocated := next_tenant_identifier_sequence(NEW.id, 'CATEGORY', '');
    INSERT INTO categories (id, tenant_id, code, name, description, active, system_managed, system_type,
                            created_at, updated_at, version)
    VALUES (gen_random_uuid(), NEW.id, NEW.merchant_code || '-CAT-' || lpad(allocated::text, 3, '0'),
            'Lottery', 'System category for lottery products.', TRUE, TRUE, 'LOTTERY', now(), now(), 0)
    ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION next_product_reference(p_tenant_id UUID) RETURNS VARCHAR
LANGUAGE plpgsql AS $$
DECLARE allocated BIGINT; namespace VARCHAR;
BEGIN
    INSERT INTO tenant_product_reference_sequences(tenant_id, next_value) VALUES (p_tenant_id, 2)
    ON CONFLICT (tenant_id) DO UPDATE SET next_value = tenant_product_reference_sequences.next_value + 1
    RETURNING next_value - 1 INTO allocated;
    SELECT merchant_code INTO STRICT namespace FROM tenants WHERE id = p_tenant_id;
    RETURN namespace || '-PRD-' || lpad(allocated::text, 6, '0');
END;
$$;

ALTER TABLE products ADD CONSTRAINT chk_products_reference_format CHECK (
    product_reference ~ '^PRD-[0-9]{6,}$' OR
    product_reference ~ '^[A-Z0-9]{3}[0-9]{2,}-PRD-[0-9]{6,}$'
);
CREATE UNIQUE INDEX uq_products_generated_reference_global
    ON products(lower(product_reference))
    WHERE product_reference ~ '^[A-Z0-9]{3}[0-9]{2,}-PRD-[0-9]{6,}$';
