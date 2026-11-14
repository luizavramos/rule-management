package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.gateway.RuleRepository;
import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.domain.RuleStatus;

/** Modo sombra: a versão entra no conjunto como SHADOW, o motor avalia e registra, mas ela não soma pontos. */
public final class ShadowRule {

    private final RuleRepository rules;
    private final RuleSetPublisher publisher;

    public ShadowRule(RuleRepository rules, RuleSetPublisher publisher) {
        this.rules = rules;
        this.publisher = publisher;
    }

    public RuleSet execute(String actor, String ruleId, int version) {
        return publisher.publish(actor, current ->
                PlaceInSet.apply(rules, current, actor, ruleId, version, RuleStatus.SHADOW, "RULE_SHADOWED"));
    }
}
