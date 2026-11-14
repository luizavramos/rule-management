package br.com.motorfraude.rules.adapters.gateway.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.rules.application.RuleInvalidException;
import br.com.motorfraude.rules.application.gateway.RuleValidator.TestCase;
import br.com.motorfraude.rules.application.usecase.RuleContent;
import br.com.motorfraude.rules.domain.RuleSetItem;
import br.com.motorfraude.rules.domain.RuleStatus;
import br.com.motorfraude.rules.fakes.Fixture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Os casos de uso com o validador real (CEL): o roteiro da demo (SC-001) sem banco e sem HTTP. */
class RuleLifecycleWithKernelTest {

    private final Fixture f = new Fixture(new KernelRuleValidator());

    @Test
    @DisplayName("criar, validar, ativar em sombra, ativar, mudar o threshold, desligar e reverter")
    void roteiroDaDemo() {
        var content = new RuleContent("features.count5m > params.maxCount", Map.of("maxCount", 5), Map.of("PJ", Map.of("maxCount", 30)), 25,
                "{features.count5m} transações em 5 min (limite {params.maxCount})");

        var report = f.validateRule.execute(content, List.of(
                new TestCase("6 transações dispara", Map.of("features.count5m", 6), true),
                new TestCase("5 não dispara", Map.of("features.count5m", 5), false),
                new TestCase("PJ com 6 não dispara", Map.of("features.count5m", 6, "profile.segment", "PJ"), false)));
        assertThat(report.valid()).isTrue();
        assertThat(report.cases()).extracting("passed").containsExactly(true, true, true);

        f.createRule.execute("analista", "R07", content);
        var sombra = f.shadowRule.execute("analista", "R07", 1);
        assertThat(sombra.items()).containsExactly(new RuleSetItem("R07", 1, RuleStatus.SHADOW));

        var ativa = f.activateRule.execute("coord", "R07", 1);
        assertThat(ativa.version()).isEqualTo(2);
        assertThat(ativa.items()).containsExactly(new RuleSetItem("R07", 1, RuleStatus.ACTIVE));

        f.createRuleVersion.execute("analista", "R07", new RuleContent(content.expression(), Map.of("maxCount", 3), content.paramOverrides(), 25, content.reasonTemplate()));
        var v2 = f.activateRule.execute("coord", "R07", 2);
        assertThat(v2.items()).containsExactly(new RuleSetItem("R07", 2, RuleStatus.ACTIVE));
        assertThat(f.getActiveRuleSet.execute().orElseThrow().versions().get(0).params()).containsEntry("maxCount", 3);

        var desligada = f.disableRule.execute("analista", "R07");
        assertThat(desligada.items()).isEmpty();

        var revertida = f.rollbackRuleSet.execute("coord", v2.version());
        assertThat(revertida.items()).isEqualTo(v2.items());
        assertThat(revertida.version()).isEqualTo(desligada.version() + 1);
        assertThat(f.audit.all()).extracting("action").contains("RULE_CREATED", "RULE_SHADOWED", "RULE_ACTIVATED", "RULE_DISABLED", "RULESET_ROLLED_BACK");
    }

    @Test
    @DisplayName("expressão com feature inexistente é recusada na criação, com a mensagem do kernel (H1, cenário 2)")
    void featureInexistente() {
        assertThatThrownBy(() -> f.createRule.execute("analista", "R99", Fixture.content("features.contagem5m > 3")))
                .isInstanceOfSatisfying(RuleInvalidException.class,
                        e -> assertThat(e.errors()).contains("feature desconhecida: contagem5m"));
        assertThat(f.rules.findRule("R99")).isEmpty();
    }
}
