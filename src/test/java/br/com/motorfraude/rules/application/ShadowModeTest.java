package br.com.motorfraude.rules.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.motorfraude.rules.domain.RuleSetItem;
import br.com.motorfraude.rules.domain.RuleStatus;
import br.com.motorfraude.rules.fakes.Fixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShadowModeTest {

    private final Fixture f = new Fixture();

    @Test
    @DisplayName("uma regra ativa volta para a sombra: o conjunto seguinte a traz como SHADOW")
    void ativaVoltaParaSombra() {
        f.createAndActivate("R07", "a > 1");

        var set = f.shadowRule.execute("analista", "R07", 1);

        assertThat(set.version()).isEqualTo(2);
        assertThat(set.items()).containsExactly(new RuleSetItem("R07", 1, RuleStatus.SHADOW));
        assertThat(f.rules.findVersion("R07", 1).orElseThrow().status()).isEqualTo(RuleStatus.SHADOW);
        assertThat(f.audit.recent(1).get(0).action()).isEqualTo("RULE_SHADOWED");
    }

    @Test
    @DisplayName("a sombra de uma nova versão desliga a anterior e convive com as outras regras ativas")
    void sombraDeNovaVersao() {
        f.createAndActivate("R07", "a > 1");
        f.createAndActivate("R10", "b > 1");
        f.createRuleVersion.execute("analista", "R07", Fixture.content("a > 2"));

        var set = f.shadowRule.execute("analista", "R07", 2);

        assertThat(set.items()).containsExactlyInAnyOrder(new RuleSetItem("R07", 2, RuleStatus.SHADOW), new RuleSetItem("R10", 1, RuleStatus.ACTIVE));
        assertThat(f.rules.findVersion("R07", 1).orElseThrow().status()).isEqualTo(RuleStatus.DISABLED);
    }

    @Test
    @DisplayName("o conjunto servido ao motor mantém a regra em sombra com o status do item")
    void servidaComoSombra() {
        f.createRule.execute("analista", "R09", Fixture.content("a > 1"));
        f.shadowRule.execute("analista", "R09", 1);

        var active = f.getActiveRuleSet.execute().orElseThrow();

        assertThat(active.ruleSet().items()).extracting(RuleSetItem::status).containsExactly(RuleStatus.SHADOW);
    }
}
