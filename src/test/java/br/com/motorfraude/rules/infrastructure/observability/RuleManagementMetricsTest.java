package br.com.motorfraude.rules.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleManagementMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RuleManagementMetrics metrics = new RuleManagementMetrics(registry);

    private double published(String action) {
        return registry.get("rules.published").tag("action", action).counter().count();
    }

    private double failures(String kind) {
        return registry.get("rules.compilation.failures").tag("kind", kind).counter().count();
    }

    @Test
    @DisplayName("publicações são contadas por ação")
    void publicacoes() {
        metrics.published("RULE_ACTIVATED");
        metrics.published("RULE_ACTIVATED");
        metrics.published("RULE_DISABLED");

        assertThat(published("RULE_ACTIVATED")).isEqualTo(2);
        assertThat(published("RULE_DISABLED")).isEqualTo(1);
    }

    @Test
    @DisplayName("falhas de compilação são contadas por tipo")
    void falhas() {
        metrics.compilationFailed("rule");
        metrics.compilationFailed("rule-set");
        metrics.compilationFailed("rule-set");

        assertThat(failures("rule")).isEqualTo(1);
        assertThat(failures("rule-set")).isEqualTo(2);
    }

    @Test
    @DisplayName("os contadores conhecidos existem em zero desde a partida, para o painel não ficar vazio")
    void existemEmZero() {
        for (var action : new String[] {"RULE_ACTIVATED", "RULE_SHADOWED", "RULE_DISABLED", "RULESET_ROLLED_BACK", "SCORING_UPDATED"}) {
            assertThat(published(action)).isZero();
        }
        assertThat(failures("rule")).isZero();
        assertThat(failures("rule-set")).isZero();
    }

    @Test
    @DisplayName("uma ação desconhecida também é contada, sem quebrar")
    void acaoDesconhecida() {
        metrics.published("OUTRA_ACAO");

        assertThat(published("OUTRA_ACAO")).isEqualTo(1);
    }
}
