package br.com.motorfraude.rules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.rules.domain.RuleSetItem;
import br.com.motorfraude.rules.domain.RuleStatus;
import br.com.motorfraude.rules.domain.SelfApprovalException;
import br.com.motorfraude.rules.fakes.Fixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MakerCheckerUseCaseTest {

    private final Fixture f = new Fixture();

    @Test
    @DisplayName("H6.1: o autor tenta ativar a própria regra: recusado, e nada é publicado")
    void autorNaoAtiva() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));

        assertThatThrownBy(() -> f.activateRule.execute("analista", "R07", 1)).isInstanceOf(SelfApprovalException.class);

        assertThat(f.ruleSets.all()).isEmpty();
        assertThat(f.rules.findVersion("R07", 1).orElseThrow().status()).isEqualTo(RuleStatus.DRAFT);
        assertThat(f.rules.findVersion("R07", 1).orElseThrow().approvedBy()).isNull();
        assertThat(f.audit.all()).extracting("action").containsExactly("RULE_CREATED");
    }

    @Test
    @DisplayName("H6.2: outra pessoa ativa, e approved_by fica com quem ativou")
    void outraPessoaAtiva() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));

        var set = f.activateRule.execute("coordenador", "R07", 1);

        assertThat(set.items()).containsExactly(new RuleSetItem("R07", 1, RuleStatus.ACTIVE));
        assertThat(f.rules.findVersion("R07", 1).orElseThrow().approvedBy()).isEqualTo("coordenador");
    }

    @Test
    @DisplayName("promover da sombra para ativa também exige outra pessoa")
    void sombraParaAtiva() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));
        f.shadowRule.execute("analista", "R07", 1);

        assertThatThrownBy(() -> f.activateRule.execute("analista", "R07", 1)).isInstanceOf(SelfApprovalException.class);

        assertThat(f.ruleSets.all()).hasSize(1);
        assertThat(f.ruleSets.findActive().orElseThrow().items()).containsExactly(new RuleSetItem("R07", 1, RuleStatus.SHADOW));
    }

    @Test
    @DisplayName("o rollback restaura versões aprovadas sem pedir aprovação de novo")
    void rollbackNaoReaprova() {
        f.createRule.execute("analista", "R07", Fixture.content("a > 1"));
        f.activateRule.execute("coordenador", "R07", 1);
        f.disableRule.execute("analista", "R07");

        var restored = f.rollbackRuleSet.execute("coordenador", 1);

        assertThat(restored.items()).containsExactly(new RuleSetItem("R07", 1, RuleStatus.ACTIVE));
        assertThat(f.rules.findVersion("R07", 1).orElseThrow().approvedBy()).isEqualTo("coordenador");
    }
}
