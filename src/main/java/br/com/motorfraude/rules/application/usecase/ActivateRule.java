package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.port.out.RuleRepository;
import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.domain.RuleStatus;

/** Ativa uma versão: publica o conjunto N+1 com ela ACTIVE (e sem a versão anterior da mesma regra). */
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
