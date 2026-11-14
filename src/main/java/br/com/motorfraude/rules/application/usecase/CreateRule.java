package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.RuleAlreadyExistsException;
import br.com.motorfraude.rules.application.RuleInvalidException;
import br.com.motorfraude.rules.application.port.out.AuditLog;
import br.com.motorfraude.rules.application.port.out.RuleRepository;
import br.com.motorfraude.rules.application.port.out.RuleValidator;
import br.com.motorfraude.rules.application.port.out.UnitOfWork;
import br.com.motorfraude.rules.domain.AuditEntry;
import br.com.motorfraude.rules.domain.Rule;
import br.com.motorfraude.rules.domain.RuleVersion;
import java.time.Clock;

/** Cria a regra e a versão 1 em DRAFT. Expressão que não compila é recusada antes de gravar qualquer coisa. */
public final class CreateRule {

    private final RuleRepository rules;
    private final AuditLog audit;
    private final RuleValidator validator;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public CreateRule(RuleRepository rules, AuditLog audit, RuleValidator validator, UnitOfWork unitOfWork, Clock clock) {
        this.rules = rules;
        this.audit = audit;
        this.validator = validator;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public RuleVersion execute(String actor, String ruleId, RuleContent content) {
        var now = clock.instant();
        RuleVersion draft = content.toDraft(ruleId, 1, actor, now);
        var errors = validator.validate(draft);
        if (!errors.isEmpty()) {
            throw new RuleInvalidException(errors);
        }
        return unitOfWork.serializable(() -> {
            if (!rules.insertRule(new Rule(ruleId, actor, now))) {
                throw new RuleAlreadyExistsException(ruleId);
            }
            rules.saveVersion(draft);
            audit.append(new AuditEntry(actor, "RULE_CREATED", "rule:" + ruleId, null, AuditMaps.of(draft), now));
            return draft;
        });
    }
}
