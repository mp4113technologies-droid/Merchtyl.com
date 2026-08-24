CREATE TABLE security_user_store_assignments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    store_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_security_user_store_assignments_user FOREIGN KEY (user_id) REFERENCES security_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_security_user_store_assignments_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE,
    CONSTRAINT uq_security_user_store_assignments_user_store UNIQUE (user_id, store_id)
);

CREATE TABLE security_user_register_assignments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    register_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_security_user_register_assignments_user FOREIGN KEY (user_id) REFERENCES security_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_security_user_register_assignments_register FOREIGN KEY (register_id) REFERENCES registers (id) ON DELETE CASCADE,
    CONSTRAINT uq_security_user_register_assignments_user_register UNIQUE (user_id, register_id)
);

CREATE INDEX idx_security_user_store_assignments_user_id ON security_user_store_assignments (user_id);
CREATE INDEX idx_security_user_store_assignments_store_id ON security_user_store_assignments (store_id);
CREATE INDEX idx_security_user_register_assignments_user_id ON security_user_register_assignments (user_id);
CREATE INDEX idx_security_user_register_assignments_register_id ON security_user_register_assignments (register_id);
