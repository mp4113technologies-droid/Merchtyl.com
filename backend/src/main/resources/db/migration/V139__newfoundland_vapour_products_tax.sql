-- Newfoundland and Labrador's retail Vapour Products Tax is calculated first;
-- HST then compounds on the net amount plus VPT. Existing sale_item_taxes are
-- historical snapshots and are deliberately not modified.

ALTER TABLE tax_categories ADD COLUMN applicable_product_tax_class VARCHAR(32);
ALTER TABLE tax_categories ADD CONSTRAINT chk_tax_categories_product_tax_class
    CHECK (applicable_product_tax_class IS NULL OR applicable_product_tax_class IN ('STANDARD','NON_TAXABLE','VAPE'));

UPDATE tax_categories
SET applicable_product_tax_class = 'VAPE'
WHERE system_managed = TRUE
  AND code IN ('CA_AB_VAPE','CA_BC_VAPE','CA_MB_VAPE','CA_NB_VAPE','CA_NL_VAPE','CA_NS_VAPE',
               'CA_NT_VAPE','CA_NU_VAPE','CA_ON_VAPE','CA_PE_VAPE','CA_QC_VAPE','CA_SK_VAPE','CA_YT_VAPE');

-- Repair contradictory active catalog configuration using the explicit existing
-- tax assignment (never product names/categories): a product directly assigned a
-- system VAPE-only category is semantically VAPE. Future resolution is store-based.
UPDATE products p
SET tax_class = 'VAPE', tax_category_id = NULL, updated_at = now()
WHERE p.tax_category_id IN (
    SELECT tc.id FROM tax_categories tc
    WHERE tc.system_managed = TRUE AND tc.applicable_product_tax_class = 'VAPE'
);

ALTER TABLE tax_components ADD COLUMN system_managed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tax_groups ADD COLUMN system_managed BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO tax_components
    (id, tax_type_id, tax_jurisdiction_id, code, name, description, active,
     reporting_type, system_managed, created_at, updated_at, version)
VALUES
    ('13900000-0000-0000-0000-000000000401',
     '10000000-0000-0000-0000-000000000303',
     '10000000-0000-0000-0000-000000000205',
     'CA_NL_VPT', 'Newfoundland and Labrador Vapour Products Tax',
     'Newfoundland and Labrador retail Vapour Products Tax.', TRUE,
     'VAPE_TAX', TRUE, now(), now(), 0)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    active = TRUE,
    reporting_type = 'VAPE_TAX',
    system_managed = TRUE,
    updated_at = now();

INSERT INTO tax_rates
    (id, tax_component_id, percentage_rate, effective_from, effective_to,
     included_in_price, compound_on_previous_tax, calculation_order, status,
     source, source_reference, verified_by, verified_at, created_at, updated_at, version)
VALUES
    ('13900000-0000-0000-0000-000000000501',
     (SELECT id FROM tax_components WHERE code = 'CA_NL_VPT'),
     20.000000, '2026-01-01', NULL, FALSE, FALSE, 1, 'ACTIVE',
     'Newfoundland and Labrador Vapour Products Tax',
     'Retail Vapour Products Tax configuration', 'Merchtyl system configuration',
     '2026-09-26T00:00:00Z', now(), now(), 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tax_groups
    (id, code, name, description, active, system_managed, created_at, updated_at, version)
VALUES
    ('13900000-0000-0000-0000-000000000601', 'CA_NL_VAPE_GROUP',
     'Newfoundland and Labrador Vape Taxes',
     'Retail VPT followed by compound HST for Newfoundland and Labrador Vape products.',
     TRUE, TRUE, now(), now(), 0)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name, description = EXCLUDED.description, active = TRUE, system_managed = TRUE, updated_at = now();

INSERT INTO tax_group_components
    (id, tax_group_id, tax_component_id, calculation_order, active, created_at, updated_at, version)
VALUES
    ('13900000-0000-0000-0000-000000000701',
     (SELECT id FROM tax_groups WHERE code = 'CA_NL_VAPE_GROUP'),
     (SELECT id FROM tax_components WHERE code = 'CA_NL_VPT'), 1, TRUE, now(), now(), 0),
    ('13900000-0000-0000-0000-000000000702',
     (SELECT id FROM tax_groups WHERE code = 'CA_NL_VAPE_GROUP'),
     (SELECT id FROM tax_components WHERE code = 'CA_NL_HST'), 2, TRUE, now(), now(), 0)
ON CONFLICT (tax_group_id, tax_component_id) DO UPDATE SET
    calculation_order = EXCLUDED.calculation_order, active = TRUE, updated_at = now();

-- Rate order/compound flags belong to effective-dated rates. Only the existing
-- open NL HST rate is changed for future calculations; historical snapshots stay intact.
UPDATE tax_rates
SET calculation_order = 2,
    compound_on_previous_tax = TRUE,
    updated_at = now()
WHERE tax_component_id = (SELECT id FROM tax_components WHERE code = 'CA_NL_HST')
  AND status IN ('ACTIVE','SCHEDULED')
  AND effective_to IS NULL;

UPDATE tax_categories
SET tax_group_id = (SELECT id FROM tax_groups WHERE code = 'CA_NL_VAPE_GROUP'),
    applicable_product_tax_class = 'VAPE',
    updated_at = now()
WHERE code = 'CA_NL_VAPE';
