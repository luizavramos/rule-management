package br.com.motorfraude.rules.application;

import br.com.motorfraude.rules.application.gateway.RuleValidator.SetIssue;
import java.util.List;

/** O conjunto resultante não compila, então nada é publicado (vira 422). */
public final class RuleSetInvalidException extends RuntimeException {

    private final transient List<SetIssue> issues;

    public RuleSetInvalidException(List<SetIssue> issues) {
        super("o conjunto de regras não compila: " + issues.stream().map(i -> i.ruleId() + " " + i.errors()).reduce((a, b) -> a + "; " + b).orElse(""));
        this.issues = List.copyOf(issues);
    }

    public List<SetIssue> issues() {
        return issues;
    }
}
