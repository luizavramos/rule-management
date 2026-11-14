package br.com.motorfraude.rules.fakes;

import br.com.motorfraude.rules.application.gateway.RuleSetRepository;
import br.com.motorfraude.rules.domain.RuleSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake em memória de {@link RuleSetRepository}. Recusa versão repetida, como a chave primária do banco. */
public final class InMemoryRuleSetRepository implements RuleSetRepository {

    private final List<RuleSet> sets = new CopyOnWriteArrayList<>();

    @Override
    public Optional<RuleSet> findActive() {
        return sets.stream().max(Comparator.comparingLong(RuleSet::version));
    }

    @Override
    public OptionalLong activeVersion() {
        return sets.stream().mapToLong(RuleSet::version).max();
    }

    @Override
    public Optional<RuleSet> findByVersion(long version) {
        return sets.stream().filter(s -> s.version() == version).findFirst();
    }

    @Override
    public void save(RuleSet ruleSet) {
        if (findByVersion(ruleSet.version()).isPresent()) {
            throw new IllegalStateException("versão de conjunto repetida: " + ruleSet.version());
        }
        sets.add(ruleSet);
    }

    public List<RuleSet> all() {
        return sets.stream().sorted(Comparator.comparingLong(RuleSet::version)).toList();
    }
}
