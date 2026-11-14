package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.RuleNotFoundException;
import br.com.motorfraude.rules.application.port.out.RuleRepository;
import br.com.motorfraude.rules.application.port.out.RuleSetRepository;
import java.util.Optional;

/** Devolve o conjunto ativo (o de maior versão) com o conteúdo completo de cada versão de regra. */
public final class GetActiveRuleSet {

    private final RuleSetRepository ruleSets;
    private final RuleRepository rules;

    public GetActiveRuleSet(RuleSetRepository ruleSets, RuleRepository rules) {
        this.ruleSets = ruleSets;
        this.rules = rules;
    }

    public Optional<ActiveRuleSet> execute() {
        return ruleSets.findActive().map(set -> new ActiveRuleSet(set, set.items().stream()
                .map(i -> rules.findVersion(i.ruleId(), i.ruleVersion())
                        .orElseThrow(() -> RuleNotFoundException.version(i.ruleId(), i.ruleVersion())))
                .toList()));
    }
}
