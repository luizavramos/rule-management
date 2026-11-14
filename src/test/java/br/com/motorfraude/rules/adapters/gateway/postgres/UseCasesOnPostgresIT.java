package br.com.motorfraude.rules.adapters.gateway.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.motorfraude.rules.adapters.gateway.kernel.KernelRuleValidator;
import br.com.motorfraude.rules.application.RuleSetInvalidException;
import br.com.motorfraude.rules.application.gateway.AuditLog;
import br.com.motorfraude.rules.application.usecase.ActivateRule;
import br.com.motorfraude.rules.application.usecase.CreateRule;
import br.com.motorfraude.rules.application.usecase.DisableRule;
import br.com.motorfraude.rules.application.usecase.RollbackRuleSet;
import br.com.motorfraude.rules.application.usecase.RuleContent;
import br.com.motorfraude.rules.application.usecase.RuleSetPublisher;
import br.com.motorfraude.rules.application.usecase.UpdateScoring;
import br.com.motorfraude.rules.domain.AuditEntry;
import br.com.motorfraude.rules.domain.RuleSetItem;
import br.com.motorfraude.rules.domain.RuleStatus;
import br.com.motorfraude.rules.domain.RuleVersion;
import br.com.motorfraude.rules.domain.Scoring;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T215 — os casos de uso sobre o Postgres real: publicação, atomicidade e 10 ativações simultâneas. */
class UseCasesOnPostgresIT extends PostgresSupport {

    private final Clock clock = Clock.systemUTC();
    private final PostgresRuleRepository rules = new PostgresRuleRepository(jdbc, json);
    private final PostgresRuleSetRepository ruleSets = new PostgresRuleSetRepository(jdbc);
    private final PostgresAuditLog audit = new PostgresAuditLog(jdbc, json);
    private final KernelRuleValidator validator = new KernelRuleValidator();
    private final TransactionalUnitOfWork unitOfWork = new TransactionalUnitOfWork(transactionManager);

    private RuleSetPublisher publisher(AuditLog auditLog) {
        return new RuleSetPublisher(rules, ruleSets, auditLog, validator, unitOfWork, clock);
    }

    private static RuleContent content(String expression) {
        return new RuleContent(expression, Map.of("maxCount", 5), Map.of(), 25, "motivo");
    }

    private CreateRule createRule(AuditLog auditLog) {
        return new CreateRule(rules, auditLog, validator, unitOfWork, clock);
    }

    @Test
    @DisplayName("ativar uma regra nova sobre o seed publica o conjunto 2 com 9 regras; rollback volta a 8")
    void ativarERollback() {
        var publisher = publisher(audit);
        createRule(audit).execute("analista", "R20", content("features.count1m > params.maxCount"));

        var set2 = new ActivateRule(rules, publisher).execute("coord", "R20", 1);

        assertThat(set2.version()).isEqualTo(2);
        assertThat(set2.items()).hasSize(9);
        assertThat(rules.findVersion("R20", 1).orElseThrow().status()).isEqualTo(RuleStatus.ACTIVE);

        var set3 = new RollbackRuleSet(rules, ruleSets, publisher).execute("coord", 1);

        assertThat(set3.version()).isEqualTo(3);
        assertThat(set3.items()).isEqualTo(ruleSets.findByVersion(1).orElseThrow().items());
        assertThat(rules.findVersion("R20", 1).orElseThrow().status()).isEqualTo(RuleStatus.DISABLED);
        assertThat(ruleSets.findByVersion(2)).isPresent();
        assertThat(audit.recent(3)).extracting(AuditEntry::action).containsExactly("RULESET_ROLLED_BACK", "RULE_ACTIVATED", "RULE_CREATED");
    }

    @Test
    @DisplayName("desligar e mudar scoring sobre o seed: kill switch da R10 e faixas novas")
    void desligarEScoring() {
        var publisher = publisher(audit);

        var semR10 = new DisableRule(rules, publisher).execute("analista", "R10");
        var faixas = new UpdateScoring(publisher).execute("coord", 30, 60);

        assertThat(semR10.items()).extracting(RuleSetItem::ruleId).doesNotContain("R10").hasSize(7);
        assertThat(faixas.version()).isEqualTo(3);
        assertThat(faixas.scoring()).isEqualTo(new Scoring(30, 60));
        assertThat(faixas.items()).isEqualTo(semR10.items());
        assertThat(rules.findVersion("R10", 1).orElseThrow().status()).isEqualTo(RuleStatus.DISABLED);
    }

    @Test
    @DisplayName("falha no meio da publicação desfaz tudo: nem conjunto, nem status, nem auditoria")
    void atomicidade() {
        createRule(audit).execute("analista", "R20", content("features.count1m > params.maxCount"));
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
        int auditoriaAntes = audit.recent(100).size();

        assertThatThrownBy(() -> new ActivateRule(rules, publisher(quebrada)).execute("coord", "R20", 1))
                .isInstanceOf(IllegalStateException.class);

        assertThat(ruleSets.findActive().orElseThrow().version()).isEqualTo(1);
        assertThat(rules.findVersion("R20", 1).orElseThrow().status()).isEqualTo(RuleStatus.DRAFT);
        assertThat(audit.recent(100)).hasSize(auditoriaAntes);
    }

    @Test
    @DisplayName("conjunto que não compila não publica nada no banco")
    void compilacaoFalha() {
        createRule(audit).execute("analista", "R20", content("features.count1m > params.maxCount"));
        // versão que o validador do conjunto recusa, gravada fora do fluxo normal
        rules.saveVersion(RuleVersion.draft("R20", 2, "features.nada > 1", Map.of(), Map.of(), 10, "m", "analista", clock.instant()));

        assertThatThrownBy(() -> new ActivateRule(rules, publisher(audit)).execute("coord", "R20", 2))
                .isInstanceOf(RuleSetInvalidException.class);

        assertThat(ruleSets.findActive().orElseThrow().version()).isEqualTo(1);
        assertThat(rules.findVersion("R20", 2).orElseThrow().status()).isEqualTo(RuleStatus.DRAFT);
    }

    @Test
    @DisplayName("dez ativações simultâneas: versões 2 a 11, sem buraco nem repetição, e todas as regras no conjunto final")
    void concorrencia() throws Exception {
        var create = createRule(audit);
        var activate = new ActivateRule(rules, publisher(audit));
        for (int i = 1; i <= 10; i++) {
            create.execute("analista", "N" + String.format("%02d", i), content("features.count1m > params.maxCount"));
        }

        try (var pool = Executors.newFixedThreadPool(10)) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 1; i <= 10; i++) {
                String id = "N" + String.format("%02d", i);
                futures.add(pool.submit(() -> activate.execute("coord", id, 1)));
            }
            for (var future : futures) {
                future.get();
            }
        }

        var versoes = jdbc.sql("select version from rule_sets order by version").query(Long.class).list();
        assertThat(versoes).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
        assertThat(ruleSets.findActive().orElseThrow().items()).hasSize(18);
        assertThat(jdbc.sql("select count(*) from audit_log where action = 'RULE_ACTIVATED'").query(Integer.class).single()).isEqualTo(10);
    }
}
