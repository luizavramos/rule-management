package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.RuleNotFoundException;
import br.com.motorfraude.rules.application.gateway.RuleRepository;
import br.com.motorfraude.rules.application.gateway.RuleSetRepository;
import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.domain.RuleSetItem;
import br.com.motorfraude.rules.domain.RuleStatus;
import br.com.motorfraude.rules.domain.RuleVersion;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Publica um conjunto novo (N+k) com o mesmo conteúdo do conjunto escolhido. O histórico não é apagado. O status das
 * versões acompanha: as que estavam no conjunto atual e não estão no escolhido ficam DISABLED; as do escolhido
 * voltam ao status que tinham nele. Quem aprovou a versão originalmente continua sendo o aprovador.
 */
public final class RollbackRuleSet {

    private final RuleRepository rules;
    private final RuleSetRepository ruleSets;
    private final RuleSetPublisher publisher;

    public RollbackRuleSet(RuleRepository rules, RuleSetRepository ruleSets, RuleSetPublisher publisher) {
        this.rules = rules;
        this.ruleSets = ruleSets;
        this.publisher = publisher;
    }

    public RuleSet execute(String actor, long targetVersion) {
        return publisher.publish(actor, current -> {
            RuleSet target = ruleSets.findByVersion(targetVersion).orElseThrow(() -> RuleNotFoundException.ruleSet(targetVersion));

            var desired = new LinkedHashMap<String, RuleStatus>();
            for (RuleSetItem item : current.items()) {
                desired.put(item.ruleId() + "#" + item.ruleVersion(), RuleStatus.DISABLED);
            }
            for (RuleSetItem item : target.items()) {
                desired.put(item.ruleId() + "#" + item.ruleVersion(), item.status());
            }

            var updates = new ArrayList<RuleVersion>();
            desired.forEach((key, status) -> {
                String ruleId = key.substring(0, key.indexOf('#'));
                int number = Integer.parseInt(key.substring(key.indexOf('#') + 1));
                RuleVersion version = rules.findVersion(ruleId, number).orElseThrow(() -> RuleNotFoundException.version(ruleId, number));
                if (version.status() != status) {
                    String approver = version.approvedBy() != null ? version.approvedBy() : actor;
                    updates.add(version.transitionTo(status, approver));
                }
            });
            return new RuleSetPublisher.Change("RULESET_ROLLED_BACK", "ruleset:rollback-to-v" + targetVersion,
                    target.items(), target.scoring(), updates);
        });
    }
}
