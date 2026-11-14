package br.com.motorfraude.rules.application.gateway;

import br.com.motorfraude.rules.domain.RuleSet;
import java.util.Optional;
import java.util.OptionalLong;

public interface RuleSetRepository {

    Optional<RuleSet> findActive();

    OptionalLong activeVersion();

    Optional<RuleSet> findByVersion(long version);

    void save(RuleSet ruleSet);
}
