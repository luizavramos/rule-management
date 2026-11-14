package br.com.motorfraude.rules.adapters.gateway.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.contracts.Schemas;
import br.com.motorfraude.rules.adapters.gateway.kernel.KernelRuleValidator;
import br.com.motorfraude.rules.adapters.presenter.ApiResponses.ActiveRuleSetResponse;
import br.com.motorfraude.rules.application.gateway.AuditLog;
import br.com.motorfraude.rules.application.usecase.ActivateRule;
import br.com.motorfraude.rules.application.usecase.CreateRule;
import br.com.motorfraude.rules.application.usecase.GetActiveRuleSet;
import br.com.motorfraude.rules.application.usecase.RuleContent;
import br.com.motorfraude.rules.application.usecase.RuleSetPublisher;
import br.com.motorfraude.rules.domain.AuditEntry;
import br.com.motorfraude.rules.fakes.RecordingRuleMetrics;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class OutboxIT extends PostgresSupport {

    private final Clock clock = Clock.systemUTC();
    private final PostgresRuleRepository rules = new PostgresRuleRepository(jdbc, json);
    private final PostgresRuleSetRepository ruleSets = new PostgresRuleSetRepository(jdbc);
    private final PostgresAuditLog audit = new PostgresAuditLog(jdbc, json);
    private final PostgresRuleSetOutbox outbox = new PostgresRuleSetOutbox(jdbc, json);
    private final KernelRuleValidator validator = new KernelRuleValidator();
    private final TransactionalUnitOfWork unitOfWork = new TransactionalUnitOfWork(transactionManager);

    private RuleSetPublisher publisher(AuditLog auditLog) {
        return new RuleSetPublisher(rules, ruleSets, auditLog, validator, unitOfWork, clock, new RecordingRuleMetrics(), outbox);
    }

    private static RuleContent content(String expression) {
        return new RuleContent(expression, Map.of("maxCount", 5), Map.of(), 25, "motivo");
    }

    private int outboxCount() {
        return jdbc.sql("select count(*) from outbox").query(Integer.class).single();
    }

    private JsonNode latestPayload() {
        return json.readTree(jdbc.sql("select payload::text from outbox order by created_at desc, id limit 1").query(String.class).single());
    }

    @Test
    @DisplayName("H7.4: depois da migração há um evento inicial com o conjunto 1 (as 8 regras), válido contra rule-set.v1")
    void eventoInicial() {
        assertThat(outboxCount()).isEqualTo(1);
        var row = jdbc.sql("select aggregatetype, aggregateid, type from outbox")
                .query((rs, n) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3))).single();
        assertThat(row).containsExactly("rule-sets.v1", "active", "RuleSetPublished");

        var payload = latestPayload();

        assertThat(payload.get("version").asLong()).isEqualTo(1);
        assertThat(payload.get("rules").size()).isEqualTo(8);
        assertThat(payload.get("scoring").get("highThreshold").asInt()).isEqualTo(70);
        assertThat(Schemas.validate("rule-set.v1", payload).errors()).isEmpty();
    }

    @Test
    @DisplayName("H7.4: a migração sobre um banco que já tem conjuntos publicados grava o evento do conjunto ATIVO do momento")
    void eventoInicialSobreBancoComHistorico() {
        var flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(false).target("4").load();
        flyway.clean();
        flyway.migrate();
        jdbc.sql("insert into rules values ('R20', 'analista', now())").update();
        jdbc.sql("insert into rule_versions values ('R20', 1, 'features.count1m > params.maxCount', '{\"maxCount\": 5}', '{}', 25, 'm', 'ACTIVE', 'analista', 'coordenador', now())").update();
        jdbc.sql("insert into rule_sets values (2, now(), 'coordenador', 30, 60)").update();
        jdbc.sql("insert into rule_set_items values (2, 'R20', 1, 'ACTIVE')").update();

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(outboxCount()).isEqualTo(1);
        var payload = latestPayload();
        assertThat(payload.get("version").asLong()).isEqualTo(2);
        assertThat(payload.get("scoring").get("mediumThreshold").asInt()).isEqualTo(30);
        assertThat(payload.get("rules").size()).isEqualTo(1);
        assertThat(payload.get("rules").get(0).get("ruleId").asString()).isEqualTo("R20");
        assertThat(Schemas.validate("rule-set.v1", payload).errors()).isEmpty();
    }

    @Test
    @DisplayName("H7.1: ativar grava uma linha na outbox na mesma transação, com o mesmo corpo do conjunto servido ao motor")
    void ativarGravaEvento() {
        var publisher = publisher(audit);
        new CreateRule(rules, audit, validator, unitOfWork, clock, new RecordingRuleMetrics()).execute("analista", "R20", content("features.count1m > params.maxCount"));

        var set = new ActivateRule(rules, publisher).execute("coordenador", "R20", 1);

        assertThat(outboxCount()).isEqualTo(2);
        var payload = latestPayload();
        assertThat(payload.get("version").asLong()).isEqualTo(set.version());
        assertThat(payload.get("rules").size()).isEqualTo(9);
        var served = new GetActiveRuleSet(ruleSets, rules).execute().orElseThrow();
        assertThat(payload).isEqualTo(json.readTree(json.writeValueAsString(ActiveRuleSetResponse.of(served))));
        assertThat(Schemas.validate("rule-set.v1", payload).errors()).isEmpty();
    }

    @Test
    @DisplayName("H7.2: se a publicação falha no meio (auditoria), a linha da outbox some junto com o conjunto")
    void atomicidade() {
        new CreateRule(rules, audit, validator, unitOfWork, clock, new RecordingRuleMetrics()).execute("analista", "R20", content("features.count1m > params.maxCount"));
        AuditLog quebrada = new AuditLog() {
            @Override
            public void append(AuditEntry entry) {
                throw new IllegalStateException("falha ao gravar a auditoria");
            }

            @Override
            public List<AuditEntry> recent(int limit) {
                return audit.recent(limit);
            }
        };
        int antes = outboxCount();

        assertThatThrownBy(() -> new ActivateRule(rules, publisher(quebrada)).execute("coordenador", "R20", 1)).isInstanceOf(IllegalStateException.class);

        assertThat(outboxCount()).isEqualTo(antes);
        assertThat(ruleSets.activeVersion()).hasValue(1);
    }

    @Test
    @DisplayName("os eventos saem em ordem de versão, um por publicação")
    void ordem() {
        var publisher = publisher(audit);
        var create = new CreateRule(rules, audit, validator, unitOfWork, clock, new RecordingRuleMetrics());
        var activate = new ActivateRule(rules, publisher);
        create.execute("analista", "R20", content("features.count1m > params.maxCount"));
        create.execute("analista", "R21", content("features.count5m > params.maxCount"));

        activate.execute("coordenador", "R20", 1);
        activate.execute("coordenador", "R21", 1);

        var versions = jdbc.sql("select (payload->>'version')::bigint from outbox order by created_at, id").query(Long.class).list();
        assertThat(versions).containsExactly(1L, 2L, 3L);
    }
}
