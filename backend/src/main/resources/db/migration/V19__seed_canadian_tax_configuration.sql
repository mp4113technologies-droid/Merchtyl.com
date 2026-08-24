INSERT INTO countries (id, code, name, active, created_at, updated_at, version)
VALUES ('10000000-0000-0000-0000-000000000001', 'CA', 'Canada', TRUE, now(), now(), 0)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO administrative_areas (id, country_id, code, name, type, active, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000000101', '10000000-0000-0000-0000-000000000001', 'AB', 'Alberta', 'PROVINCE', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000102', '10000000-0000-0000-0000-000000000001', 'BC', 'British Columbia', 'PROVINCE', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000103', '10000000-0000-0000-0000-000000000001', 'MB', 'Manitoba', 'PROVINCE', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000104', '10000000-0000-0000-0000-000000000001', 'NB', 'New Brunswick', 'PROVINCE', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000105', '10000000-0000-0000-0000-000000000001', 'NL', 'Newfoundland and Labrador', 'PROVINCE', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000106', '10000000-0000-0000-0000-000000000001', 'NS', 'Nova Scotia', 'PROVINCE', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000107', '10000000-0000-0000-0000-000000000001', 'NT', 'Northwest Territories', 'TERRITORY', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000108', '10000000-0000-0000-0000-000000000001', 'NU', 'Nunavut', 'TERRITORY', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000109', '10000000-0000-0000-0000-000000000001', 'ON', 'Ontario', 'PROVINCE', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000110', '10000000-0000-0000-0000-000000000001', 'PE', 'Prince Edward Island', 'PROVINCE', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000111', '10000000-0000-0000-0000-000000000001', 'QC', 'Quebec', 'PROVINCE', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000112', '10000000-0000-0000-0000-000000000001', 'SK', 'Saskatchewan', 'PROVINCE', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000113', '10000000-0000-0000-0000-000000000001', 'YT', 'Yukon', 'TERRITORY', TRUE, now(), now(), 0)
ON CONFLICT (country_id, code) DO UPDATE SET
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO tax_jurisdictions (id, country_id, administrative_area_id, code, name, type, active, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000000200', '10000000-0000-0000-0000-000000000001', NULL, 'CA', 'Canada GST jurisdiction', 'NATIONAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000201', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000101', 'CA-AB', 'Alberta tax jurisdiction', 'PROVINCIAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000202', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 'CA-BC', 'British Columbia tax jurisdiction', 'PROVINCIAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000203', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000103', 'CA-MB', 'Manitoba tax jurisdiction', 'PROVINCIAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000204', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000104', 'CA-NB', 'New Brunswick tax jurisdiction', 'PROVINCIAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000205', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000105', 'CA-NL', 'Newfoundland and Labrador tax jurisdiction', 'PROVINCIAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000206', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000106', 'CA-NS', 'Nova Scotia tax jurisdiction', 'PROVINCIAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000207', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000107', 'CA-NT', 'Northwest Territories tax jurisdiction', 'TERRITORIAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000208', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000108', 'CA-NU', 'Nunavut tax jurisdiction', 'TERRITORIAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000209', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000109', 'CA-ON', 'Ontario tax jurisdiction', 'PROVINCIAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000210', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000110', 'CA-PE', 'Prince Edward Island tax jurisdiction', 'PROVINCIAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000211', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000111', 'CA-QC', 'Quebec tax jurisdiction', 'PROVINCIAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000212', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000112', 'CA-SK', 'Saskatchewan tax jurisdiction', 'PROVINCIAL', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000213', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000113', 'CA-YT', 'Yukon tax jurisdiction', 'TERRITORIAL', TRUE, now(), now(), 0)
ON CONFLICT (country_id, code) DO UPDATE SET
    administrative_area_id = EXCLUDED.administrative_area_id,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO tax_types (id, code, name, description, active, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000000301', 'GST', 'Goods and Services Tax', 'Canadian federal goods and services tax.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000302', 'HST', 'Harmonized Sales Tax', 'Canadian harmonized sales tax for participating provinces.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000303', 'PST', 'Provincial Sales Tax', 'Canadian provincial sales tax.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000304', 'RST', 'Retail Sales Tax', 'Manitoba retail sales tax.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000305', 'QST', 'Quebec Sales Tax', 'Quebec sales tax.', TRUE, now(), now(), 0)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO tax_components (id, tax_type_id, tax_jurisdiction_id, code, name, description, active, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000000401', '10000000-0000-0000-0000-000000000301', '10000000-0000-0000-0000-000000000200', 'CA_GST', 'Canada GST', 'Federal GST component.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000402', '10000000-0000-0000-0000-000000000303', '10000000-0000-0000-0000-000000000202', 'CA_BC_PST', 'British Columbia PST', 'British Columbia PST component.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000403', '10000000-0000-0000-0000-000000000304', '10000000-0000-0000-0000-000000000203', 'CA_MB_RST', 'Manitoba RST', 'Manitoba RST component.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000404', '10000000-0000-0000-0000-000000000302', '10000000-0000-0000-0000-000000000204', 'CA_NB_HST', 'New Brunswick HST', 'New Brunswick HST component.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000405', '10000000-0000-0000-0000-000000000302', '10000000-0000-0000-0000-000000000205', 'CA_NL_HST', 'Newfoundland and Labrador HST', 'Newfoundland and Labrador HST component.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000406', '10000000-0000-0000-0000-000000000302', '10000000-0000-0000-0000-000000000206', 'CA_NS_HST', 'Nova Scotia HST', 'Nova Scotia HST component.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000407', '10000000-0000-0000-0000-000000000302', '10000000-0000-0000-0000-000000000209', 'CA_ON_HST', 'Ontario HST', 'Ontario HST component.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000408', '10000000-0000-0000-0000-000000000302', '10000000-0000-0000-0000-000000000210', 'CA_PE_HST', 'Prince Edward Island HST', 'Prince Edward Island HST component.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000409', '10000000-0000-0000-0000-000000000305', '10000000-0000-0000-0000-000000000211', 'CA_QC_QST', 'Quebec QST', 'Quebec QST component.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000410', '10000000-0000-0000-0000-000000000303', '10000000-0000-0000-0000-000000000212', 'CA_SK_PST', 'Saskatchewan PST', 'Saskatchewan PST component.', TRUE, now(), now(), 0)
ON CONFLICT (code) DO UPDATE SET
    tax_type_id = EXCLUDED.tax_type_id,
    tax_jurisdiction_id = EXCLUDED.tax_jurisdiction_id,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO tax_rates (id, tax_component_id, percentage_rate, effective_from, effective_to, included_in_price, compound_on_previous_tax, calculation_order, status, source, source_reference, verified_by, verified_at, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000000501', '10000000-0000-0000-0000-000000000401', 5.000000, '2026-01-01', NULL, FALSE, FALSE, 0, 'ACTIVE', 'Canada Revenue Agency GST/HST rates', 'https://www.canada.ca/en/revenue-agency/services/tax/businesses/topics/gst-hst-businesses/charge-collect-which-rate.html', 'Merchtyl seed data', '2026-07-23T00:00:00Z', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000502', '10000000-0000-0000-0000-000000000402', 7.000000, '2026-01-01', NULL, FALSE, FALSE, 1, 'ACTIVE', 'Province of British Columbia PST', 'https://www2.gov.bc.ca/gov/content/taxes/sales-taxes/pst', 'Merchtyl seed data', '2026-07-23T00:00:00Z', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000503', '10000000-0000-0000-0000-000000000403', 7.000000, '2026-01-01', NULL, FALSE, FALSE, 1, 'ACTIVE', 'Province of Manitoba RST', 'https://www.gov.mb.ca/finance/taxation/taxes/retail.html', 'Merchtyl seed data', '2026-07-23T00:00:00Z', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000504', '10000000-0000-0000-0000-000000000404', 15.000000, '2026-01-01', NULL, FALSE, FALSE, 0, 'ACTIVE', 'Canada Revenue Agency GST/HST rates', 'https://www.canada.ca/en/revenue-agency/services/tax/businesses/topics/gst-hst-businesses/charge-collect-which-rate.html', 'Merchtyl seed data', '2026-07-23T00:00:00Z', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000505', '10000000-0000-0000-0000-000000000405', 15.000000, '2026-01-01', NULL, FALSE, FALSE, 0, 'ACTIVE', 'Canada Revenue Agency GST/HST rates', 'https://www.canada.ca/en/revenue-agency/services/tax/businesses/topics/gst-hst-businesses/charge-collect-which-rate.html', 'Merchtyl seed data', '2026-07-23T00:00:00Z', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000506', '10000000-0000-0000-0000-000000000406', 14.000000, '2026-01-01', NULL, FALSE, FALSE, 0, 'ACTIVE', 'Canada Revenue Agency GST/HST rates', 'https://www.canada.ca/en/revenue-agency/services/tax/businesses/topics/gst-hst-businesses/charge-collect-which-rate.html', 'Merchtyl seed data', '2026-07-23T00:00:00Z', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000507', '10000000-0000-0000-0000-000000000407', 13.000000, '2026-01-01', NULL, FALSE, FALSE, 0, 'ACTIVE', 'Canada Revenue Agency GST/HST rates', 'https://www.canada.ca/en/revenue-agency/services/tax/businesses/topics/gst-hst-businesses/charge-collect-which-rate.html', 'Merchtyl seed data', '2026-07-23T00:00:00Z', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000508', '10000000-0000-0000-0000-000000000408', 15.000000, '2026-01-01', NULL, FALSE, FALSE, 0, 'ACTIVE', 'Canada Revenue Agency GST/HST rates', 'https://www.canada.ca/en/revenue-agency/services/tax/businesses/topics/gst-hst-businesses/charge-collect-which-rate.html', 'Merchtyl seed data', '2026-07-23T00:00:00Z', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000509', '10000000-0000-0000-0000-000000000409', 9.975000, '2026-01-01', NULL, FALSE, FALSE, 1, 'ACTIVE', 'Revenu Quebec GST/QST rates', 'https://www.revenuquebec.ca/en/businesses/consumption-taxes/gsthst-and-qst/basic-rules-for-applying-the-gsthst-and-qst/', 'Merchtyl seed data', '2026-07-23T00:00:00Z', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000510', '10000000-0000-0000-0000-000000000410', 6.000000, '2026-01-01', NULL, FALSE, FALSE, 1, 'ACTIVE', 'Government of Saskatchewan PST', 'https://www.saskatchewan.ca/business/taxes-licensing-and-reporting/provincial-taxes-policies-and-bulletins/provincial-sales-tax', 'Merchtyl seed data', '2026-07-23T00:00:00Z', now(), now(), 0)
ON CONFLICT (id) DO UPDATE SET
    tax_component_id = EXCLUDED.tax_component_id,
    percentage_rate = EXCLUDED.percentage_rate,
    effective_from = EXCLUDED.effective_from,
    effective_to = EXCLUDED.effective_to,
    included_in_price = EXCLUDED.included_in_price,
    compound_on_previous_tax = EXCLUDED.compound_on_previous_tax,
    calculation_order = EXCLUDED.calculation_order,
    status = EXCLUDED.status,
    source = EXCLUDED.source,
    source_reference = EXCLUDED.source_reference,
    verified_by = EXCLUDED.verified_by,
    verified_at = EXCLUDED.verified_at,
    updated_at = now();

INSERT INTO tax_groups (id, code, name, description, active, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000000601', 'CA_AB_GST', 'Alberta GST', 'Canadian standard tax group for Alberta.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000602', 'CA_BC_GST_PST', 'British Columbia GST/PST', 'Canadian standard tax group for British Columbia.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000603', 'CA_MB_GST_RST', 'Manitoba GST/RST', 'Canadian standard tax group for Manitoba.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000604', 'CA_NB_HST', 'New Brunswick HST', 'Canadian standard tax group for New Brunswick.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000605', 'CA_NL_HST', 'Newfoundland and Labrador HST', 'Canadian standard tax group for Newfoundland and Labrador.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000606', 'CA_NS_HST', 'Nova Scotia HST', 'Canadian standard tax group for Nova Scotia.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000607', 'CA_NT_GST', 'Northwest Territories GST', 'Canadian standard tax group for Northwest Territories.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000608', 'CA_NU_GST', 'Nunavut GST', 'Canadian standard tax group for Nunavut.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000609', 'CA_ON_HST', 'Ontario HST', 'Canadian standard tax group for Ontario.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000610', 'CA_PE_HST', 'Prince Edward Island HST', 'Canadian standard tax group for Prince Edward Island.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000611', 'CA_QC_GST_QST', 'Quebec GST/QST', 'Canadian standard tax group for Quebec.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000612', 'CA_SK_GST_PST', 'Saskatchewan GST/PST', 'Canadian standard tax group for Saskatchewan.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000613', 'CA_YT_GST', 'Yukon GST', 'Canadian standard tax group for Yukon.', TRUE, now(), now(), 0)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO tax_group_components (id, tax_group_id, tax_component_id, calculation_order, active, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000000701', '10000000-0000-0000-0000-000000000601', '10000000-0000-0000-0000-000000000401', 0, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000702', '10000000-0000-0000-0000-000000000602', '10000000-0000-0000-0000-000000000401', 0, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000703', '10000000-0000-0000-0000-000000000602', '10000000-0000-0000-0000-000000000402', 1, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000704', '10000000-0000-0000-0000-000000000603', '10000000-0000-0000-0000-000000000401', 0, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000705', '10000000-0000-0000-0000-000000000603', '10000000-0000-0000-0000-000000000403', 1, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000706', '10000000-0000-0000-0000-000000000604', '10000000-0000-0000-0000-000000000404', 0, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000707', '10000000-0000-0000-0000-000000000605', '10000000-0000-0000-0000-000000000405', 0, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000708', '10000000-0000-0000-0000-000000000606', '10000000-0000-0000-0000-000000000406', 0, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000709', '10000000-0000-0000-0000-000000000607', '10000000-0000-0000-0000-000000000401', 0, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000710', '10000000-0000-0000-0000-000000000608', '10000000-0000-0000-0000-000000000401', 0, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000711', '10000000-0000-0000-0000-000000000609', '10000000-0000-0000-0000-000000000407', 0, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000712', '10000000-0000-0000-0000-000000000610', '10000000-0000-0000-0000-000000000408', 0, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000713', '10000000-0000-0000-0000-000000000611', '10000000-0000-0000-0000-000000000401', 0, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000714', '10000000-0000-0000-0000-000000000611', '10000000-0000-0000-0000-000000000409', 1, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000715', '10000000-0000-0000-0000-000000000612', '10000000-0000-0000-0000-000000000401', 0, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000716', '10000000-0000-0000-0000-000000000612', '10000000-0000-0000-0000-000000000410', 1, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000717', '10000000-0000-0000-0000-000000000613', '10000000-0000-0000-0000-000000000401', 0, TRUE, now(), now(), 0)
ON CONFLICT (tax_group_id, tax_component_id) DO UPDATE SET
    calculation_order = EXCLUDED.calculation_order,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO tax_categories (id, tax_group_id, code, name, treatment, description, active, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000000801', NULL, 'STANDARD', 'Standard taxable', 'STANDARD', 'Default taxable treatment; jurisdiction rules select the applicable Canadian tax group.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000802', NULL, 'ZERO_RATED', 'Zero-rated', 'ZERO_RATED', 'Taxable at zero percent where a zero-rated treatment applies.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000803', NULL, 'EXEMPT', 'Exempt', 'EXEMPT', 'Exempt supplies where no tax is charged.', TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000804', NULL, 'OUT_OF_SCOPE', 'Out of scope', 'OUT_OF_SCOPE', 'Transactions outside configured sales tax scope.', TRUE, now(), now(), 0)
ON CONFLICT (code) DO UPDATE SET
    tax_group_id = EXCLUDED.tax_group_id,
    name = EXCLUDED.name,
    treatment = EXCLUDED.treatment,
    description = EXCLUDED.description,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO tax_rules (id, code, name, description, priority, effective_from, effective_to, active, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000000901', 'CA_AB_STANDARD', 'Apply Alberta GST', 'Applies configured GST group for Alberta supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000902', 'CA_BC_STANDARD', 'Apply British Columbia GST/PST', 'Applies configured GST/PST group for British Columbia supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000903', 'CA_MB_STANDARD', 'Apply Manitoba GST/RST', 'Applies configured GST/RST group for Manitoba supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000904', 'CA_NB_STANDARD', 'Apply New Brunswick HST', 'Applies configured HST group for New Brunswick supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000905', 'CA_NL_STANDARD', 'Apply Newfoundland and Labrador HST', 'Applies configured HST group for Newfoundland and Labrador supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000906', 'CA_NS_STANDARD', 'Apply Nova Scotia HST', 'Applies configured HST group for Nova Scotia supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000907', 'CA_NT_STANDARD', 'Apply Northwest Territories GST', 'Applies configured GST group for Northwest Territories supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000908', 'CA_NU_STANDARD', 'Apply Nunavut GST', 'Applies configured GST group for Nunavut supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000909', 'CA_ON_STANDARD', 'Apply Ontario HST', 'Applies configured HST group for Ontario supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000910', 'CA_PE_STANDARD', 'Apply Prince Edward Island HST', 'Applies configured HST group for Prince Edward Island supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000911', 'CA_QC_STANDARD', 'Apply Quebec GST/QST', 'Applies configured GST/QST group for Quebec supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000912', 'CA_SK_STANDARD', 'Apply Saskatchewan GST/PST', 'Applies configured GST/PST group for Saskatchewan supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000913', 'CA_YT_STANDARD', 'Apply Yukon GST', 'Applies configured GST group for Yukon supplies.', 100, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000914', 'CA_ZERO_RATED_CATEGORY', 'Apply zero-rated treatment', 'Marks zero-rated product tax categories as zero-rated after matching jurisdiction components.', 10, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000915', 'CA_EXEMPT_CATEGORY', 'Apply exempt treatment', 'Marks exempt product tax categories as exempt after matching jurisdiction components.', 10, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000916', 'CA_OUT_OF_SCOPE_CATEGORY', 'Apply out-of-scope treatment', 'Marks out-of-scope product tax categories as out-of-scope after matching jurisdiction components.', 10, '2026-01-01', NULL, TRUE, now(), now(), 0),
    ('10000000-0000-0000-0000-000000000917', 'CA_CUSTOMER_EXEMPTION', 'Apply customer exemption', 'Marks sales to exempt customers as exempt after matching jurisdiction components.', 10, '2026-01-01', NULL, TRUE, now(), now(), 0)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    priority = EXCLUDED.priority,
    effective_from = EXCLUDED.effective_from,
    effective_to = EXCLUDED.effective_to,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO tax_rule_conditions (id, tax_rule_id, condition_type, operator, value, second_value, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000001001', '10000000-0000-0000-0000-000000000901', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000201', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001002', '10000000-0000-0000-0000-000000000902', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000202', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001003', '10000000-0000-0000-0000-000000000903', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000203', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001004', '10000000-0000-0000-0000-000000000904', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000204', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001005', '10000000-0000-0000-0000-000000000905', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000205', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001006', '10000000-0000-0000-0000-000000000906', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000206', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001007', '10000000-0000-0000-0000-000000000907', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000207', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001008', '10000000-0000-0000-0000-000000000908', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000208', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001009', '10000000-0000-0000-0000-000000000909', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000209', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001010', '10000000-0000-0000-0000-000000000910', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000210', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001011', '10000000-0000-0000-0000-000000000911', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000211', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001012', '10000000-0000-0000-0000-000000000912', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000212', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001013', '10000000-0000-0000-0000-000000000913', 'SUPPLY_JURISDICTION', 'EQUALS', '10000000-0000-0000-0000-000000000213', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001014', '10000000-0000-0000-0000-000000000914', 'PRODUCT_TAX_CATEGORY', 'EQUALS', '10000000-0000-0000-0000-000000000802', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001015', '10000000-0000-0000-0000-000000000915', 'PRODUCT_TAX_CATEGORY', 'EQUALS', '10000000-0000-0000-0000-000000000803', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001016', '10000000-0000-0000-0000-000000000916', 'PRODUCT_TAX_CATEGORY', 'EQUALS', '10000000-0000-0000-0000-000000000804', NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001017', '10000000-0000-0000-0000-000000000917', 'CUSTOMER_EXEMPTION', 'IS_TRUE', NULL, NULL, now(), now(), 0)
ON CONFLICT (id) DO UPDATE SET
    tax_rule_id = EXCLUDED.tax_rule_id,
    condition_type = EXCLUDED.condition_type,
    operator = EXCLUDED.operator,
    value = EXCLUDED.value,
    second_value = EXCLUDED.second_value,
    updated_at = now();

INSERT INTO tax_rule_actions (id, tax_rule_id, action_type, tax_group_id, tax_component_id, value, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000001101', '10000000-0000-0000-0000-000000000901', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000601', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001102', '10000000-0000-0000-0000-000000000902', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000602', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001103', '10000000-0000-0000-0000-000000000903', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000603', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001104', '10000000-0000-0000-0000-000000000904', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000604', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001105', '10000000-0000-0000-0000-000000000905', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000605', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001106', '10000000-0000-0000-0000-000000000906', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000606', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001107', '10000000-0000-0000-0000-000000000907', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000607', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001108', '10000000-0000-0000-0000-000000000908', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000608', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001109', '10000000-0000-0000-0000-000000000909', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000609', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001110', '10000000-0000-0000-0000-000000000910', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000610', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001111', '10000000-0000-0000-0000-000000000911', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000611', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001112', '10000000-0000-0000-0000-000000000912', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000612', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001113', '10000000-0000-0000-0000-000000000913', 'APPLY_TAX_GROUP', '10000000-0000-0000-0000-000000000613', NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001114', '10000000-0000-0000-0000-000000000914', 'ZERO_RATE', NULL, NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001115', '10000000-0000-0000-0000-000000000915', 'EXEMPT', NULL, NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001116', '10000000-0000-0000-0000-000000000916', 'OUT_OF_SCOPE', NULL, NULL, NULL, now(), now(), 0),
    ('10000000-0000-0000-0000-000000001117', '10000000-0000-0000-0000-000000000917', 'EXEMPT', NULL, NULL, NULL, now(), now(), 0)
ON CONFLICT (id) DO UPDATE SET
    tax_rule_id = EXCLUDED.tax_rule_id,
    action_type = EXCLUDED.action_type,
    tax_group_id = EXCLUDED.tax_group_id,
    tax_component_id = EXCLUDED.tax_component_id,
    value = EXCLUDED.value,
    updated_at = now();
