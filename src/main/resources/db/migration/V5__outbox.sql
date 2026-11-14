CREATE TABLE outbox (
    id            uuid        PRIMARY KEY,
    aggregatetype text        NOT NULL,
    aggregateid   text        NOT NULL,
    type          text        NOT NULL,
    payload       jsonb       NOT NULL,
    created_at    timestamptz NOT NULL
);

CREATE INDEX outbox_created_at ON outbox (created_at, id);

INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload, created_at)
SELECT gen_random_uuid(), 'rule-sets.v1', 'active', 'RuleSetPublished',
       jsonb_build_object(
           'version', s.version,
           'publishedAt', to_char(s.published_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
           'scoring', jsonb_build_object('mediumThreshold', s.medium_threshold, 'highThreshold', s.high_threshold),
           'rules', coalesce((
               SELECT jsonb_agg(jsonb_build_object(
                          'ruleId', v.rule_id, 'version', v.version, 'status', i.status, 'expression', v.expression,
                          'params', v.params, 'paramOverrides', v.param_overrides, 'weight', v.weight,
                          'reasonTemplate', v.reason_template) ORDER BY i.rule_id)
               FROM rule_set_items i
               JOIN rule_versions v ON v.rule_id = i.rule_id AND v.version = i.rule_version
               WHERE i.rule_set_version = s.version), '[]'::jsonb)),
       now()
FROM rule_sets s
ORDER BY s.version DESC
LIMIT 1;
