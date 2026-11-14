package br.com.motorfraude.rules.application.gateway;

import br.com.motorfraude.rules.domain.RuleVersion;
import br.com.motorfraude.rules.domain.Scoring;
import java.util.List;
import java.util.Map;

/** Valida regras com a mesma linguagem que o motor usa (implementado sobre o rules-kernel). */
public interface RuleValidator {

    /** Erros de sintaxe, tipo e complexidade de uma regra; vazio se for válida. */
    List<String> validate(RuleVersion version);

    /** Compila o conjunto inteiro (versões ACTIVE e SHADOW); devolve os problemas por regra, vazio se compilou. */
    List<SetIssue> validateSet(Scoring scoring, List<RuleVersion> versions);

    /** Avalia a regra contra cada caso de teste (valores das features) e compara com o resultado esperado. */
    List<CaseResult> runCases(RuleVersion version, List<TestCase> cases);

    record SetIssue(String ruleId, List<String> errors) {}

    record TestCase(String name, Map<String, Object> context, boolean expectedHit) {}

    record CaseResult(String name, boolean expectedHit, boolean actualHit, boolean passed, String error) {}
}
