package br.com.motorfraude.rules.adapters.controller;

import br.com.motorfraude.rules.application.gateway.RuleValidator.TestCase;
import br.com.motorfraude.rules.application.usecase.RuleContent;
import br.com.motorfraude.rules.domain.InvalidRuleDefinitionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ApiRequests {

    private ApiRequests() {}

    record RuleRequest(String ruleId, String expression, Map<String, Object> params,
            Map<String, Map<String, Object>> paramOverrides, Integer weight, String reasonTemplate,
            List<TestCaseRequest> testCases) {

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
}
