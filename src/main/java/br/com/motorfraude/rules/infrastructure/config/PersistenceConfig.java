package br.com.motorfraude.rules.infrastructure.config;

import br.com.motorfraude.rules.adapters.out.postgres.PostgresAuditLog;
import br.com.motorfraude.rules.adapters.out.postgres.PostgresRuleRepository;
import br.com.motorfraude.rules.adapters.out.postgres.PostgresRuleSetRepository;
import br.com.motorfraude.rules.adapters.out.postgres.TransactionalUnitOfWork;
import br.com.motorfraude.rules.application.port.out.AuditLog;
import br.com.motorfraude.rules.application.port.out.RuleRepository;
import br.com.motorfraude.rules.application.port.out.RuleSetRepository;
import br.com.motorfraude.rules.application.port.out.UnitOfWork;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/** Liga as portas de saída aos adaptadores Postgres e aplica as migrações do Flyway ao subir. */
@Configuration
public class PersistenceConfig {

    /** Migra antes de tudo: o serviço só atende com o schema e o seed (as 8 regras) no lugar. */
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
    UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
        return new TransactionalUnitOfWork(transactionManager);
    }
}
