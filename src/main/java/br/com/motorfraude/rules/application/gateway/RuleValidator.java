package br.com.motorfraude.rules.application.gateway;

import br.com.motorfraude.rules.domain.RuleVersion;
import br.com.motorfraude.rules.domain.Scoring;
import java.util.List;
import java.util.Map;

/** Valida regras com a mesma linguagem que o motor usa (implementado sobre o rules-kernel). */
public interface RuleValidator {

    List<String> validate(RuleVersion version);

    List<SetIssue> validateSet(Scoring scoring, List<RuleVersion> versions);

    List<CaseResult> runCases(RuleVersion version, List<TestCase> cases);

    record SetIssue(String ruleId, List<String> errors) {}

    record TestCase(String name, Map<String, Object> context, boolean expectedHit) {}

    record CaseResult(String name, boolean expectedHit, boolean actualHit, boolean passed, String error) {}
}
