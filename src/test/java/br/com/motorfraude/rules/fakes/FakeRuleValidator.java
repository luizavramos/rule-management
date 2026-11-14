package br.com.motorfraude.rules.fakes;

import br.com.motorfraude.rules.application.gateway.RuleValidator;
import br.com.motorfraude.rules.domain.RuleVersion;
import br.com.motorfraude.rules.domain.Scoring;
import java.util.ArrayList;
import java.util.List;

public final class FakeRuleValidator implements RuleValidator {

    @Override
    public List<String> validate(RuleVersion version) {
        return version.expression().contains("INVALID") ? List.of("erro de sintaxe: expressão inválida") : List.of();
    }

    @Override
    public List<SetIssue> validateSet(Scoring scoring, List<RuleVersion> versions) {
        var issues = new ArrayList<SetIssue>();
        for (RuleVersion version : versions) {
            var errors = validate(version);
            if (!errors.isEmpty()) {
                issues.add(new SetIssue(version.ruleId(), errors));
            }
        }
        return issues;
    }

    @Override
    public List<CaseResult> runCases(RuleVersion version, List<TestCase> cases) {
        var results = new ArrayList<CaseResult>();
        for (TestCase c : cases) {
            boolean actual = version.expression().contains("HIT");
            results.add(new CaseResult(c.name(), c.expectedHit(), actual, actual == c.expectedHit(), null));
        }
        return results;
    }
}
