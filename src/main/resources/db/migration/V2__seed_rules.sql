
INSERT INTO rules (rule_id, created_by, created_at)
SELECT r, 'system', now() FROM unnest(ARRAY['R01', 'R03', 'R04', 'R05', 'R07', 'R09', 'R10', 'R12']) AS r;

INSERT INTO rule_versions (rule_id, version, expression, params, param_overrides, weight, reason_template, status, author, approved_by, created_at) VALUES
('R01', 1,
 'profile.hoursSinceCredentialChange >= 0 && profile.hoursSinceCredentialChange < params.hours && tx.amount > params.minAmount',
 '{"hours": 24, "minAmount": 100000}', '{}', 35,
 'Credencial alterada há {profile.hoursSinceCredentialChange} h e valor de {tx.amount} centavos (acima de {params.minAmount})',
 'ACTIVE', 'system', 'system', now()),
('R03', 1,
 'features.isNewBeneficiary && tx.amount > params.minAmount',
 '{"minAmount": 200000}', '{}', 25,
 'Beneficiário novo com valor de {tx.amount} centavos (acima de {params.minAmount})',
 'ACTIVE', 'system', 'system', now()),
('R04', 1,
 'features.count30d >= params.minHistory && tx.amount > max(params.floor, features.avgAmount30d * params.multiplier)',
 '{"minHistory": 5, "floor": 100000, "multiplier": 5}', '{}', 35,
 'Valor de {tx.amount} centavos acima de {params.multiplier} vezes a média de 30 dias ({features.avgAmount30d})',
 'ACTIVE', 'system', 'system', now()),
('R05', 1,
 'tx.hourOfDay < params.endHour && tx.amount > params.minAmount',
 '{"endHour": 6, "minAmount": 100000}', '{}', 15,
 'Transação de madrugada (hora {tx.hourOfDay}) com valor de {tx.amount} centavos',
 'ACTIVE', 'system', 'system', now()),
('R07', 1,
 'features.count5m > params.maxCount',
 '{"maxCount": 5}', '{"PJ": {"maxCount": 30}}', 25,
 '{features.count5m} transações em 5 min (limite {params.maxCount})',
 'ACTIVE', 'system', 'system', now()),
('R09', 1,
 'features.distinctBeneficiaries24h > params.maxDistinct',
 '{"maxDistinct": 10}', '{"PJ": {"maxDistinct": 100}}', 30,
 '{features.distinctBeneficiaries24h} beneficiários distintos em 24 h (limite {params.maxDistinct})',
 'ACTIVE', 'system', 'system', now()),
('R10', 1,
 'lists.counterpartyFlagged',
 '{}', '{}', 70,
 'Contraparte em lista de bloqueio',
 'ACTIVE', 'system', 'system', now()),
('R12', 1,
 'profile.hoursSinceCredentialChange >= 0 && profile.hoursSinceCredentialChange < 24 && features.isNewDevice && features.isNewBeneficiary',
 '{}', '{}', 30,
 'Credencial alterada há menos de 24 h, dispositivo novo e beneficiário novo',
 'ACTIVE', 'system', 'system', now());

INSERT INTO rule_sets (version, published_at, published_by, medium_threshold, high_threshold)
VALUES (1, now(), 'system', 40, 70);

INSERT INTO rule_set_items (rule_set_version, rule_id, rule_version, status)
SELECT 1, rule_id, 1, 'ACTIVE' FROM rule_versions;

INSERT INTO audit_log (actor, action, entity, before, after, at)
VALUES ('system', 'RULESET_SEEDED', 'ruleset:1', NULL,
        '{"ruleSetVersion": 1, "scoring": {"mediumThreshold": 40, "highThreshold": 70}, "rules": ["R01", "R03", "R04", "R05", "R07", "R09", "R10", "R12"]}',
        now());
