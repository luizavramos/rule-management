package br.com.motorfraude.rules.adapters.gateway.postgres;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** Base dos testes de integração: um Postgres 16 real (o mesmo do compose), com as migrações aplicadas do zero a cada teste. */
abstract class PostgresSupport {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.16");

    static {
        POSTGRES.start();
    }

    protected final DataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    protected final JdbcClient jdbc = JdbcClient.create(dataSource);
    protected final DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
    protected final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void migrate() {
        var flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
    }
}
