package br.com.motorfraude.rules.application.gateway;

import br.com.motorfraude.rules.domain.Rule;
import br.com.motorfraude.rules.domain.RuleVersion;
import java.util.List;
import java.util.Optional;

public interface RuleRepository {

    boolean insertRule(Rule rule);

    Optional<Rule> findRule(String ruleId);

    List<Rule> listRules();

    int nextVersionNumber(String ruleId);

    void saveVersion(RuleVersion version);

    Optional<RuleVersion> findVersion(String ruleId, int version);

    List<RuleVersion> listVersions(String ruleId);
}
