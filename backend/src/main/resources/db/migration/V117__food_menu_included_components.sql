CREATE TABLE food_menu_item_components (
    id UUID PRIMARY KEY,
    menu_item_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    included_by_default BOOLEAN NOT NULL DEFAULT TRUE,
    removable BOOLEAN NOT NULL DEFAULT TRUE,
    allow_extra BOOLEAN NOT NULL DEFAULT FALSE,
    extra_price NUMERIC(19,4) NOT NULL DEFAULT 0,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_food_menu_components_item FOREIGN KEY (menu_item_id) REFERENCES food_menu_items(id) ON DELETE CASCADE,
    CONSTRAINT ck_food_menu_components_extra_price CHECK (extra_price >= 0)
);

CREATE INDEX idx_food_menu_components_item_order ON food_menu_item_components(menu_item_id, display_order, name);

ALTER TABLE sale_items ADD COLUMN menu_component_snapshot TEXT;
