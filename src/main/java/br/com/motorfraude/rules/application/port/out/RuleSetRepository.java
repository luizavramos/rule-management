package br.com.motorfraude.rules.application.port.out;

import br.com.motorfraude.rules.domain.RuleSet;
import java.util.Optional;

/** Conjuntos publicados. Nunca são alterados nem apagados: o histórico fica inteiro. */
public interface RuleSetRepository {

    /** O conjunto ativo é o de maior versão. */
    Optional<RuleSet> findActive();

    Optional<RuleSet> findByVersion(long version);

    void save(RuleSet ruleSet);
}
