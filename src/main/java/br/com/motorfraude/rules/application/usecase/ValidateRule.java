package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.gateway.RuleValidator;
import br.com.motorfraude.rules.application.gateway.RuleValidator.TestCase;
import br.com.motorfraude.rules.domain.InvalidRuleDefinitionException;
import java.time.Instant;
import java.util.List;

/** Valida uma regra sem gravar nada: erros de compilação e o resultado de cada caso de teste. */
public final class ValidateRule {

    private final RuleValidator validator;

    public ValidateRule(RuleValidator validator) {
        this.validator = validator;
    }

    public ValidationReport execute(RuleContent content, List<TestCase> cases) {
        try {
            var draft = content.toDraft("TMP", 1, "validate", Instant.EPOCH);
            var errors = validator.validate(draft);
            if (!errors.isEmpty()) {
                return new ValidationReport(false, errors, List.of());
            }
            return new ValidationReport(true, List.of(), cases.isEmpty() ? List.of() : validator.runCases(draft, cases));
        } catch (InvalidRuleDefinitionException e) {
            return new ValidationReport(false, e.errors(), List.of());
        }
    }
}
