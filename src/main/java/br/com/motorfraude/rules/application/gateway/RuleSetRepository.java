package br.com.motorfraude.rules.application.gateway;

import br.com.motorfraude.rules.domain.RuleSet;
import java.util.Optional;
import java.util.OptionalLong;

/** Conjuntos publicados. Nunca são alterados nem apagados: o histórico fica inteiro. */
public interface RuleSetRepository {

    /** O conjunto ativo é o de maior versão. */
    Optional<RuleSet> findActive();

    /** Só o número da versão ativa: a pergunta barata, sem ler os itens. Vazio se nada foi publicado. */
    OptionalLong activeVersion();

    Optional<RuleSet> findByVersion(long version);

    void save(RuleSet ruleSet);
}
