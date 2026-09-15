ALTER TABLE categories
    ADD COLUMN system_managed BOOLEAN NOT NULL DEFAULT FALSE;

DROP TRIGGER IF EXISTS trg_categories_immutable_code ON categories;

WITH ranked_lottery_categories AS (
    SELECT id,
           tenant_id,
           row_number() OVER (PARTITION BY tenant_id ORDER BY created_at, id) AS position
    FROM categories
    WHERE tenant_id IS NOT NULL
      AND lower(btrim(name)) = 'lottery'
), adoptable AS (
    SELECT candidate.id
    FROM ranked_lottery_categories candidate
    WHERE candidate.position = 1
      AND NOT EXISTS (
          SELECT 1
          FROM categories coded
          WHERE coded.tenant_id = candidate.tenant_id
            AND lower(coded.code) = 'lottery'
      )
)
UPDATE categories category
SET code = 'LOTTERY',
    name = 'Lottery',
    active = TRUE,
    system_managed = TRUE,
    updated_at = now(),
    version = version + 1
FROM adoptable
WHERE category.id = adoptable.id;

UPDATE categories
SET name = 'Lottery',
    active = TRUE,
    system_managed = TRUE,
    updated_at = now(),
    version = version + 1
WHERE tenant_id IS NOT NULL
  AND lower(code) = 'lottery'
  AND (name IS DISTINCT FROM 'Lottery' OR active IS DISTINCT FROM TRUE OR system_managed IS DISTINCT FROM TRUE);

INSERT INTO categories (id, tenant_id, code, name, description, active, system_managed,
                        created_at, updated_at, version)
SELECT gen_random_uuid(), tenant.id, 'LOTTERY', 'Lottery',
       'System category for lottery products.', TRUE, TRUE, now(), now(), 0
FROM tenants tenant
WHERE NOT EXISTS (
    SELECT 1
    FROM categories category
    WHERE category.tenant_id = tenant.id
      AND lower(category.code) = 'lottery'
);

CREATE OR REPLACE FUNCTION provision_default_lottery_category() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO categories (id, tenant_id, code, name, description, active, system_managed,
                            created_at, updated_at, version)
    VALUES (gen_random_uuid(), NEW.id, 'LOTTERY', 'Lottery',
            'System category for lottery products.', TRUE, TRUE, now(), now(), 0)
    ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_tenants_default_lottery_category ON tenants;
CREATE TRIGGER trg_tenants_default_lottery_category
AFTER INSERT ON tenants
FOR EACH ROW EXECUTE FUNCTION provision_default_lottery_category();

CREATE TRIGGER trg_categories_immutable_code BEFORE UPDATE ON categories
FOR EACH ROW EXECUTE FUNCTION immutable_catalogue_code();
