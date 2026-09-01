ALTER TABLE products
    ADD COLUMN restaurant_menu_managed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE food_menu_items
    ADD COLUMN description VARCHAR(1000),
    ADD COLUMN linked_product BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_food_menu_items_store_available
    ON food_menu_items(store_id, available, category_id, display_order);
