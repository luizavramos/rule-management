package br.com.motorfraude.rules.adapters.in.rest;

import br.com.motorfraude.rules.application.port.out.RuleValidator.CaseResult;
import br.com.motorfraude.rules.application.port.out.RuleValidator.TestCase;
import br.com.motorfraude.rules.application.usecase.ActiveRuleSet;
import br.com.motorfraude.rules.application.usecase.RuleContent;
import br.com.motorfraude.rules.application.usecase.ValidationReport;
import br.com.motorfraude.rules.domain.InvalidRuleDefinitionException;
import br.com.motorfraude.rules.domain.Rule;
import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.domain.RuleVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Corpos de requisição e resposta da API. Datas saem como texto ISO-8601 em UTC. */
final class ApiModels {

    private ApiModels() {}

    /** Corpo de criar regra, criar versão e validar. {@code ruleId} só vale na criação; {@code testCases}, só na validação. */
    record RuleRequest(String ruleId, String expression, Map<String, Object> params,
            Map<String, Map<String, Object>> paramOverrides, Integer weight, String reasonTemplate,
            List<TestCaseRequest> testCases) {

        /** Campos obrigatórios que faltam viram 400, todos juntos. */
        RuleContent toContent(boolean requireRuleId) {
            var missing = new ArrayList<String>();
            if (requireRuleId && ruleId == null) {
                missing.add("ruleId é obrigatório");
            }
            if (expression == null) {
                missing.add("expression é obrigatório");
            }
            if (weight == null) {
                missing.add("weight é obrigatório");
            }
            if (reasonTemplate == null) {
                missing.add("reasonTemplate é obrigatório");
            }
            if (!missing.isEmpty()) {
                throw new InvalidRuleDefinitionException(missing);
            }
            return new RuleContent(expression, params, paramOverrides, weight, reasonTemplate);
        }

        List<TestCase> cases() {
            return testCases == null ? List.of() : testCases.stream()
                    .map(c -> new TestCase(c.name(), c.context() == null ? Map.of() : c.context(), c.expectedHit()))
                    .toList();
        }
    }

    record TestCaseRequest(String name, Map<String, Object> context, boolean expectedHit) {}

    record ScoringRequest(Integer mediumThreshold, Integer highThreshold) {

        void requireBoth() {
            var missing = new ArrayList<String>();
            if (mediumThreshold == null) {
                missing.add("mediumThreshold é obrigatório");
            }
            if (highThreshold == null) {
                missing.add("highThreshold é obrigatório");
            }
            if (!missing.isEmpty()) {
                throw new InvalidRuleDefinitionException(missing);
            }
        }
    }

    record VersionResponse(String ruleId, int version, String status, String expression, Map<String, Object> params,
            Map<String, Map<String, Object>> paramOverrides, int weight, String reasonTemplate, String author,
            String approvedBy, String createdAt) {

        static VersionResponse of(RuleVersion v) {
            return new VersionResponse(v.ruleId(), v.version(), v.status().name(), v.expression(), v.params(), v.paramOverrides(),
                    v.weight(), v.reasonTemplate(), v.author(), v.approvedBy(), v.createdAt().toString());
        }
    }

    record RuleSummary(String ruleId, String createdBy, String createdAt) {

        static RuleSummary of(Rule r) {
            return new RuleSummary(r.ruleId(), r.createdBy(), r.createdAt().toString());
        }
    }

    record RuleHistoryResponse(String ruleId, String createdBy, String createdAt, List<VersionResponse> versions) {}

    record ScoringBody(int mediumThreshold, int highThreshold) {}

    record PublishedItem(String ruleId, int ruleVersion, String status) {}

    /** Resultado de uma publicação: o conjunto novo, só com as referências (o conteúdo completo sai em GET /active). */
    record RuleSetResponse(long version, String publishedAt, String publishedBy, ScoringBody scoring, List<PublishedItem> rules) {

        static RuleSetResponse of(RuleSet set) {
            return new RuleSetResponse(set.version(), set.publishedAt().toString(), set.publishedBy(),
                    new ScoringBody(set.scoring().mediumThreshold(), set.scoring().highThreshold()),
                    set.items().stream().map(i -> new PublishedItem(i.ruleId(), i.ruleVersion(), i.status().name())).toList());
        }
    }

    record ValidationResponse(boolean valid, List<String> errors, List<CaseResult> cases) {

        static ValidationResponse of(ValidationReport report) {
            return new ValidationResponse(report.valid(), report.errors(), report.cases());
        }
    }

    record ActiveRule(String ruleId, int version, String status, String expression, Map<String, Object> params,
            Map<String, Map<String, Object>> paramOverrides, int weight, String reasonTemplate) {}

    /** O corpo do contrato rule-set.v1 que o motor lê: o status vem do item do conjunto, não da versão da regra. */
    record ActiveRuleSetResponse(long version, String publishedAt, ScoringBody scoring, List<ActiveRule> rules) {

        static ActiveRuleSetResponse of(ActiveRuleSet active) {
            var set = active.ruleSet();
            var rules = new ArrayList<ActiveRule>();
            for (int i = 0; i < set.items().size(); i++) {
                var item = set.items().get(i);
                var v = active.versions().get(i);
                rules.add(new ActiveRule(v.ruleId(), v.version(), item.status().name(), v.expression(), v.params(),
                        v.paramOverrides(), v.weight(), v.reasonTemplate()));
            }
            return new ActiveRuleSetResponse(set.version(), set.publishedAt().toString(),
                    new ScoringBody(set.scoring().mediumThreshold(), set.scoring().highThreshold()), rules);
        }
    }
}
