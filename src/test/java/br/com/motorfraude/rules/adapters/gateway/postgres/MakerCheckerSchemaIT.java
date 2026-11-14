package br.com.motorfraude.rules.adapters.gateway.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MakerCheckerSchemaIT extends PostgresSupport {

    @Test
    @DisplayName("o banco recusa uma versão aprovada por quem a criou")
    void bancoRecusaAutoAprovacao() {
        assertThatThrownBy(() -> jdbc.sql("update rule_versions set approved_by = author where rule_id = 'R10'").update())
                .hasMessageContaining("check");
        jdbc.sql("insert into rules values ('X1', 'a', now())").update();
        assertThatThrownBy(() -> jdbc.sql("insert into rule_versions values ('X1', 1, 'a > 1', '{}', '{}', 10, 'm', 'ACTIVE', 'analista', 'analista', now())").update())
                .hasMessageContaining("check");
    }

    @Test
    @DisplayName("aprovador diferente do autor é aceito, e sem aprovador também (rascunho)")
    void aceitaAprovadorDiferente() {
        jdbc.sql("insert into rules values ('X1', 'a', now())").update();

        int comAprovador = jdbc.sql("insert into rule_versions values ('X1', 1, 'a > 1', '{}', '{}', 10, 'm', 'ACTIVE', 'analista', 'coordenador', now())").update();
        int rascunho = jdbc.sql("insert into rule_versions values ('X1', 2, 'a > 1', '{}', '{}', 10, 'm', 'DRAFT', 'analista', null, now())").update();

        assertThat(comAprovador).isEqualTo(1);
        assertThat(rascunho).isEqualTo(1);
    }

    @Test
    @DisplayName("as 8 regras do seed já têm aprovador diferente do autor")
    void seedComAprovadorDiferente() {
        assertThat(jdbc.sql("select count(*) from rule_versions where approved_by = author").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from rule_versions where approved_by is not null and status = 'ACTIVE'").query(Integer.class).single()).isEqualTo(8);
    }
}
