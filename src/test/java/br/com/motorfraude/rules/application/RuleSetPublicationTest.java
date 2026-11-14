package br.com.motorfraude.rules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.rules.application.usecase.RuleContent;
import br.com.motorfraude.rules.domain.InvalidRuleDefinitionException;
import br.com.motorfraude.rules.domain.InvalidTransitionException;
import br.com.motorfraude.rules.domain.RuleSetItem;
import br.com.motorfraude.rules.domain.RuleStatus;
import br.com.motorfraude.rules.domain.Scoring;
import br.com.motorfraude.rules.fakes.Fixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleSetPublicationTest {

    private final Fixture f = new Fixture();

    private List<RuleSetItem> itemsOf(long version) {
        return f.ruleSets.findByVersion(version).orElseThrow().items();
    }

    @Test
    @DisplayName("ativar publica o conjunto N+1 com a regra ACTIVE, aprovador registrado, e audita antes e depois")
    void ativar() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));

        var set = f.activateRule.execute("coord", "R07", 1);

        assertThat(set.version()).isEqualTo(1);
        assertThat(set.items()).containsExactly(new RuleSetItem("R07", 1, RuleStatus.ACTIVE));
        assertThat(set.publishedBy()).isEqualTo("coord");
        assertThat(set.publishedAt()).isEqualTo(Fixture.NOW);
        assertThat(f.ruleSets.findActive()).contains(set);
        var version = f.rules.findVersion("R07", 1).orElseThrow();
        assertThat(version.status()).isEqualTo(RuleStatus.ACTIVE);
        assertThat(version.approvedBy()).isEqualTo("coord");
        var entry = f.audit.recent(1).get(0);
        assertThat(entry.action()).isEqualTo("RULE_ACTIVATED");
        assertThat(entry.actor()).isEqualTo("coord");
        assertThat(entry.entity()).isEqualTo("rule:R07@v1");
        assertThat(entry.before()).containsEntry("ruleSetVersion", 0L);
        assertThat(entry.after()).containsEntry("ruleSetVersion", 1L);
    }

    @Test
    @DisplayName("a segunda ativação gera a versão seguinte do conjunto, mantendo as demais regras")
    void versaoCrescente() {
        f.createAndActivate("R07", "a > 1");
        f.createRule.execute("analista", "R10", Fixture.content("b > 1"));

        var set = f.activateRule.execute("coord", "R10", 1);

        assertThat(set.version()).isEqualTo(2);
        assertThat(set.items()).extracting(RuleSetItem::ruleId).containsExactlyInAnyOrder("R07", "R10");
        assertThat(set.scoring()).isEqualTo(new Scoring(40, 70));
    }

    @Test
    @DisplayName("ativar a v2 substitui a v1 no conjunto e desliga a v1 (threshold mudou, lógica igual)")
    void novaVersaoSubstitui() {
        f.createAndActivate("R07", "a > params.maxCount");
        f.createRuleVersion.execute("analista", "R07", new RuleContent("a > params.maxCount", Map.of("maxCount", 3), Map.of(), 25, "m"));

        var set = f.activateRule.execute("coord", "R07", 2);

        assertThat(set.items()).containsExactly(new RuleSetItem("R07", 2, RuleStatus.ACTIVE));
        assertThat(f.rules.findVersion("R07", 1).orElseThrow().status()).isEqualTo(RuleStatus.DISABLED);
        assertThat(f.rules.findVersion("R07", 2).orElseThrow().status()).isEqualTo(RuleStatus.ACTIVE);
    }

    @Test
    @DisplayName("sombra: a regra entra no conjunto com status SHADOW")
    void sombra() {
        f.createRule.execute("analista", "R09", Fixture.content("a > 1"));

        var set = f.shadowRule.execute("analista", "R09", 1);

        assertThat(set.items()).containsExactly(new RuleSetItem("R09", 1, RuleStatus.SHADOW));
        assertThat(f.rules.findVersion("R09", 1).orElseThrow().status()).isEqualTo(RuleStatus.SHADOW);
        assertThat(f.audit.recent(1).get(0).action()).isEqualTo("RULE_SHADOWED");
    }

    @Test
    @DisplayName("promover da sombra para ativa troca o status no conjunto seguinte")
    void sombraParaAtiva() {
        f.createRule.execute("analista", "R09", Fixture.content("a > 1"));
        f.shadowRule.execute("analista", "R09", 1);

        var set = f.activateRule.execute("coord", "R09", 1);

        assertThat(set.items()).containsExactly(new RuleSetItem("R09", 1, RuleStatus.ACTIVE));
        assertThat(set.version()).isEqualTo(2);
    }

    @Test
    @DisplayName("desligar publica o conjunto sem a regra e marca a versão como DISABLED")
    void desligar() {
        f.createAndActivate("R07", "a > 1");

        var set = f.disableRule.execute("analista", "R07");

        assertThat(set.version()).isEqualTo(2);
        assertThat(set.items()).isEmpty();
        assertThat(f.rules.findVersion("R07", 1).orElseThrow().status()).isEqualTo(RuleStatus.DISABLED);
        assertThat(f.audit.recent(1).get(0).action()).isEqualTo("RULE_DISABLED");
    }

    @Test
    @DisplayName("desligar regra que não está no conjunto: lança e não publica")
    void desligarForaDoConjunto() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));

        assertThatThrownBy(() -> f.disableRule.execute("analista", "R07")).isInstanceOf(RuleNotInSetException.class);
        assertThatThrownBy(() -> f.disableRule.execute("analista", "NAO")).isInstanceOf(RuleNotFoundException.class);
        assertThat(f.ruleSets.all()).isEmpty();
    }

    @Test
    @DisplayName("rollback publica N+k com o mesmo conteúdo de N, sem apagar o histórico")
    void rollback() {
        f.createAndActivate("R07", "a > 1");
        f.createRule.execute("analista", "R10", Fixture.content("b > 1"));
        f.activateRule.execute("coord", "R10", 1);
        f.disableRule.execute("analista", "R07");

        var set = f.rollbackRuleSet.execute("coord", 1);

        assertThat(set.version()).isEqualTo(4);
        assertThat(set.items()).isEqualTo(itemsOf(1));
        assertThat(set.items()).containsExactly(new RuleSetItem("R07", 1, RuleStatus.ACTIVE));
        assertThat(set.scoring()).isEqualTo(f.ruleSets.findByVersion(1).orElseThrow().scoring());
        assertThat(f.ruleSets.all()).extracting("version").containsExactly(1L, 2L, 3L, 4L);
        assertThat(itemsOf(3)).extracting(RuleSetItem::ruleId).containsExactly("R10");
        assertThat(f.rules.findVersion("R07", 1).orElseThrow().status()).isEqualTo(RuleStatus.ACTIVE);
        assertThat(f.rules.findVersion("R10", 1).orElseThrow().status()).isEqualTo(RuleStatus.DISABLED);
        assertThat(f.audit.recent(1).get(0).action()).isEqualTo("RULESET_ROLLED_BACK");
    }

    @Test
    @DisplayName("rollback para versão inexistente: 404 e nada é publicado")
    void rollbackInexistente() {
        f.createAndActivate("R07", "a > 1");

        assertThatThrownBy(() -> f.rollbackRuleSet.execute("coord", 99)).isInstanceOf(RuleNotFoundException.class);
        assertThat(f.ruleSets.all()).hasSize(1);
    }

    @Test
    @DisplayName("scoring: publica conjunto novo com as faixas novas e as mesmas regras")
    void scoring() {
        f.createAndActivate("R07", "a > 1");

        var set = f.updateScoring.execute("coord", 30, 60);

        assertThat(set.version()).isEqualTo(2);
        assertThat(set.scoring()).isEqualTo(new Scoring(30, 60));
        assertThat(set.items()).isEqualTo(itemsOf(1));
        assertThat(f.audit.recent(1).get(0).action()).isEqualTo("SCORING_UPDATED");
    }

    @Test
    @DisplayName("scoring inválido (média acima da alta): lança e não publica")
    void scoringInvalido() {
        f.createAndActivate("R07", "a > 1");

        assertThatThrownBy(() -> f.updateScoring.execute("coord", 80, 70)).isInstanceOf(InvalidRuleDefinitionException.class);
        assertThat(f.ruleSets.all()).hasSize(1);
    }

    @Test
    @DisplayName("falha de compilação não publica nada: conjunto, status e auditoria ficam como estavam")
    void compilacaoFalhaNaoPublica() {
        f.createAndActivate("R07", "a > 1");
        f.rules.saveVersion(f.createRuleVersion.execute("analista", "R07", Fixture.content("a > 2")));
        f.rules.saveVersion(br.com.motorfraude.rules.domain.RuleVersion.draft("R07", 3, "INVALID", Map.of(), Map.of(), 10, "m", "analista", Fixture.NOW));
        int auditAntes = f.audit.all().size();

        assertThatThrownBy(() -> f.activateRule.execute("coord", "R07", 3))
                .isInstanceOfSatisfying(RuleSetInvalidException.class, e -> assertThat(e.issues()).extracting("ruleId").containsExactly("R07"));

        assertThat(f.ruleSets.all()).hasSize(1);
        assertThat(f.ruleSets.findActive().orElseThrow().items()).containsExactly(new RuleSetItem("R07", 1, RuleStatus.ACTIVE));
        assertThat(f.rules.findVersion("R07", 1).orElseThrow().status()).isEqualTo(RuleStatus.ACTIVE);
        assertThat(f.rules.findVersion("R07", 3).orElseThrow().status()).isEqualTo(RuleStatus.DRAFT);
        assertThat(f.audit.all()).hasSize(auditAntes);
    }

    @Test
    @DisplayName("ativar o que já está ativo, ou uma versão inexistente: lança e não publica")
    void ativarErros() {
        f.createAndActivate("R07", "a > 1");

        assertThatThrownBy(() -> f.activateRule.execute("coord", "R07", 1)).isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> f.activateRule.execute("coord", "R07", 9)).isInstanceOf(RuleNotFoundException.class);
        assertThatThrownBy(() -> f.activateRule.execute("coord", "NAO", 1)).isInstanceOf(RuleNotFoundException.class);
        assertThat(f.ruleSets.all()).hasSize(1);
    }

    @Test
    @DisplayName("dez ativações simultâneas geram as versões 1 a 10, sem buraco nem repetição")
    void concorrencia() throws Exception {
        for (int i = 1; i <= 10; i++) {
            f.createRule.execute("analista", "R" + String.format("%02d", i), Fixture.content("a > 1"));
        }
        try (var pool = Executors.newFixedThreadPool(10)) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 1; i <= 10; i++) {
                String id = "R" + String.format("%02d", i);
                futures.add(pool.submit(() -> f.activateRule.execute("coord", id, 1)));
            }
            for (var future : futures) {
                future.get();
            }
        }

        assertThat(f.ruleSets.all()).extracting("version").containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(f.ruleSets.findActive().orElseThrow().items()).hasSize(10);
    }

    @Test
    @DisplayName("conjunto ativo: devolve o conjunto com as versões completas das regras; vazio sem publicação")
    void conjuntoAtivo() {
        assertThat(f.getActiveRuleSet.execute()).isEmpty();
        f.createAndActivate("R07", "a > 1");

        var active = f.getActiveRuleSet.execute().orElseThrow();

        assertThat(active.ruleSet().version()).isEqualTo(1);
        assertThat(active.versions()).hasSize(1);
        assertThat(active.versions().get(0).expression()).isEqualTo("a > 1");
        assertThat(active.versions().get(0).status()).isEqualTo(RuleStatus.ACTIVE);
    }
}
