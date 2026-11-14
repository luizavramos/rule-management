package br.com.motorfraude.rules.adapters.gateway.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.motorfraude.rules.application.gateway.RuleValidator.TestCase;
import br.com.motorfraude.rules.domain.RuleVersion;
import br.com.motorfraude.rules.domain.Scoring;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KernelRuleValidatorTest {

    private final KernelRuleValidator validator = new KernelRuleValidator();

    private static RuleVersion rule(String id, String expression, Map<String, Object> params) {
        return RuleVersion.draft(id, 1, expression, params, Map.of(), 25, "motivo", "analista", Instant.EPOCH);
    }

    @Test
    @DisplayName("regra válida: sem erros")
    void valida() {
        assertThat(validator.validate(rule("R07", "features.count5m > params.maxCount", Map.of("maxCount", 5)))).isEmpty();
    }

    @Test
    @DisplayName("feature inexistente: a mensagem do kernel chega inteira (H1, cenário 2)")
    void featureDesconhecida() {
        assertThat(validator.validate(rule("R07", "features.contagem5m > 3", Map.of()))).contains("feature desconhecida: contagem5m");
    }

    @Test
    @DisplayName("tipo errado: erro de tipo (H1, cenário 3)")
    void tipoErrado() {
        assertThat(validator.validate(rule("R07", "tx.amount > \"mil\"", Map.of()))).anyMatch(e -> e.startsWith("erro de tipo:"));
    }

    @Test
    @DisplayName("conjunto válido compila; conjunto com regra ruim devolve o problema por regra")
    void conjunto() {
        var boa = rule("R01", "tx.amount > 0", Map.of()).activate("coord");
        var ruim = rule("R02", "features.nada > 1", Map.of()).activate("coord");
        var sombra = rule("R03", "tx.amount > 0", Map.of()).shadow();

        assertThat(validator.validateSet(new Scoring(40, 70), List.of(boa, sombra))).isEmpty();
        var issues = validator.validateSet(new Scoring(40, 70), List.of(boa, ruim));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).ruleId()).isEqualTo("R02");
        assertThat(issues.get(0).errors()).contains("feature desconhecida: nada");
    }

    @Test
    @DisplayName("casos de teste: dispara, não dispara, e o que não foi informado assume um valor neutro")
    void casos() {
        var version = rule("R07", "features.count5m > params.maxCount", Map.of("maxCount", 5));
        var cases = List.of(
                new TestCase("6 transações dispara", Map.of("features.count5m", 6), true),
                new TestCase("5 transações não dispara", Map.of("features.count5m", 5), false),
                new TestCase("esperava disparar e não disparou", Map.of("features.count5m", 1), true));

        var results = validator.runCases(version, cases);

        assertThat(results).extracting("passed").containsExactly(true, true, false);
        assertThat(results.get(0).actualHit()).isTrue();
        assertThat(results.get(1).actualHit()).isFalse();
        assertThat(results.get(2).actualHit()).isFalse();
        assertThat(results).extracting("error").containsOnlyNulls();
    }

    @Test
    @DisplayName("caso com feature desconhecida ou tipo errado vira erro daquele caso, sem derrubar os outros")
    void casoComContextoInvalido() {
        var version = rule("R10", "lists.counterpartyFlagged", Map.of());
        var cases = List.of(
                new TestCase("ok", Map.of("lists.counterpartyFlagged", true), true),
                new TestCase("feature inexistente", Map.of("lists.nada", true), false),
                new TestCase("tipo errado", Map.of("lists.counterpartyFlagged", "sim"), false));

        var results = validator.runCases(version, cases);

        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(1).passed()).isFalse();
        assertThat(results.get(1).error()).contains("lists.nada");
        assertThat(results.get(2).passed()).isFalse();
        assertThat(results.get(2).error()).contains("lists.counterpartyFlagged");
    }

    @Test
    @DisplayName("um caso sem nenhuma feature parte de valores neutros e não dispara regras de lista nem de dispositivo novo")
    void padraoNeutro() {
        var version = rule("R10", "lists.counterpartyFlagged || features.isNewDevice", Map.of());

        var results = validator.runCases(version, List.of(new TestCase("vazio", Map.of(), false)));

        assertThat(results.get(0).passed()).isTrue();
        assertThat(results.get(0).error()).isNull();
    }
}
