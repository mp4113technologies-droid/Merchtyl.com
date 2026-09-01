ALTER TABLE platform_pricing_plan_capability_prices
    DROP CONSTRAINT IF EXISTS ck_plan_capability_price_capability;

ALTER TABLE platform_pricing_plan_capability_prices
    ADD CONSTRAINT ck_plan_capability_price_capability CHECK (
        capability IN (
            'RETAIL_POS',
            'INVENTORY',
            'REGISTER_MANAGEMENT',
            'RETURNS',
            'REPORTING',
            'ADVANCED_REPORTING',
            'EMPLOYEE_MANAGEMENT',
            'FOOD_SERVICE',
            'LOTTERY'
        )
    );

ALTER TABLE tenant_subscription_capability_price_snapshots
    DROP CONSTRAINT IF EXISTS ck_subscription_capability_snapshot_capability;

ALTER TABLE tenant_subscription_capability_price_snapshots
    ADD CONSTRAINT ck_subscription_capability_snapshot_capability CHECK (
        capability IN (
            'RETAIL_POS',
            'INVENTORY',
            'REGISTER_MANAGEMENT',
            'RETURNS',
            'REPORTING',
            'ADVANCED_REPORTING',
            'EMPLOYEE_MANAGEMENT',
            'FOOD_SERVICE',
            'LOTTERY'
        )
    );
