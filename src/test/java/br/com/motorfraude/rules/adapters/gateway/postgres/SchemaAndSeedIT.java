package br.com.motorfraude.rules.adapters.gateway.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.rules.adapters.gateway.kernel.KernelRuleValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T215 — migrações (V1, V2), restrições do banco e as 8 regras iniciais (SC-003 da spec 002). */
class SchemaAndSeedIT extends PostgresSupport {

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }

    @Test
    @DisplayName("o seed publica o conjunto 1 com as 8 regras ACTIVE e o scoring 40/70")
    void seed() {
        assertThat(count("select count(*) from rule_sets")).isEqualTo(1);
        assertThat(jdbc.sql("select medium_threshold || '/' || high_threshold from rule_sets where version = 1").query(String.class).single()).isEqualTo("40/70");
        assertThat(jdbc.sql("select rule_id from rule_set_items where rule_set_version = 1 order by rule_id").query(String.class).list())
                .containsExactly("R01", "R03", "R04", "R05", "R07", "R09", "R10", "R12");
        assertThat(count("select count(*) from rule_set_items where status <> 'ACTIVE'")).isZero();
        assertThat(count("select count(*) from rule_versions where status = 'ACTIVE' and approved_by is not null")).isEqualTo(8);
        assertThat(jdbc.sql("select action from audit_log").query(String.class).list()).containsExactly("RULESET_SEEDED");
    }

    @Test
    @DisplayName("as 8 regras do seed compilam juntas com o kernel (SC-003)")
    void seedCompila() {
        var repository = new PostgresRuleSetRepository(jdbc);
        var rules = new PostgresRuleRepository(jdbc, json);
        var active = repository.findActive().orElseThrow();
        var versions = active.items().stream().map(i -> rules.findVersion(i.ruleId(), i.ruleVersion()).orElseThrow()).toList();

        var issues = new KernelRuleValidator().validateSet(active.scoring(), versions);

        assertThat(issues).isEmpty();
        assertThat(versions).hasSize(8);
    }

    @Test
    @DisplayName("o conteúdo de uma versão é imutável no banco; só status e aprovador mudam")
    void versaoImutavel() {
        assertThatThrownBy(() -> jdbc.sql("update rule_versions set expression = 'true' where rule_id = 'R10'").update())
                .hasMessageContaining("imutavel");
        assertThatThrownBy(() -> jdbc.sql("update rule_versions set weight = 1 where rule_id = 'R10'").update())
                .hasMessageContaining("imutavel");

        int linhas = jdbc.sql("update rule_versions set status = 'DISABLED', approved_by = 'outro' where rule_id = 'R10'").update();

        assertThat(linhas).isEqualTo(1);
    }

    @Test
    @DisplayName("o audit_log é append-only: UPDATE e DELETE são recusados")
    void auditAppendOnly() {
        assertThatThrownBy(() -> jdbc.sql("update audit_log set actor = 'x'").update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("delete from audit_log").update()).hasMessageContaining("append-only");
        assertThat(count("select count(*) from audit_log")).isEqualTo(1);
    }

    @Test
    @DisplayName("restrições: item de conjunto precisa apontar para uma versão existente; peso e scoring têm limites")
    void restricoes() {
        assertThat(jdbc.sql("insert into rule_sets values (2, now(), 'x', 40, 70)").update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("insert into rule_set_items values (2, 'R10', 9, 'ACTIVE')").update())
                .hasMessageContaining("foreign key");
        assertThatThrownBy(() -> jdbc.sql("insert into rule_set_items values (2, 'R10', 1, 'DRAFT')").update())
                .hasMessageContaining("check");
        assertThatThrownBy(() -> jdbc.sql("insert into rule_sets values (3, now(), 'x', 80, 70)").update()).hasMessageContaining("check");
        assertThatThrownBy(() -> jdbc.sql("insert into rule_sets values (1, now(), 'x', 40, 70)").update()).hasMessageContaining("duplicate");
        assertThatThrownBy(() -> jdbc.sql("insert into rule_versions values ('R10', 2, 'x', '{}', '{}', 2000, 'm', 'DRAFT', 'a', null, now())").update())
                .hasMessageContaining("check");
    }
}
