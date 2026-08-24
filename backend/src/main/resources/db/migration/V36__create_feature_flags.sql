CREATE TABLE feature_definitions (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    default_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_feature_definitions_code UNIQUE (code),
    CONSTRAINT ck_feature_definitions_code_nonblank CHECK (length(trim(code)) > 0),
    CONSTRAINT ck_feature_definitions_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_feature_definitions_description_nonblank CHECK (length(trim(description)) > 0)
);

CREATE TABLE tenant_features (
    id UUID PRIMARY KEY,
    feature_definition_id UUID NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_tenant_features_definition FOREIGN KEY (feature_definition_id) REFERENCES feature_definitions (id) ON DELETE CASCADE,
    CONSTRAINT uq_tenant_features_definition UNIQUE (feature_definition_id)
);

CREATE TABLE store_features (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    feature_definition_id UUID NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_store_features_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE,
    CONSTRAINT fk_store_features_definition FOREIGN KEY (feature_definition_id) REFERENCES feature_definitions (id) ON DELETE CASCADE,
    CONSTRAINT uq_store_features_store_definition UNIQUE (store_id, feature_definition_id)
);

CREATE TABLE register_features (
    id UUID PRIMARY KEY,
    register_id UUID NOT NULL,
    feature_definition_id UUID NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_register_features_register FOREIGN KEY (register_id) REFERENCES registers (id) ON DELETE CASCADE,
    CONSTRAINT fk_register_features_definition FOREIGN KEY (feature_definition_id) REFERENCES feature_definitions (id) ON DELETE CASCADE,
    CONSTRAINT uq_register_features_register_definition UNIQUE (register_id, feature_definition_id)
);

CREATE INDEX idx_tenant_features_definition_id ON tenant_features (feature_definition_id);
CREATE INDEX idx_store_features_store_id ON store_features (store_id);
CREATE INDEX idx_store_features_definition_id ON store_features (feature_definition_id);
CREATE INDEX idx_register_features_register_id ON register_features (register_id);
CREATE INDEX idx_register_features_definition_id ON register_features (feature_definition_id);

INSERT INTO feature_definitions (id, code, name, description, default_enabled)
VALUES
    ('00000000-0000-0000-0000-000000000f01', 'LOTTERY_SALES', 'Lottery sales', 'Enable sale workflows and controls for lottery products.', FALSE),
    ('00000000-0000-0000-0000-000000000f02', 'FOOD_SALES', 'Food sales', 'Enable sale workflows and controls for prepared food products.', FALSE),
    ('00000000-0000-0000-0000-000000000f03', 'KITCHEN_DISPLAY', 'Kitchen display', 'Enable kitchen display order routing and status screens.', FALSE),
    ('00000000-0000-0000-0000-000000000f04', 'AGE_VERIFICATION', 'Age verification', 'Enable age-verification prompts and enforcement for restricted products.', TRUE),
    ('00000000-0000-0000-0000-000000000f05', 'GIFT_CARDS', 'Gift cards', 'Enable gift card sale, redemption, and balance workflows.', FALSE),
    ('00000000-0000-0000-0000-000000000f06', 'LOYALTY', 'Loyalty', 'Enable loyalty enrollment, lookup, and rewards workflows.', FALSE),
    ('00000000-0000-0000-0000-000000000f07', 'PURCHASE_ORDERS', 'Purchase orders', 'Enable purchase-order planning and receiving workflows.', FALSE),
    ('00000000-0000-0000-0000-000000000f08', 'WAREHOUSE_TRANSFERS', 'Warehouse transfers', 'Enable warehouse and inter-location inventory transfer workflows.', FALSE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-00000000023e', 'FEATURE_VIEW', 'View feature flag configuration.'),
    ('00000000-0000-0000-0000-00000000023f', 'FEATURE_MANAGE', 'Manage feature flag configuration.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-00000000033e'::UUID, 'OWNER', 'FEATURE_VIEW'),
        ('00000000-0000-0000-0000-00000000033f'::UUID, 'OWNER', 'FEATURE_MANAGE'),
        ('00000000-0000-0000-0000-00000000043e'::UUID, 'MANAGER', 'FEATURE_VIEW'),
        ('00000000-0000-0000-0000-00000000043f'::UUID, 'MANAGER', 'FEATURE_MANAGE')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
