
CREATE TABLE idempotency_keys (
    actor                 text        NOT NULL,
    idempotency_key       text        NOT NULL,
    request_hash          text        NOT NULL,
    state                 text        NOT NULL CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    response_status       integer,
    response_content_type text,
    response_location     text,
    response_body         text,
    created_at            timestamptz NOT NULL,
    completed_at          timestamptz,
    PRIMARY KEY (actor, idempotency_key)
);
