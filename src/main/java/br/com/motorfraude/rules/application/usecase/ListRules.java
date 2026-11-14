package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.port.out.RuleRepository;
import br.com.motorfraude.rules.domain.Rule;
import java.util.List;

/** Lista as regras, em ordem de id. */
public final class ListRules {

    private final RuleRepository rules;

    public ListRules(RuleRepository rules) {
        this.rules = rules;
    }

    public List<Rule> execute() {
        return rules.listRules();
    }
}
