package br.com.motorfraude.rules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.rules.domain.SelfApprovalException;
import br.com.motorfraude.rules.fakes.Fixture;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleMetricsTest {

    private final Fixture f = new Fixture();

    @Test
    @DisplayName("cada publicação conta uma vez, com a ação que a originou")
    void publicacoes() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));
        f.activateRule.execute("coordenador", "R07", 1);
        f.updateScoring.execute("coordenador", 30, 60);
        f.shadowRule.execute("analista", "R07", 1);
        f.disableRule.execute("analista", "R07");
        f.rollbackRuleSet.execute("coordenador", 1);

        assertThat(f.metrics.published()).containsExactly("RULE_ACTIVATED", "SCORING_UPDATED", "RULE_SHADOWED", "RULE_DISABLED", "RULESET_ROLLED_BACK");
        assertThat(f.metrics.failures()).isEmpty();
    }

    @Test
    @DisplayName("criar regra ou versão com expressão inválida conta uma falha de compilação do tipo 'rule'")
    void falhaAoCriar() {
        assertThatThrownBy(() -> f.createRule.execute("analista", "R07", Fixture.content("INVALID"))).isInstanceOf(RuleInvalidException.class);
        f.createRule.execute("analista", "R08", Fixture.content("a > 1"));
        assertThatThrownBy(() -> f.createRuleVersion.execute("analista", "R08", Fixture.content("INVALID"))).isInstanceOf(RuleInvalidException.class);

        assertThat(f.metrics.failures()).containsExactly("rule", "rule");
        assertThat(f.metrics.published()).isEmpty();
    }

    @Test
    @DisplayName("conjunto que não compila conta uma falha do tipo 'rule-set' e não conta publicação")
    void falhaNoConjunto() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));
        f.rules.saveVersion(br.com.motorfraude.rules.domain.RuleVersion.draft("R07", 2, "INVALID", Map.of(), Map.of(), 10, "m", "analista", Fixture.NOW));

        assertThatThrownBy(() -> f.activateRule.execute("coordenador", "R07", 2)).isInstanceOf(RuleSetInvalidException.class);

        assertThat(f.metrics.failures()).containsExactly("rule-set");
        assertThat(f.metrics.published()).isEmpty();
    }

    @Test
    @DisplayName("recusas que não são falha de compilação (aprovação, conflito, 404) não contam nada")
    void outrasRecusasNaoContam() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));

        assertThatThrownBy(() -> f.activateRule.execute("analista", "R07", 1)).isInstanceOf(SelfApprovalException.class);
        assertThatThrownBy(() -> f.activateRule.execute("coordenador", "R07", 9)).isInstanceOf(RuleNotFoundException.class);

        assertThat(f.metrics.published()).isEmpty();
        assertThat(f.metrics.failures()).isEmpty();
    }
}
