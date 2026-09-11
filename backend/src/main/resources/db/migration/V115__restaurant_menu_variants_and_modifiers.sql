CREATE TABLE food_menu_item_variants (
    id UUID PRIMARY KEY,
    menu_item_id UUID NOT NULL REFERENCES food_menu_items(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    price NUMERIC(19,4) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_food_menu_item_variant_name UNIQUE (menu_item_id, name),
    CONSTRAINT ck_food_menu_item_variant_price CHECK (price >= 0),
    CONSTRAINT ck_food_menu_item_variant_order CHECK (display_order >= 0)
);

CREATE TABLE food_menu_modifier_groups (
    id UUID PRIMARY KEY,
    menu_item_id UUID NOT NULL REFERENCES food_menu_items(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    minimum_selections INTEGER NOT NULL DEFAULT 0,
    maximum_selections INTEGER NOT NULL DEFAULT 1,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_food_menu_modifier_group_name UNIQUE (menu_item_id, name),
    CONSTRAINT ck_food_menu_modifier_group_range CHECK (minimum_selections >= 0 AND maximum_selections >= minimum_selections),
    CONSTRAINT ck_food_menu_modifier_group_order CHECK (display_order >= 0)
);

CREATE TABLE food_menu_modifier_options (
    id UUID PRIMARY KEY,
    modifier_group_id UUID NOT NULL REFERENCES food_menu_modifier_groups(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    price_adjustment NUMERIC(19,4) NOT NULL DEFAULT 0,
    display_order INTEGER NOT NULL DEFAULT 0,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_food_menu_modifier_option_name UNIQUE (modifier_group_id, name),
    CONSTRAINT ck_food_menu_modifier_option_price CHECK (price_adjustment >= 0),
    CONSTRAINT ck_food_menu_modifier_option_order CHECK (display_order >= 0)
);

CREATE INDEX idx_food_menu_item_variants_order ON food_menu_item_variants(menu_item_id, display_order, name);
CREATE INDEX idx_food_menu_modifier_groups_order ON food_menu_modifier_groups(menu_item_id, display_order, name);
CREATE INDEX idx_food_menu_modifier_options_order ON food_menu_modifier_options(modifier_group_id, display_order, name);
ALTER TABLE sale_items ADD COLUMN menu_modifier_snapshot TEXT;
