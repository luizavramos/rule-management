package br.com.motorfraude.rules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.rules.application.gateway.RuleValidator.TestCase;
import br.com.motorfraude.rules.application.usecase.RuleContent;
import br.com.motorfraude.rules.domain.InvalidRuleDefinitionException;
import br.com.motorfraude.rules.domain.RuleStatus;
import br.com.motorfraude.rules.fakes.Fixture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleAuthoringTest {

    private final Fixture f = new Fixture();

    @Test
    @DisplayName("criar uma regra válida cria a regra e a versão 1 em DRAFT, e registra a auditoria")
    void criaRegra() {
        var version = f.createRule.execute("analista", "R07", Fixture.content("features.count5m > params.maxCount"));

        assertThat(version.ruleId()).isEqualTo("R07");
        assertThat(version.version()).isEqualTo(1);
        assertThat(version.status()).isEqualTo(RuleStatus.DRAFT);
        assertThat(version.author()).isEqualTo("analista");
        assertThat(version.createdAt()).isEqualTo(Fixture.NOW);
        assertThat(f.rules.findRule("R07")).isPresent();
        assertThat(f.rules.findVersion("R07", 1)).contains(version);
        assertThat(f.audit.all()).hasSize(1);
        var entry = f.audit.all().get(0);
        assertThat(entry.actor()).isEqualTo("analista");
        assertThat(entry.action()).isEqualTo("RULE_CREATED");
        assertThat(entry.entity()).isEqualTo("rule:R07");
        assertThat(entry.before()).isNull();
        assertThat(entry.after()).containsEntry("version", 1).containsEntry("status", "DRAFT");
        assertThat(entry.at()).isEqualTo(Fixture.NOW);
    }

    @Test
    @DisplayName("expressão inválida: lança com os erros e não grava nada")
    void expressaoInvalida() {
        assertThatThrownBy(() -> f.createRule.execute("analista", "R07", Fixture.content("INVALID")))
                .isInstanceOfSatisfying(RuleInvalidException.class, e -> assertThat(e.errors()).isNotEmpty());

        assertThat(f.rules.findRule("R07")).isEmpty();
        assertThat(f.rules.listVersions("R07")).isEmpty();
        assertThat(f.audit.all()).isEmpty();
    }

    @Test
    @DisplayName("campo fora dos limites do contrato (peso 5000): lança com o erro do campo")
    void campoInvalido() {
        var content = new RuleContent("a > 1", Map.of(), Map.of(), 5000, "motivo");

        assertThatThrownBy(() -> f.createRule.execute("analista", "R07", content))
                .isInstanceOf(InvalidRuleDefinitionException.class)
                .hasMessageContaining("weight");
        assertThat(f.rules.findRule("R07")).isEmpty();
    }

    @Test
    @DisplayName("id repetido: lança e não mexe na regra existente")
    void idRepetido() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));

        assertThatThrownBy(() -> f.createRule.execute("outro", "R07", Fixture.content("b > 2")))
                .isInstanceOf(RuleAlreadyExistsException.class);

        assertThat(f.rules.listVersions("R07")).hasSize(1);
        assertThat(f.rules.findVersion("R07", 1).orElseThrow().expression()).isEqualTo("a > 1");
        assertThat(f.audit.all()).hasSize(1);
    }

    @Test
    @DisplayName("nova versão: cria a v2 em DRAFT e deixa a v1 intacta")
    void novaVersao() {
        f.createRule.execute("analista", "R07", Fixture.content("a > params.maxCount"));
        var content = new RuleContent("a > params.maxCount", Map.of("maxCount", 3), Map.of(), 25, "motivo");

        var v2 = f.createRuleVersion.execute("analista", "R07", content);

        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.status()).isEqualTo(RuleStatus.DRAFT);
        assertThat(v2.params()).containsEntry("maxCount", 3);
        assertThat(f.rules.findVersion("R07", 1).orElseThrow().params()).containsEntry("maxCount", 5);
        assertThat(f.audit.all()).extracting("action").containsExactly("RULE_CREATED", "RULE_VERSION_CREATED");
    }

    @Test
    @DisplayName("nova versão de regra inexistente: 404; expressão inválida não grava")
    void novaVersaoErros() {
        assertThatThrownBy(() -> f.createRuleVersion.execute("a", "NAO", Fixture.content("a > 1")))
                .isInstanceOf(RuleNotFoundException.class);

        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));
        assertThatThrownBy(() -> f.createRuleVersion.execute("a", "R07", Fixture.content("INVALID")))
                .isInstanceOf(RuleInvalidException.class);
        assertThat(f.rules.listVersions("R07")).hasSize(1);
    }

    @Test
    @DisplayName("validar: regra boa e casos de teste devolvem o resultado de cada caso")
    void validarComCasos() {
        var cases = List.of(new TestCase("dispara", Map.of(), true), new TestCase("não dispara", Map.of(), false));

        var report = f.validateRule.execute(Fixture.content("HIT"), cases);

        assertThat(report.valid()).isTrue();
        assertThat(report.errors()).isEmpty();
        assertThat(report.cases()).hasSize(2);
        assertThat(report.cases().get(0).passed()).isTrue();
        assertThat(report.cases().get(1).passed()).isFalse();
        assertThat(f.audit.all()).isEmpty();
        assertThat(f.rules.listRules()).isEmpty();
    }

    @Test
    @DisplayName("validar: expressão inválida e campos fora do contrato viram erros no relatório, sem lançar")
    void validarComErros() {
        var invalida = f.validateRule.execute(Fixture.content("INVALID"), List.of());
        var foraDoContrato = f.validateRule.execute(new RuleContent("a > 1", Map.of(), Map.of(), 5000, "m"), List.of());

        assertThat(invalida.valid()).isFalse();
        assertThat(invalida.errors()).isNotEmpty();
        assertThat(foraDoContrato.valid()).isFalse();
        assertThat(foraDoContrato.errors()).anyMatch(e -> e.contains("weight"));
    }
}
