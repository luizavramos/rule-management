package br.com.motorfraude.rules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.rules.domain.RuleSetItem;
import br.com.motorfraude.rules.domain.RuleStatus;
import br.com.motorfraude.rules.domain.RuleVersion;
import br.com.motorfraude.rules.domain.Scoring;
import br.com.motorfraude.rules.domain.SelfApprovalException;
import br.com.motorfraude.rules.fakes.Fixture;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleSetOutboxTest {

    private final Fixture f = new Fixture();

    @Test
    @DisplayName("H7.1: ativar grava um evento com o conjunto completo e a versão publicada")
    void ativarGravaUmEvento() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));

        var published = f.activateRule.execute("coordenador", "R07", 1);

        assertThat(f.outbox.events()).hasSize(1);
        var event = f.outbox.events().get(0);
        assertThat(event.ruleSet()).isEqualTo(published);
        assertThat(event.versions()).hasSize(1);
        assertThat(event.versions().get(0).expression()).isEqualTo("a > 1");
        assertThat(event.versions().get(0).approvedBy()).isEqualTo("coordenador");
    }

    @Test
    @DisplayName("cada publicação (ativar, sombra, desligar, rollback, scoring) gera exatamente um evento, na ordem das versões")
    void umEventoPorPublicacao() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));
        f.activateRule.execute("coordenador", "R07", 1);
        f.shadowRule.execute("analista", "R07", 1);
        f.disableRule.execute("analista", "R07");
        f.rollbackRuleSet.execute("coordenador", 1);
        f.updateScoring.execute("coordenador", 30, 60);

        assertThat(f.outbox.events()).extracting(e -> e.ruleSet().version()).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(f.outbox.events().get(1).ruleSet().items()).containsExactly(new RuleSetItem("R07", 1, RuleStatus.SHADOW));
        assertThat(f.outbox.events().get(2).ruleSet().items()).isEmpty();
        assertThat(f.outbox.events().get(4).ruleSet().scoring()).isEqualTo(new Scoring(30, 60));
    }

    @Test
    @DisplayName("o evento traz o conteúdo das versões com o status do item (SHADOW), igual ao conjunto servido ao motor")
    void igualAoConjuntoAtivo() {
        f.createRule.execute("analista", "R09", Fixture.content("a > 1"));
        f.shadowRule.execute("analista", "R09", 1);

        var event = f.outbox.events().get(0);
        var served = f.getActiveRuleSet.execute().orElseThrow();

        assertThat(event.ruleSet()).isEqualTo(served.ruleSet());
        assertThat(event.versions()).isEqualTo(served.versions());
        assertThat(event.ruleSet().items()).extracting(RuleSetItem::status).containsExactly(RuleStatus.SHADOW);
    }

    @Test
    @DisplayName("H7.2: publicação que não compila não grava evento")
    void naoCompilaNaoGravaEvento() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));
        f.rules.saveVersion(RuleVersion.draft("R07", 2, "INVALID", Map.of(), Map.of(), 10, "m", "analista", Fixture.NOW));

        assertThatThrownBy(() -> f.activateRule.execute("coordenador", "R07", 2)).isInstanceOf(RuleSetInvalidException.class);

        assertThat(f.outbox.events()).isEmpty();
    }

    @Test
    @DisplayName("recusas antes de publicar (aprovação, 404, transição inválida, scoring inválido) não gravam evento")
    void recusasNaoGravamEvento() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));

        assertThatThrownBy(() -> f.activateRule.execute("analista", "R07", 1)).isInstanceOf(SelfApprovalException.class);
        assertThatThrownBy(() -> f.activateRule.execute("coordenador", "R07", 9)).isInstanceOf(RuleNotFoundException.class);
        assertThatThrownBy(() -> f.disableRule.execute("analista", "R07")).isInstanceOf(RuleNotInSetException.class);
        assertThatThrownBy(() -> f.updateScoring.execute("coordenador", 80, 70)).isInstanceOf(RuntimeException.class);

        assertThat(f.outbox.events()).isEmpty();
    }

    @Test
    @DisplayName("criar regra e criar versão não publicam conjunto, então não geram evento")
    void criarNaoGeraEvento() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));
        f.createRuleVersion.execute("analista", "R07", Fixture.content("a > 2"));

        assertThat(f.outbox.events()).isEmpty();
    }
}
