package br.com.motorfraude.rules.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = RuleManagementApplication.class)
@AutoConfigureMockMvc
class IdempotencyApiIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcClient jdbc;

    private static String body(String ruleId) {
        return """
                { "ruleId": "%s", "expression": "features.count1m > params.maxCount", "params": { "maxCount": 5 },
                  "paramOverrides": {}, "weight": 25, "reasonTemplate": "Mais de {params.maxCount} transações em 1 min" }
                """.formatted(ruleId);
    }

    private static String newId() {
        return "I" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private int versions(String ruleId) {
        return jdbc.sql("select count(*) from rule_versions where rule_id = :id").param("id", ruleId).query(Integer.class).single();
    }

    @Test
    @DisplayName("a mesma chave duas vezes: a segunda repete o 201 e o banco tem uma só versão")
    void repete() throws Exception {
        var id = newId();
        var key = "k-" + id;
        var first = mvc.perform(post("/v1/rules").with(httpBasic("analista", "analista-local")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body(id))).andReturn();
        var second = mvc.perform(post("/v1/rules").with(httpBasic("analista", "analista-local")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body(id))).andReturn();

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(versions(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("dez requisições simultâneas com a mesma chave: a ação acontece uma única vez")
    void simultaneas() throws Exception {
        var id = newId();
        var key = "k-" + id;
        var statuses = new ArrayList<Integer>();
        try (var pool = Executors.newFixedThreadPool(10)) {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(() -> mvc.perform(post("/v1/rules").with(httpBasic("analista", "analista-local"))
                                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body(id)))
                        .andReturn().getResponse().getStatus()));
            }
            for (var future : futures) {
                statuses.add(future.get());
            }
        }

        assertThat(statuses).allMatch(s -> s == 201 || s == 409);
        assertThat(statuses).contains(201);
        assertThat(versions(id)).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from audit_log where action = 'RULE_CREATED' and entity = :e").param("e", "rule:" + id)
                .query(Integer.class).single()).isEqualTo(1);
        var depois = mvc.perform(post("/v1/rules").with(httpBasic("analista", "analista-local")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body(id))).andReturn().getResponse();
        assertThat(depois.getStatus()).isEqualTo(201);
        assertThat(depois.getHeader("Idempotent-Replayed")).isEqualTo("true");
    }

    @Test
    @DisplayName("requisição negada pela segurança não consome a chave")
    void negadaNaoGastaAChave() throws Exception {
        var id = newId();
        var key = "k-" + id;

        var negada = mvc.perform(post("/v1/rules").with(httpBasic("motor", "motor-local")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body(id))).andReturn().getResponse();
        var valida = mvc.perform(post("/v1/rules").with(httpBasic("analista", "analista-local")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body(id))).andReturn().getResponse();

        assertThat(negada.getStatus()).isEqualTo(403);
        assertThat(valida.getStatus()).isEqualTo(201);
        assertThat(valida.getHeader("Idempotent-Replayed")).isNull();
        assertThat(jdbc.sql("select count(*) from idempotency_keys where idempotency_key = :k").param("k", key).query(Integer.class).single()).isEqualTo(1);
    }
}
