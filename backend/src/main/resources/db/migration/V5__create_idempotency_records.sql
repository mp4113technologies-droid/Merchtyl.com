CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    response_status INTEGER,
    response_content_type VARCHAR(120),
    response_body TEXT,
    failure_message VARCHAR(1000),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_idempotency_records_scope UNIQUE (user_id, endpoint, idempotency_key),
    CONSTRAINT ck_idempotency_records_key_nonblank CHECK (length(trim(idempotency_key)) > 0),
    CONSTRAINT ck_idempotency_records_endpoint_nonblank CHECK (length(trim(endpoint)) > 0),
    CONSTRAINT ck_idempotency_records_fingerprint_nonblank CHECK (length(trim(request_fingerprint)) > 0),
    CONSTRAINT ck_idempotency_records_state CHECK (state IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_idempotency_records_response_complete CHECK (
        state = 'PROCESSING' OR response_status IS NOT NULL
    )
);

CREATE INDEX idx_idempotency_records_user_id ON idempotency_records (user_id);
CREATE INDEX idx_idempotency_records_expires_at ON idempotency_records (expires_at);
CREATE INDEX idx_idempotency_records_state ON idempotency_records (state);
