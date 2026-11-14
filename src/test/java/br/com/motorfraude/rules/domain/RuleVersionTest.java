package br.com.motorfraude.rules.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** T209 — transições de status e invariantes de uma versão de regra (imutável). */
class RuleVersionTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    static RuleVersion draft() {
        return RuleVersion.draft("R07", 1, "features.count5m > params.maxCount", Map.of("maxCount", 5), Map.of(), 25,
                "Mais de {params.maxCount} transações em 5 min", "analista", NOW);
    }

    private static RuleVersion in(RuleStatus status) {
        return switch (status) {
            case DRAFT -> draft();
            case ACTIVE -> draft().activate("coord");
            case SHADOW -> draft().shadow();
            case DISABLED -> draft().disable();
        };
    }

    @ParameterizedTest(name = "{0} -> {1} é permitida")
    @DisplayName("transições permitidas")
    @CsvSource({"DRAFT,ACTIVE", "DRAFT,SHADOW", "DRAFT,DISABLED", "SHADOW,ACTIVE", "SHADOW,DISABLED",
            "ACTIVE,SHADOW", "ACTIVE,DISABLED", "DISABLED,ACTIVE", "DISABLED,SHADOW"})
    void permitidas(RuleStatus from, RuleStatus to) {
        var result = in(from).transitionTo(to, "coord");

        assertThat(result.status()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1} é proibida")
    @DisplayName("transições proibidas: voltar a DRAFT e repetir o mesmo status")
    @CsvSource({"ACTIVE,DRAFT", "SHADOW,DRAFT", "DISABLED,DRAFT", "DRAFT,DRAFT",
            "ACTIVE,ACTIVE", "SHADOW,SHADOW", "DISABLED,DISABLED"})
    void proibidas(RuleStatus from, RuleStatus to) {
        assertThatThrownBy(() -> in(from).transitionTo(to, "coord"))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @Test
    @DisplayName("a transição devolve outra versão; a original não muda")
    void imutavel() {
        var draft = draft();

        var active = draft.activate("coord");

        assertThat(draft.status()).isEqualTo(RuleStatus.DRAFT);
        assertThat(draft.approvedBy()).isNull();
        assertThat(active.status()).isEqualTo(RuleStatus.ACTIVE);
        assertThat(active.expression()).isEqualTo(draft.expression());
        assertThat(active.version()).isEqualTo(draft.version());
        assertThat(active.author()).isEqualTo("analista");
    }

    @Test
    @DisplayName("ativar registra quem aprovou; sombra e desligar não")
    void aprovador() {
        assertThat(draft().activate("coord").approvedBy()).isEqualTo("coord");
        assertThat(draft().shadow().approvedBy()).isNull();
        assertThat(draft().activate("coord").disable().approvedBy()).isEqualTo("coord");
    }

    @Test
    @DisplayName("parâmetros são copiados e ficam imutáveis")
    void parametrosImutaveis() {
        var params = new HashMap<String, Object>(Map.of("maxCount", 5));
        var version = RuleVersion.draft("R07", 1, "features.count5m > params.maxCount", params, Map.of(), 25, "motivo", "a", NOW);

        params.put("maxCount", 99);

        assertThat(version.params()).containsEntry("maxCount", 5);
        assertThatThrownBy(() -> version.params().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("versão válida: campos limites aceitos")
    void limitesAceitos() {
        var version = RuleVersion.draft("Ab", 1, "x".repeat(2000), Map.of(), Map.of("PF", Map.of("a", 1), "PJ", Map.of("a", 2)), 1000,
                "r".repeat(200), "a", NOW);

        assertThat(version.weight()).isEqualTo(1000);
        assertThat(RuleVersion.draft("R_01-x", 1, "a", Map.of(), Map.of(), 0, "r", "a", NOW).weight()).isZero();
    }

    @Test
    @DisplayName("campos inválidos: todos os erros vêm juntos")
    void invalidosJuntos() {
        assertThatThrownBy(() -> RuleVersion.draft("1ruim", 0, " ", Map.of(), Map.of("XX", Map.of()), 1001, "", " ", NOW))
                .isInstanceOfSatisfying(InvalidRuleDefinitionException.class, e -> assertThat(e.errors()).contains(
                        "ruleId inválido: deve começar com letra e ter 2 a 32 caracteres (letras, números, _ ou -)",
                        "version deve ser 1 ou mais",
                        "a expressão não pode ser vazia",
                        "weight deve estar entre 0 e 1000",
                        "reasonTemplate deve ter de 1 a 200 caracteres",
                        "author é obrigatório",
                        "segmento de override inválido: XX (use PF ou PJ)"));
    }

    @Test
    @DisplayName("expressão e motivo longos demais, e nome de parâmetro inválido")
    void outrosLimites() {
        assertThatThrownBy(() -> RuleVersion.draft("R01", 1, "x".repeat(2001), Map.of("Ruim", 1), Map.of(), 1, "r".repeat(201), "a", NOW))
                .isInstanceOfSatisfying(InvalidRuleDefinitionException.class, e -> assertThat(e.errors()).contains(
                        "a expressão deve ter no máximo 2000 caracteres",
                        "reasonTemplate deve ter de 1 a 200 caracteres",
                        "nome de parâmetro inválido: Ruim"));
    }
}
