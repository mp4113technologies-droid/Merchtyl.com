ALTER TABLE store_capabilities DROP CONSTRAINT ck_store_capabilities_value;
ALTER TABLE store_capabilities ADD CONSTRAINT ck_store_capabilities_value
    CHECK (capability IN ('RETAIL', 'FOOD_SERVICE', 'LOTTERY'));

ALTER TABLE tenant_store_operation_defaults DROP CONSTRAINT ck_tenant_store_operation_defaults_value;
ALTER TABLE tenant_store_operation_defaults ADD CONSTRAINT ck_tenant_store_operation_defaults_value
    CHECK (capability IN ('RETAIL', 'FOOD_SERVICE', 'LOTTERY'));
