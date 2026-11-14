package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.RuleNotFoundException;
import br.com.motorfraude.rules.application.RuleNotInSetException;
import br.com.motorfraude.rules.application.gateway.RuleRepository;
import br.com.motorfraude.rules.domain.RuleSet;
import java.util.List;

/** Kill switch: publica o conjunto N+1 sem a regra e marca a versão que estava nele como DISABLED. */
public final class DisableRule {

    private final RuleRepository rules;
    private final RuleSetPublisher publisher;

    public DisableRule(RuleRepository rules, RuleSetPublisher publisher) {
        this.rules = rules;
        this.publisher = publisher;
    }

    public RuleSet execute(String actor, String ruleId) {
        return publisher.publish(actor, current -> {
            var item = current.item(ruleId);
            if (item == null) {
                rules.findRule(ruleId).orElseThrow(() -> RuleNotFoundException.rule(ruleId));
                throw new RuleNotInSetException(ruleId);
            }
            var version = rules.findVersion(ruleId, item.ruleVersion())
                    .orElseThrow(() -> RuleNotFoundException.version(ruleId, item.ruleVersion()));
            return new RuleSetPublisher.Change("RULE_DISABLED", "rule:" + ruleId + "@v" + item.ruleVersion(),
                    RuleSetPublisher.itemsWithout(current, ruleId), current.scoring(), List.of(version.disable()));
        });
    }
}
