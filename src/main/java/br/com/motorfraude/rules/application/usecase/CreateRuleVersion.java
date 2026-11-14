package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.RuleInvalidException;
import br.com.motorfraude.rules.application.RuleNotFoundException;
import br.com.motorfraude.rules.application.gateway.AuditLog;
import br.com.motorfraude.rules.application.gateway.RuleMetrics;
import br.com.motorfraude.rules.application.gateway.RuleRepository;
import br.com.motorfraude.rules.application.gateway.RuleValidator;
import br.com.motorfraude.rules.application.gateway.UnitOfWork;
import br.com.motorfraude.rules.domain.AuditEntry;
import br.com.motorfraude.rules.domain.RuleVersion;
import java.time.Clock;

public final class CreateRuleVersion {

    private final RuleRepository rules;
    private final AuditLog audit;
    private final RuleValidator validator;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final RuleMetrics metrics;

    public CreateRuleVersion(RuleRepository rules, AuditLog audit, RuleValidator validator, UnitOfWork unitOfWork, Clock clock,
            RuleMetrics metrics) {
        this.rules = rules;
        this.audit = audit;
        this.validator = validator;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.metrics = metrics;
    }

    public RuleVersion execute(String actor, String ruleId, RuleContent content) {
        try {
            return create(actor, ruleId, content);
        } catch (RuleInvalidException e) {
            metrics.compilationFailed("rule");
            throw e;
        }
    }

    private RuleVersion create(String actor, String ruleId, RuleContent content) {
        return unitOfWork.serializable(() -> {
            rules.findRule(ruleId).orElseThrow(() -> RuleNotFoundException.rule(ruleId));
            var now = clock.instant();
            RuleVersion draft = content.toDraft(ruleId, rules.nextVersionNumber(ruleId), actor, now);
            var errors = validator.validate(draft);
            if (!errors.isEmpty()) {
                throw new RuleInvalidException(errors);
            }
            rules.saveVersion(draft);
            audit.append(new AuditEntry(actor, "RULE_VERSION_CREATED", "rule:" + ruleId + "@v" + draft.version(),
                    null, AuditMaps.of(draft), now));
            return draft;
        });
    }
}
