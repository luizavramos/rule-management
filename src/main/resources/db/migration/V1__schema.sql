-- rule-management (spec 002, plano 4.3): regras, versoes imutaveis, conjuntos publicados e auditoria append-only.

CREATE TABLE rules (
    rule_id    text        PRIMARY KEY,
    created_by text        NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE rule_versions (
    rule_id         text        NOT NULL REFERENCES rules (rule_id),
    version         integer     NOT NULL CHECK (version >= 1),
    expression      text        NOT NULL CHECK (char_length(expression) BETWEEN 1 AND 2000),
    params          jsonb       NOT NULL,
    param_overrides jsonb       NOT NULL,
    weight          integer     NOT NULL CHECK (weight BETWEEN 0 AND 1000),
    reason_template text        NOT NULL CHECK (char_length(reason_template) BETWEEN 1 AND 200),
    status          text        NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'SHADOW', 'DISABLED')),
    author          text        NOT NULL,
    approved_by     text,
    created_at      timestamptz NOT NULL,
    PRIMARY KEY (rule_id, version)
);

-- Versao imutavel: so o status e o aprovador podem mudar. Mudar o conteudo exige uma versao nova.
CREATE FUNCTION rule_versions_keep_content() RETURNS trigger AS $$
BEGIN
    IF NEW.rule_id <> OLD.rule_id OR NEW.version <> OLD.version OR NEW.expression <> OLD.expression
       OR NEW.params <> OLD.params OR NEW.param_overrides <> OLD.param_overrides OR NEW.weight <> OLD.weight
       OR NEW.reason_template <> OLD.reason_template OR NEW.author <> OLD.author OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'o conteudo de uma versao de regra e imutavel: crie uma versao nova (%, v%)', OLD.rule_id, OLD.version;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER rule_versions_immutable BEFORE UPDATE ON rule_versions
    FOR EACH ROW EXECUTE FUNCTION rule_versions_keep_content();

CREATE TABLE rule_sets (
    version          bigint      PRIMARY KEY CHECK (version >= 1),
    published_at     timestamptz NOT NULL,
    published_by     text        NOT NULL,
    medium_threshold integer     NOT NULL CHECK (medium_threshold >= 1),
    high_threshold   integer     NOT NULL,
    CHECK (medium_threshold <= high_threshold)
);

-- O status do item e o que a versao tinha NAQUELE conjunto (o rollback restaura isso).
CREATE TABLE rule_set_items (
    rule_set_version bigint  NOT NULL REFERENCES rule_sets (version),
    rule_id          text    NOT NULL,
    rule_version     integer NOT NULL,
    status           text    NOT NULL CHECK (status IN ('ACTIVE', 'SHADOW')),
    PRIMARY KEY (rule_set_version, rule_id),
    FOREIGN KEY (rule_id, rule_version) REFERENCES rule_versions (rule_id, version)
);

CREATE TABLE audit_log (
    id     bigserial   PRIMARY KEY,
    actor  text        NOT NULL,
    action text        NOT NULL,
    entity text        NOT NULL,
    before jsonb,
    after  jsonb,
    at     timestamptz NOT NULL
);

-- Append-only: nem UPDATE nem DELETE.
CREATE FUNCTION audit_log_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log e append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();
