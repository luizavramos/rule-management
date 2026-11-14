-- Idempotency-Key (plano 002, secao 4.4): a resposta de cada POST/PUT com chave, por usuario, repetida em reenvios.
-- Linhas com mais de 24 h sao tratadas como vencidas e reaproveitadas na proxima reserva da mesma chave; a tabela e
-- pequena (so operacoes manuais da API), entao nao ha rotina de limpeza.

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
