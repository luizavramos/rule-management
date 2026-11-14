package br.com.motorfraude.rules.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MakerCheckerTest {

    private static RuleVersion draftBy(String author) {
        return RuleVersion.draft("R07", 1, "features.count5m > params.maxCount", Map.of("maxCount", 5), Map.of(), 25, "motivo", author,
                Instant.parse("2026-11-14T12:00:00Z"));
    }

    @Test
    @DisplayName("quem criou a versão não pode ativá-la")
    void autorNaoAtiva() {
        assertThatThrownBy(() -> draftBy("analista").activate("analista"))
                .isInstanceOf(SelfApprovalException.class)
                .hasMessageContaining("analista");
    }

    @Test
    @DisplayName("outra pessoa ativa, e fica registrada como aprovadora")
    void outraPessoaAtiva() {
        var active = draftBy("analista").activate("coordenador");

        assertThat(active.status()).isEqualTo(RuleStatus.ACTIVE);
        assertThat(active.approvedBy()).isEqualTo("coordenador");
        assertThat(active.author()).isEqualTo("analista");
    }

    @Test
    @DisplayName("a regra vale também saindo de sombra e de desligada")
    void deSombraEDeDesligada() {
        assertThatThrownBy(() -> draftBy("analista").shadow().activate("analista")).isInstanceOf(SelfApprovalException.class);
        assertThatThrownBy(() -> draftBy("analista").activate("coordenador").disable().activate("analista"))
                .isInstanceOf(SelfApprovalException.class);
        assertThat(draftBy("analista").shadow().activate("coordenador").status()).isEqualTo(RuleStatus.ACTIVE);
    }

    @Test
    @DisplayName("o autor pode pôr em sombra, desligar e rascunhar: só a ativação exige outra pessoa")
    void autorFazOResto() {
        assertThat(draftBy("analista").shadow().status()).isEqualTo(RuleStatus.SHADOW);
        assertThat(draftBy("analista").disable().status()).isEqualTo(RuleStatus.DISABLED);
        assertThat(draftBy("analista").activate("coordenador").shadow().status()).isEqualTo(RuleStatus.SHADOW);
        assertThat(draftBy("analista").activate("coordenador").disable().status()).isEqualTo(RuleStatus.DISABLED);
    }

    @Test
    @DisplayName("restaurar uma versão já aprovada (rollback) mantém o aprovador original")
    void restaurarMantemAprovador() {
        var disabled = draftBy("analista").activate("coordenador").disable();

        var restored = disabled.transitionTo(RuleStatus.ACTIVE, disabled.approvedBy());

        assertThat(restored.approvedBy()).isEqualTo("coordenador");
    }
}
