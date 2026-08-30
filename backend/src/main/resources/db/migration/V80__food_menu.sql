INSERT INTO product_capability_assignments (id, product_id, capability, created_at, updated_at, version)
SELECT md5('product-capability:' || product.id || ':RETAIL')::UUID, product.id, 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM products product
ON CONFLICT (product_id, capability) DO NOTHING;

CREATE TABLE food_menu_categories (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    display_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    image_url VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_food_menu_categories_store_name UNIQUE (store_id, name),
    CONSTRAINT ck_food_menu_categories_order CHECK (display_order >= 0)
);

CREATE TABLE food_menu_items (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES food_menu_categories(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    display_name VARCHAR(180) NOT NULL,
    price NUMERIC(19,4) NOT NULL,
    display_order INTEGER NOT NULL,
    available BOOLEAN NOT NULL,
    image_url VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_food_menu_items_store_product UNIQUE (store_id, product_id),
    CONSTRAINT ck_food_menu_items_price CHECK (price >= 0),
    CONSTRAINT ck_food_menu_items_order CHECK (display_order >= 0)
);

CREATE INDEX idx_food_menu_categories_store_order ON food_menu_categories(store_id, display_order, name);
CREATE INDEX idx_food_menu_items_store_category_order ON food_menu_items(store_id, category_id, display_order, display_name);
