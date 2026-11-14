package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.gateway.RuleRepository;
import br.com.motorfraude.rules.domain.Rule;
import java.util.List;

public final class ListRules {

    private final RuleRepository rules;

    public ListRules(RuleRepository rules) {
        this.rules = rules;
    }

    public List<Rule> execute() {
        return rules.listRules();
    }
}
