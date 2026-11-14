package br.com.motorfraude.rules.infrastructure.config;

import br.com.motorfraude.rules.adapters.gateway.postgres.PostgresAuditLog;
import br.com.motorfraude.rules.adapters.gateway.postgres.PostgresIdempotencyStore;
import br.com.motorfraude.rules.adapters.gateway.postgres.PostgresRuleRepository;
import br.com.motorfraude.rules.adapters.gateway.postgres.PostgresRuleSetOutbox;
import br.com.motorfraude.rules.adapters.gateway.postgres.PostgresRuleSetRepository;
import br.com.motorfraude.rules.adapters.gateway.postgres.TransactionalUnitOfWork;
import br.com.motorfraude.rules.application.gateway.AuditLog;
import br.com.motorfraude.rules.application.gateway.IdempotencyStore;
import br.com.motorfraude.rules.application.gateway.RuleRepository;
import br.com.motorfraude.rules.application.gateway.RuleSetOutbox;
import br.com.motorfraude.rules.application.gateway.RuleSetRepository;
import br.com.motorfraude.rules.application.gateway.UnitOfWork;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class PersistenceConfig {

    @Bean(initMethod = "migrate")
    Flyway flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).load();
    }

    @Bean
    RuleRepository ruleRepository(JdbcClient jdbc, JsonMapper json, Flyway flyway) {
        return new PostgresRuleRepository(jdbc, json);
    }

    @Bean
    RuleSetRepository ruleSetRepository(JdbcClient jdbc, Flyway flyway) {
        return new PostgresRuleSetRepository(jdbc);
    }

    @Bean
    AuditLog auditLog(JdbcClient jdbc, JsonMapper json, Flyway flyway) {
        return new PostgresAuditLog(jdbc, json);
    }

    @Bean
    RuleSetOutbox ruleSetOutbox(JdbcClient jdbc, JsonMapper json, Flyway flyway) {
        return new PostgresRuleSetOutbox(jdbc, json);
    }

    @Bean
    IdempotencyStore idempotencyStore(JdbcClient jdbc, Flyway flyway) {
        return new PostgresIdempotencyStore(jdbc);
    }

    @Bean
    UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
        return new TransactionalUnitOfWork(transactionManager);
    }
}
