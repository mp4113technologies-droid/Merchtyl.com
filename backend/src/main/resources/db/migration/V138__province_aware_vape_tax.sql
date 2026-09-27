-- Semantic product tax classification; existing products remain STANDARD.
ALTER TABLE products ADD COLUMN tax_class VARCHAR(32) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE products ADD CONSTRAINT chk_products_tax_class CHECK (tax_class IN ('STANDARD','NON_TAXABLE','VAPE'));

-- Explicit ownership and reporting semantics. Never infer either from a code string.
ALTER TABLE tax_categories ADD COLUMN system_managed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tax_components ADD COLUMN reporting_type VARCHAR(32) NOT NULL DEFAULT 'GENERAL_SALES_TAX';
ALTER TABLE tax_components ADD CONSTRAINT chk_tax_components_reporting_type
    CHECK (reporting_type IN ('GENERAL_SALES_TAX','VAPE_TAX','OTHER'));

UPDATE tax_categories SET system_managed = TRUE
WHERE code IN ('STANDARD','ZERO_RATED','EXEMPT','OUT_OF_SCOPE');

-- These categories deliberately reuse the currently verified retail sales-tax groups.
-- Coordinated federal/provincial vaping excise duty is not added at retail POS.
INSERT INTO tax_categories
    (id, tax_group_id, code, name, treatment, description, active, system_managed, created_at, updated_at, version)
VALUES
 ('13800000-0000-0000-0000-000000000101','10000000-0000-0000-0000-000000000601','CA_AB_VAPE','Alberta Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0),
 ('13800000-0000-0000-0000-000000000102','10000000-0000-0000-0000-000000000602','CA_BC_VAPE','British Columbia Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0),
 ('13800000-0000-0000-0000-000000000103','10000000-0000-0000-0000-000000000603','CA_MB_VAPE','Manitoba Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0),
 ('13800000-0000-0000-0000-000000000104','10000000-0000-0000-0000-000000000604','CA_NB_VAPE','New Brunswick Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0),
 ('13800000-0000-0000-0000-000000000105','10000000-0000-0000-0000-000000000605','CA_NL_VAPE','Newfoundland and Labrador Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0),
 ('13800000-0000-0000-0000-000000000106','10000000-0000-0000-0000-000000000606','CA_NS_VAPE','Nova Scotia Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0),
 ('13800000-0000-0000-0000-000000000107','10000000-0000-0000-0000-000000000607','CA_NT_VAPE','Northwest Territories Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0),
 ('13800000-0000-0000-0000-000000000108','10000000-0000-0000-0000-000000000608','CA_NU_VAPE','Nunavut Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0),
 ('13800000-0000-0000-0000-000000000109','10000000-0000-0000-0000-000000000609','CA_ON_VAPE','Ontario Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0),
 ('13800000-0000-0000-0000-000000000110','10000000-0000-0000-0000-000000000610','CA_PE_VAPE','Prince Edward Island Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0),
 ('13800000-0000-0000-0000-000000000111','10000000-0000-0000-0000-000000000611','CA_QC_VAPE','Quebec Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0),
 ('13800000-0000-0000-0000-000000000112','10000000-0000-0000-0000-000000000612','CA_SK_VAPE','Saskatchewan Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0),
 ('13800000-0000-0000-0000-000000000113','10000000-0000-0000-0000-000000000613','CA_YT_VAPE','Yukon Vape Products','STANDARD','System Vape category; retail components are effective-dated in the linked group.',TRUE,TRUE,now(),now(),0);

CREATE TABLE sale_item_taxes (
 id UUID PRIMARY KEY, sale_item_id UUID NOT NULL, tax_category_id UUID, tax_category_code VARCHAR(64),
 tax_group_id UUID, tax_group_code VARCHAR(64), product_tax_class VARCHAR(32) NOT NULL,
 tax_component_id UUID NOT NULL, tax_component_code VARCHAR(64) NOT NULL, tax_component_name VARCHAR(180) NOT NULL,
 reporting_type VARCHAR(32) NOT NULL, tax_rate_id UUID NOT NULL, percentage_rate NUMERIC(9,6) NOT NULL,
 taxable_amount NUMERIC(12,2) NOT NULL, tax_amount NUMERIC(12,2) NOT NULL,
 included_in_price BOOLEAN NOT NULL, compound_on_previous_tax BOOLEAN NOT NULL, calculation_order INTEGER NOT NULL,
 effective_from DATE NOT NULL, effective_to DATE, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL,
 CONSTRAINT fk_sale_item_taxes_item FOREIGN KEY (sale_item_id) REFERENCES sale_items(id) ON DELETE CASCADE,
 CONSTRAINT chk_sale_item_taxes_reporting_type CHECK (reporting_type IN ('GENERAL_SALES_TAX','VAPE_TAX','OTHER'))
);
CREATE INDEX idx_sale_item_taxes_item ON sale_item_taxes(sale_item_id);
CREATE INDEX idx_sale_item_taxes_reporting ON sale_item_taxes(reporting_type);

ALTER TABLE refund_item_taxes ADD COLUMN reporting_type VARCHAR(32) NOT NULL DEFAULT 'OTHER';
CREATE INDEX idx_refund_item_taxes_reporting ON refund_item_taxes(reporting_type);
