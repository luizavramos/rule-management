package br.com.motorfraude.rules.application.port.out;

import br.com.motorfraude.rules.domain.Rule;
import br.com.motorfraude.rules.domain.RuleVersion;
import java.util.List;
import java.util.Optional;

/** Regras e suas versões. O conteúdo de uma versão nunca é alterado; só o status (por {@link #saveVersion}). */
public interface RuleRepository {

    /** Cria a regra; devolve false se o id já existe (sem sobrescrever). */
    boolean insertRule(Rule rule);

    Optional<Rule> findRule(String ruleId);

    List<Rule> listRules();

    /** Número da próxima versão da regra: 1 se ainda não há versões. */
    int nextVersionNumber(String ruleId);

    /** Insere a versão ou atualiza o status de uma já existente. */
    void saveVersion(RuleVersion version);

    Optional<RuleVersion> findVersion(String ruleId, int version);

    List<RuleVersion> listVersions(String ruleId);
}
