CREATE TABLE tax_rules (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    priority INTEGER NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_tax_rules_code UNIQUE (code),
    CONSTRAINT ck_tax_rules_priority_non_negative CHECK (priority >= 0),
    CONSTRAINT ck_tax_rules_effective_period CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE tax_rule_conditions (
    id UUID PRIMARY KEY,
    tax_rule_id UUID NOT NULL,
    condition_type VARCHAR(64) NOT NULL,
    operator VARCHAR(32) NOT NULL,
    value VARCHAR(180),
    second_value VARCHAR(180),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_tax_rule_conditions_rule FOREIGN KEY (tax_rule_id) REFERENCES tax_rules(id) ON DELETE CASCADE
);

CREATE TABLE tax_rule_actions (
    id UUID PRIMARY KEY,
    tax_rule_id UUID NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    tax_group_id UUID,
    tax_component_id UUID,
    value VARCHAR(180),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_tax_rule_actions_rule FOREIGN KEY (tax_rule_id) REFERENCES tax_rules(id) ON DELETE CASCADE,
    CONSTRAINT fk_tax_rule_actions_group FOREIGN KEY (tax_group_id) REFERENCES tax_groups(id),
    CONSTRAINT fk_tax_rule_actions_component FOREIGN KEY (tax_component_id) REFERENCES tax_components(id)
);

CREATE INDEX ix_tax_rules_effective_active_priority ON tax_rules(active, effective_from, effective_to, priority, code);
CREATE INDEX ix_tax_rule_conditions_rule ON tax_rule_conditions(tax_rule_id);
CREATE INDEX ix_tax_rule_actions_rule ON tax_rule_actions(tax_rule_id);
