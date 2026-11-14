package br.com.motorfraude.rules.adapters.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.rules.domain.AuditEntry;
import br.com.motorfraude.rules.domain.Rule;
import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.domain.RuleSetItem;
import br.com.motorfraude.rules.domain.RuleStatus;
import br.com.motorfraude.rules.domain.RuleVersion;
import br.com.motorfraude.rules.domain.Scoring;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T215 — os repositórios JDBC contra um Postgres real. */
class RepositoriesIT extends PostgresSupport {

    private static final Instant T0 = Instant.parse("2026-11-14T12:00:00Z");

    private final PostgresRuleRepository rules = new PostgresRuleRepository(jdbc, json);
    private final PostgresRuleSetRepository ruleSets = new PostgresRuleSetRepository(jdbc);
    private final PostgresAuditLog audit = new PostgresAuditLog(jdbc, json);

    private static RuleVersion draft(String id, int version) {
        return RuleVersion.draft(id, version, "features.count5m > params.maxCount",
                Map.of("maxCount", 5, "fator", 1.5, "ligada", true, "nome", "abc"),
                Map.of("PJ", Map.of("maxCount", 30)), 25, "motivo {params.maxCount}", "analista", T0);
    }

    @Test
    @DisplayName("regra: cria uma vez; repetir o id devolve false e não sobrescreve")
    void regra() {
        assertThat(rules.insertRule(new Rule("R20", "analista", T0))).isTrue();
        assertThat(rules.insertRule(new Rule("R20", "outro", T0))).isFalse();

        assertThat(rules.findRule("R20")).contains(new Rule("R20", "analista", T0));
        assertThat(rules.findRule("NAO")).isEmpty();
        assertThat(rules.listRules()).extracting(Rule::ruleId).contains("R01", "R20").isSorted();
    }

    @Test
    @DisplayName("versão: ida e volta preserva expressão, parâmetros de todos os tipos, overrides e datas")
    void versao() {
        rules.insertRule(new Rule("R20", "analista", T0));
        var original = draft("R20", 1);

        rules.saveVersion(original);
        var lida = rules.findVersion("R20", 1).orElseThrow();

        assertThat(lida.expression()).isEqualTo(original.expression());
        assertThat(lida.params()).containsEntry("maxCount", 5).containsEntry("fator", 1.5).containsEntry("ligada", true).containsEntry("nome", "abc");
        assertThat(lida.paramOverrides()).isEqualTo(Map.of("PJ", Map.of("maxCount", 30)));
        assertThat(lida.weight()).isEqualTo(25);
        assertThat(lida.status()).isEqualTo(RuleStatus.DRAFT);
        assertThat(lida.author()).isEqualTo("analista");
        assertThat(lida.approvedBy()).isNull();
        assertThat(lida.createdAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("salvar de novo atualiza só status e aprovador; mudar o conteúdo é recusado pelo banco")
    void atualizaStatus() {
        rules.insertRule(new Rule("R20", "analista", T0));
        rules.saveVersion(draft("R20", 1));

        rules.saveVersion(draft("R20", 1).activate("coord"));

        var lida = rules.findVersion("R20", 1).orElseThrow();
        assertThat(lida.status()).isEqualTo(RuleStatus.ACTIVE);
        assertThat(lida.approvedBy()).isEqualTo("coord");
        var alterada = new RuleVersion("R20", 1, "tx.amount > 1", Map.of(), Map.of(), 25, "m", RuleStatus.ACTIVE, "analista", "coord", T0);
        assertThatThrownBy(() -> rules.saveVersion(alterada)).hasMessageContaining("imutavel");
    }

    @Test
    @DisplayName("próximo número de versão: 1 sem versões; senão o maior mais um; listagem em ordem")
    void numeracao() {
        rules.insertRule(new Rule("R20", "analista", T0));
        assertThat(rules.nextVersionNumber("R20")).isEqualTo(1);

        rules.saveVersion(draft("R20", 1));
        rules.saveVersion(draft("R20", 2));

        assertThat(rules.nextVersionNumber("R20")).isEqualTo(3);
        assertThat(rules.listVersions("R20")).extracting(RuleVersion::version).containsExactly(1, 2);
        assertThat(rules.findVersion("R20", 9)).isEmpty();
        assertThat(rules.nextVersionNumber("R01")).isEqualTo(2);
    }

    @Test
    @DisplayName("conjunto: o ativo é o de maior versão, e os itens voltam com o status de cada um")
    void conjuntos() {
        var seed = ruleSets.findActive().orElseThrow();
        assertThat(seed.version()).isEqualTo(1);
        assertThat(seed.items()).hasSize(8);
        assertThat(seed.scoring()).isEqualTo(new Scoring(40, 70));

        rules.saveVersion(RuleVersion.draft("R10", 2, "lists.counterpartyFlagged", Map.of(), Map.of(), 70, "m", "analista", T0).shadow());
        var novo = new RuleSet(2, T0, "coord", new Scoring(30, 60), List.of(
                new RuleSetItem("R01", 1, RuleStatus.ACTIVE), new RuleSetItem("R10", 2, RuleStatus.SHADOW)));
        ruleSets.save(novo);

        assertThat(ruleSets.findActive().orElseThrow()).isEqualTo(novo);
        assertThat(ruleSets.findByVersion(1).orElseThrow()).isEqualTo(seed);
        assertThat(ruleSets.findByVersion(99)).isEmpty();
    }

    @Test
    @DisplayName("conjunto com versão repetida é recusado")
    void versaoRepetida() {
        assertThatThrownBy(() -> ruleSets.save(new RuleSet(1, T0, "x", new Scoring(40, 70), List.of()))).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("auditoria: acrescenta com antes e depois e devolve as mais recentes primeiro")
    void auditoria() {
        audit.append(new AuditEntry("analista", "RULE_CREATED", "rule:R20", null, Map.of("version", 1), T0));
        audit.append(new AuditEntry("coord", "RULE_ACTIVATED", "rule:R20@v1", Map.of("ruleSetVersion", 1L), Map.of("ruleSetVersion", 2L), T0.plusSeconds(5)));

        var recentes = audit.recent(2);

        assertThat(recentes).extracting(AuditEntry::action).containsExactly("RULE_ACTIVATED", "RULE_CREATED");
        assertThat(recentes.get(0).actor()).isEqualTo("coord");
        assertThat(recentes.get(0).before()).containsEntry("ruleSetVersion", 1);
        assertThat(recentes.get(0).after()).containsEntry("ruleSetVersion", 2);
        assertThat(recentes.get(1).before()).isNull();
        assertThat(recentes.get(0).at()).isEqualTo(T0.plusSeconds(5));
        assertThat(audit.recent(1)).hasSize(1);
    }
}
