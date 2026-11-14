package br.com.motorfraude.rules.fakes;

import br.com.motorfraude.rules.application.gateway.RuleValidator;
import br.com.motorfraude.rules.application.usecase.ActivateRule;
import br.com.motorfraude.rules.application.usecase.CreateRule;
import br.com.motorfraude.rules.application.usecase.CreateRuleVersion;
import br.com.motorfraude.rules.application.usecase.DisableRule;
import br.com.motorfraude.rules.application.usecase.GetActiveRuleSet;
import br.com.motorfraude.rules.application.usecase.RollbackRuleSet;
import br.com.motorfraude.rules.application.usecase.RuleContent;
import br.com.motorfraude.rules.application.usecase.RuleSetPublisher;
import br.com.motorfraude.rules.application.usecase.ShadowRule;
import br.com.motorfraude.rules.application.usecase.UpdateScoring;
import br.com.motorfraude.rules.application.usecase.ValidateRule;
import br.com.motorfraude.rules.domain.RuleSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

/** Monta os casos de uso com fakes em memória e um relógio fixo; o validador pode ser trocado pelo real. */
public final class Fixture {

    public static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    public final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    public final InMemoryRuleRepository rules = new InMemoryRuleRepository();
    public final InMemoryRuleSetRepository ruleSets = new InMemoryRuleSetRepository();
    public final InMemoryAuditLog audit = new InMemoryAuditLog();
    public final LockUnitOfWork unitOfWork = new LockUnitOfWork();
    public final RuleValidator validator;

    public final RuleSetPublisher publisher;
    public final CreateRule createRule;
    public final CreateRuleVersion createRuleVersion;
    public final ValidateRule validateRule;
    public final ActivateRule activateRule;
    public final ShadowRule shadowRule;
    public final DisableRule disableRule;
    public final RollbackRuleSet rollbackRuleSet;
    public final UpdateScoring updateScoring;
    public final GetActiveRuleSet getActiveRuleSet;

    public Fixture() {
        this(new FakeRuleValidator());
    }

    public Fixture(RuleValidator validator) {
        this.validator = validator;
        this.publisher = new RuleSetPublisher(rules, ruleSets, audit, validator, unitOfWork, clock);
        this.createRule = new CreateRule(rules, audit, validator, unitOfWork, clock);
        this.createRuleVersion = new CreateRuleVersion(rules, audit, validator, unitOfWork, clock);
        this.validateRule = new ValidateRule(validator);
        this.activateRule = new ActivateRule(rules, publisher);
        this.shadowRule = new ShadowRule(rules, publisher);
        this.disableRule = new DisableRule(rules, publisher);
        this.rollbackRuleSet = new RollbackRuleSet(rules, ruleSets, publisher);
        this.updateScoring = new UpdateScoring(publisher);
        this.getActiveRuleSet = new GetActiveRuleSet(ruleSets, rules);
    }

    public static RuleContent content(String expression) {
        return new RuleContent(expression, Map.of("maxCount", 5), Map.of(), 25, "motivo");
    }

    /** Cria a regra e a ativa, devolvendo o conjunto publicado. */
    public RuleSet createAndActivate(String ruleId, String expression) {
        createRule.execute("analista", ruleId, content(expression));
        return activateRule.execute("coord", ruleId, 1);
    }
}
