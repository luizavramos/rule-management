package br.com.motorfraude.rules.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScoringTest {

    @Test
    @DisplayName("40 e 70 são válidas; média igual à alta também")
    void validas() {
        assertThat(new Scoring(40, 70)).isEqualTo(new Scoring(40, 70));
        assertThat(new Scoring(50, 50).highThreshold()).isEqualTo(50);
    }

    @Test
    @DisplayName("zero, negativa ou média acima da alta são rejeitadas")
    void invalidas() {
        assertThatThrownBy(() -> new Scoring(0, 70)).isInstanceOf(InvalidRuleDefinitionException.class);
        assertThatThrownBy(() -> new Scoring(-1, 70)).isInstanceOf(InvalidRuleDefinitionException.class);
        assertThatThrownBy(() -> new Scoring(80, 70)).isInstanceOf(InvalidRuleDefinitionException.class)
                .hasMessageContaining("mediumThreshold");
    }
}
