package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.RuleNotFoundException;
import br.com.motorfraude.rules.application.gateway.RuleRepository;
import br.com.motorfraude.rules.application.gateway.RuleSetRepository;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Devolve o conjunto ativo (o de maior versão) com o conteúdo completo de cada versão de regra. O motor pergunta a cada
 * 30 s, então a resposta fica em cache e só é refeita quando a versão muda: a pergunta barata é só o número da versão.
 * Seguro com várias threads: o cache é um valor imutável trocado de uma vez. Como cada instância confere a versão no
 * banco, o cache nunca fica velho, mesmo com mais de uma instância do serviço.
 */
public final class GetActiveRuleSet {

    private final RuleSetRepository ruleSets;
    private final RuleRepository rules;
    private volatile ActiveRuleSet cached;

    public GetActiveRuleSet(RuleSetRepository ruleSets, RuleRepository rules) {
        this.ruleSets = ruleSets;
        this.rules = rules;
    }

    /** Número da versão ativa, sem ler o conjunto. Serve para responder 304 barato. */
    public OptionalLong currentVersion() {
        return ruleSets.activeVersion();
    }

    public Optional<ActiveRuleSet> execute() {
        var version = ruleSets.activeVersion();
        if (version.isEmpty()) {
            cached = null;
            return Optional.empty();
        }
        var snapshot = cached;
        if (snapshot != null && snapshot.ruleSet().version() == version.getAsLong()) {
            return Optional.of(snapshot);
        }
        var loaded = ruleSets.findActive().map(set -> new ActiveRuleSet(set, set.items().stream()
                .map(i -> rules.findVersion(i.ruleId(), i.ruleVersion())
                        .orElseThrow(() -> RuleNotFoundException.version(i.ruleId(), i.ruleVersion())))
                .toList()));
        cached = loaded.orElse(null);
        return loaded;
    }
}
