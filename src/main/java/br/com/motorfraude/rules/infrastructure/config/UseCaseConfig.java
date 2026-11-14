package br.com.motorfraude.rules.infrastructure.config;

import br.com.motorfraude.rules.adapters.gateway.kernel.KernelRuleValidator;
import br.com.motorfraude.rules.application.gateway.AuditLog;
import br.com.motorfraude.rules.application.gateway.IdempotencyStore;
import br.com.motorfraude.rules.application.gateway.RuleMetrics;
import br.com.motorfraude.rules.application.gateway.RuleRepository;
import br.com.motorfraude.rules.application.gateway.RuleSetOutbox;
import br.com.motorfraude.rules.application.gateway.RuleSetRepository;
import br.com.motorfraude.rules.application.gateway.RuleValidator;
import br.com.motorfraude.rules.application.gateway.UnitOfWork;
import br.com.motorfraude.rules.application.usecase.ActivateRule;
import br.com.motorfraude.rules.application.usecase.CreateRule;
import br.com.motorfraude.rules.application.usecase.CreateRuleVersion;
import br.com.motorfraude.rules.application.usecase.DisableRule;
import br.com.motorfraude.rules.application.usecase.GetActiveRuleSet;
import br.com.motorfraude.rules.application.usecase.GetRule;
import br.com.motorfraude.rules.application.usecase.IdempotencyGuard;
import br.com.motorfraude.rules.application.usecase.ListRules;
import br.com.motorfraude.rules.application.usecase.RollbackRuleSet;
import br.com.motorfraude.rules.application.usecase.RuleSetPublisher;
import br.com.motorfraude.rules.application.usecase.ShadowRule;
import br.com.motorfraude.rules.application.usecase.UpdateScoring;
import br.com.motorfraude.rules.application.usecase.ValidateRule;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RuleValidator ruleValidator() {
        return new KernelRuleValidator();
    }

    @Bean
    RuleSetPublisher ruleSetPublisher(RuleRepository rules, RuleSetRepository ruleSets, AuditLog audit, RuleValidator validator,
            UnitOfWork unitOfWork, Clock clock, RuleMetrics metrics, RuleSetOutbox outbox) {
        return new RuleSetPublisher(rules, ruleSets, audit, validator, unitOfWork, clock, metrics, outbox);
    }

    @Bean
    CreateRule createRule(RuleRepository rules, AuditLog audit, RuleValidator validator, UnitOfWork unitOfWork, Clock clock,
            RuleMetrics metrics) {
        return new CreateRule(rules, audit, validator, unitOfWork, clock, metrics);
    }

    @Bean
    CreateRuleVersion createRuleVersion(RuleRepository rules, AuditLog audit, RuleValidator validator, UnitOfWork unitOfWork, Clock clock,
            RuleMetrics metrics) {
        return new CreateRuleVersion(rules, audit, validator, unitOfWork, clock, metrics);
    }

    @Bean
    ValidateRule validateRule(RuleValidator validator) {
        return new ValidateRule(validator);
    }

    @Bean
    ActivateRule activateRule(RuleRepository rules, RuleSetPublisher publisher) {
        return new ActivateRule(rules, publisher);
    }

    @Bean
    ShadowRule shadowRule(RuleRepository rules, RuleSetPublisher publisher) {
        return new ShadowRule(rules, publisher);
    }

    @Bean
    DisableRule disableRule(RuleRepository rules, RuleSetPublisher publisher) {
        return new DisableRule(rules, publisher);
    }

    @Bean
    RollbackRuleSet rollbackRuleSet(RuleRepository rules, RuleSetRepository ruleSets, RuleSetPublisher publisher) {
        return new RollbackRuleSet(rules, ruleSets, publisher);
    }

    @Bean
    UpdateScoring updateScoring(RuleSetPublisher publisher) {
        return new UpdateScoring(publisher);
    }

    @Bean
    ListRules listRules(RuleRepository rules) {
        return new ListRules(rules);
    }

    @Bean
    GetRule getRule(RuleRepository rules) {
        return new GetRule(rules);
    }

    @Bean
    IdempotencyGuard idempotencyGuard(IdempotencyStore store, Clock clock) {
        return new IdempotencyGuard(store, clock);
    }

    @Bean
    GetActiveRuleSet getActiveRuleSet(RuleSetRepository ruleSets, RuleRepository rules) {
        return new GetActiveRuleSet(ruleSets, rules);
    }
}
