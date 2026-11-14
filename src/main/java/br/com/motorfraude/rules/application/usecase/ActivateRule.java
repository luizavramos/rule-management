package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.gateway.RuleRepository;
import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.domain.RuleStatus;

public final class ActivateRule {

    private final RuleRepository rules;
    private final RuleSetPublisher publisher;

    public ActivateRule(RuleRepository rules, RuleSetPublisher publisher) {
        this.rules = rules;
        this.publisher = publisher;
    }

    public RuleSet execute(String actor, String ruleId, int version) {
        return publisher.publish(actor, current ->
                PlaceInSet.apply(rules, current, actor, ruleId, version, RuleStatus.ACTIVE, "RULE_ACTIVATED"));
    }
}
