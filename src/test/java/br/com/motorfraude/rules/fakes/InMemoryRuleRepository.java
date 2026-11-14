package br.com.motorfraude.rules.fakes;

import br.com.motorfraude.rules.application.port.out.RuleRepository;
import br.com.motorfraude.rules.domain.Rule;
import br.com.motorfraude.rules.domain.RuleVersion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Fake em memória de {@link RuleRepository}. */
public final class InMemoryRuleRepository implements RuleRepository {

    private final Map<String, Rule> rules = new ConcurrentHashMap<>();
    private final Map<String, RuleVersion> versions = new ConcurrentHashMap<>();

    private static String key(String ruleId, int version) {
        return ruleId + "#" + version;
    }

    @Override
    public boolean insertRule(Rule rule) {
        return rules.putIfAbsent(rule.ruleId(), rule) == null;
    }

    @Override
    public Optional<Rule> findRule(String ruleId) {
        return Optional.ofNullable(rules.get(ruleId));
    }

    @Override
    public List<Rule> listRules() {
        return rules.values().stream().sorted(Comparator.comparing(Rule::ruleId)).toList();
    }

    @Override
    public int nextVersionNumber(String ruleId) {
        return listVersions(ruleId).stream().mapToInt(RuleVersion::version).max().orElse(0) + 1;
    }

    @Override
    public void saveVersion(RuleVersion version) {
        versions.put(key(version.ruleId(), version.version()), version);
    }

    @Override
    public Optional<RuleVersion> findVersion(String ruleId, int version) {
        return Optional.ofNullable(versions.get(key(ruleId, version)));
    }

    @Override
    public List<RuleVersion> listVersions(String ruleId) {
        var list = new ArrayList<>(versions.values().stream().filter(v -> v.ruleId().equals(ruleId)).toList());
        list.sort(Comparator.comparingInt(RuleVersion::version));
        return list;
    }
}
