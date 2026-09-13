ALTER TABLE food_menu_modifier_groups ADD COLUMN store_id UUID;
ALTER TABLE food_menu_modifier_groups ADD COLUMN tenant_id UUID;
ALTER TABLE food_menu_modifier_groups ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE food_menu_modifier_groups modifier_group
SET store_id = menu_item.store_id,
    tenant_id = menu_item.tenant_id
FROM food_menu_items menu_item
WHERE menu_item.id = modifier_group.menu_item_id;

ALTER TABLE food_menu_modifier_groups ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE food_menu_modifier_groups ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE food_menu_modifier_groups
    ADD CONSTRAINT fk_food_menu_modifier_groups_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE;

CREATE TABLE food_menu_item_modifier_group_assignments (
    id UUID PRIMARY KEY,
    menu_item_id UUID NOT NULL REFERENCES food_menu_items(id) ON DELETE CASCADE,
    modifier_group_id UUID NOT NULL REFERENCES food_menu_modifier_groups(id) ON DELETE RESTRICT,
    minimum_selections INTEGER NOT NULL DEFAULT 0,
    maximum_selections INTEGER NOT NULL DEFAULT 1,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_food_menu_item_modifier_group_assignment UNIQUE(menu_item_id, modifier_group_id),
    CONSTRAINT ck_food_menu_item_modifier_group_assignment_range CHECK(minimum_selections >= 0 AND maximum_selections >= minimum_selections),
    CONSTRAINT ck_food_menu_item_modifier_group_assignment_order CHECK(display_order >= 0)
);

INSERT INTO food_menu_item_modifier_group_assignments(
    id, menu_item_id, modifier_group_id, minimum_selections, maximum_selections,
    display_order, active, created_at, updated_at, version)
SELECT md5('food-menu-assignment:' || id::text)::uuid, menu_item_id, id,
       minimum_selections, maximum_selections, display_order, TRUE,
       created_at, updated_at, 0
FROM food_menu_modifier_groups;

ALTER TABLE food_menu_modifier_groups DROP CONSTRAINT uq_food_menu_modifier_group_name;
ALTER TABLE food_menu_modifier_groups DROP CONSTRAINT ck_food_menu_modifier_group_range;
ALTER TABLE food_menu_modifier_groups DROP CONSTRAINT ck_food_menu_modifier_group_order;
ALTER TABLE food_menu_modifier_groups DROP COLUMN menu_item_id;
ALTER TABLE food_menu_modifier_groups DROP COLUMN minimum_selections;
ALTER TABLE food_menu_modifier_groups DROP COLUMN maximum_selections;
ALTER TABLE food_menu_modifier_groups DROP COLUMN display_order;

CREATE INDEX idx_food_menu_modifier_groups_store
    ON food_menu_modifier_groups(store_id, active, name);
CREATE INDEX idx_food_menu_item_modifier_assignments
    ON food_menu_item_modifier_group_assignments(menu_item_id, active, display_order);
