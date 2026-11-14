package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.RuleNotFoundException;
import br.com.motorfraude.rules.application.port.out.RuleRepository;
import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.domain.RuleSetItem;
import br.com.motorfraude.rules.domain.RuleStatus;
import java.util.ArrayList;
import java.util.List;

/** Coloca uma versão de regra no conjunto como ACTIVE ou SHADOW; se outra versão da mesma regra estava lá, desliga-a. */
final class PlaceInSet {

    private PlaceInSet() {}

    static RuleSetPublisher.Change apply(RuleRepository rules, RuleSet current, String actor, String ruleId, int versionNumber,
            RuleStatus target, String action) {
        var version = rules.findVersion(ruleId, versionNumber)
                .orElseThrow(() -> RuleNotFoundException.version(ruleId, versionNumber));
        var updated = target == RuleStatus.ACTIVE ? version.activate(actor) : version.transitionTo(target, actor);

        var updates = new ArrayList<>(List.of(updated));
        var existing = current.item(ruleId);
        if (existing != null && existing.ruleVersion() != versionNumber) {
            var superseded = rules.findVersion(ruleId, existing.ruleVersion())
                    .orElseThrow(() -> RuleNotFoundException.version(ruleId, existing.ruleVersion()));
            updates.add(superseded.disable());
        }
        var items = RuleSetPublisher.itemsWithout(current, ruleId);
        items.add(new RuleSetItem(ruleId, versionNumber, target));
        return new RuleSetPublisher.Change(action, "rule:" + ruleId + "@v" + versionNumber, items, current.scoring(), updates);
    }
}
