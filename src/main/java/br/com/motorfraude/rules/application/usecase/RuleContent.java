package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.domain.RuleVersion;
import java.time.Instant;
import java.util.Map;

/** Conteúdo editável de uma versão de regra, como chega pela API. */
public record RuleContent(String expression, Map<String, Object> params,
        Map<String, Map<String, Object>> paramOverrides, int weight, String reasonTemplate) {

    RuleVersion toDraft(String ruleId, int version, String author, Instant createdAt) {
        return RuleVersion.draft(ruleId, version, expression, params, paramOverrides, weight, reasonTemplate, author, createdAt);
    }
}
