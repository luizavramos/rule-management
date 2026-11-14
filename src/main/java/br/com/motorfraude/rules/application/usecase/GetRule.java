package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.RuleNotFoundException;
import br.com.motorfraude.rules.application.gateway.RuleRepository;

public final class GetRule {

    private final RuleRepository rules;

    public GetRule(RuleRepository rules) {
        this.rules = rules;
    }

    public RuleHistory execute(String ruleId) {
        var rule = rules.findRule(ruleId).orElseThrow(() -> RuleNotFoundException.rule(ruleId));
        return new RuleHistory(rule, rules.listVersions(ruleId));
    }
}
