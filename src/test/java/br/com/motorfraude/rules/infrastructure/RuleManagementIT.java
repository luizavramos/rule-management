package br.com.motorfraude.rules.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.motorfraude.contracts.Schemas;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** T217–T220 — o serviço inteiro (Spring, segurança, Flyway e Postgres reais): papéis, seed servido e o roteiro da demo (SC-001). */
@SpringBootTest(classes = RuleManagementApplication.class)
@AutoConfigureMockMvc
// O banco é reiniciado entre os testes; o cache do conjunto ativo é por versão (que só cresce em produção), então cada teste ganha um contexto novo.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RuleManagementIT {

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
    private DataSource dataSource;
    @Autowired
    private JdbcClient jdbc;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void freshDatabase() {
        var flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder as(String user, MockHttpServletRequestBuilder request) {
        return request.with(httpBasic(user, user + "-local"));
    }

    private static String ruleBody(String ruleId) {
        return """
                { "ruleId": "%s", "expression": "features.count1m > params.maxCount", "params": { "maxCount": 5 },
                  "paramOverrides": { "PJ": { "maxCount": 30 } }, "weight": 25,
                  "reasonTemplate": "Mais de {params.maxCount} transações em 1 min" }
                """.formatted(ruleId);
    }

    private static String newId() {
        return "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Test
    @DisplayName("sem credencial ou com senha errada: 401 em Problem Details")
    void naoAutenticado() throws Exception {
        mvc.perform(get("/v1/rules")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/v1/rules").with(httpBasic("analista", "errada"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("papéis: analista cria mas não ativa; coordenador faz as duas; motor só lê")
    void papeis() throws Exception {
        var id = newId();
        mvc.perform(json(as("analista", post("/v1/rules")), ruleBody(id))).andExpect(status().isCreated());

        mvc.perform(as("analista", post("/v1/rules/" + id + "/versions/1:activate")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(json(as("analista", put("/v1/scoring")), "{\"mediumThreshold\":30,\"highThreshold\":60}")).andExpect(status().isForbidden());
        mvc.perform(as("analista", post("/v1/rule-sets/1:rollback"))).andExpect(status().isForbidden());

        mvc.perform(json(as("motor", post("/v1/rules")), ruleBody(newId()))).andExpect(status().isForbidden());
        mvc.perform(as("motor", get("/v1/rules"))).andExpect(status().isOk());
        mvc.perform(as("motor", get("/v1/rule-sets/active"))).andExpect(status().isOk());

        mvc.perform(as("coordenador", post("/v1/rules/" + id + "/versions/1:activate"))).andExpect(status().isOk());
        mvc.perform(json(as("coordenador", post("/v1/rules")), ruleBody(newId()))).andExpect(status().isCreated());
        mvc.perform(as("analista", get("/v1/rule-sets/active"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("saúde e métricas respondem sem login; rotas que não existem na API são negadas")
    void saudeEMetricas() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
        mvc.perform(as("coordenador", get("/v1/qualquer"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("o seed é servido ao motor: conjunto 1 com as 8 regras, ETag \"1\", válido contra rule-set.v1, e 304 se não mudou")
    void seedServido() throws Exception {
        var result = mvc.perform(as("motor", get("/v1/rule-sets/active")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.rules.length()").value(8))
                .andExpect(jsonPath("$.scoring.mediumThreshold").value(40))
                .andExpect(jsonPath("$.scoring.highThreshold").value(70))
                .andReturn();

        assertThat(Schemas.validate("rule-set.v1", json.readTree(result.getResponse().getContentAsString())).errors()).isEmpty();
        mvc.perform(as("motor", get("/v1/rule-sets/active")).header(HttpHeaders.IF_NONE_MATCH, "\"1\"")).andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("SC-001: validar, criar, sombra, ativar, mudar o threshold, desligar e reverter pela API, em menos de 1 minuto, sem reiniciar nada")
    void roteiroDaDemo() throws Exception {
        long inicio = System.nanoTime();
        var id = newId();

        mvc.perform(json(as("analista", post("/v1/rules:validate")), ruleBody(id).replace("}\n", ",\"testCases\":[{\"name\":\"6 dispara\",\"context\":{\"features.count1m\":6},\"expectedHit\":true}]}\n")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.cases[0].passed").value(true));
        mvc.perform(json(as("analista", post("/v1/rules")), ruleBody(id))).andExpect(status().isCreated());
        mvc.perform(as("analista", post("/v1/rules/" + id + "/versions/1:shadow")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(as("motor", get("/v1/rule-sets/active")))
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.rules[?(@.ruleId=='" + id + "')].status").value("SHADOW"));

        mvc.perform(as("coordenador", post("/v1/rules/" + id + "/versions/1:activate")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        mvc.perform(json(as("analista", post("/v1/rules/" + id + "/versions")),
                        "{\"expression\":\"features.count1m > params.maxCount\",\"params\":{\"maxCount\":3},\"paramOverrides\":{},\"weight\":25,\"reasonTemplate\":\"limite menor\"}"))
                .andExpect(status().isCreated());
        mvc.perform(as("coordenador", post("/v1/rules/" + id + "/versions/2:activate"))).andExpect(status().isOk());
        mvc.perform(as("motor", get("/v1/rule-sets/active")))
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""))
                .andExpect(jsonPath("$.rules[?(@.ruleId=='" + id + "')].params.maxCount").value(3));

        mvc.perform(json(as("coordenador", put("/v1/scoring")), "{\"mediumThreshold\":30,\"highThreshold\":60}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(5));
        mvc.perform(as("analista", post("/v1/rules/" + id + ":disable")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(6));
        mvc.perform(as("coordenador", post("/v1/rule-sets/4:rollback")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(7)).andExpect(jsonPath("$.scoring.mediumThreshold").value(40));

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(60));
        mvc.perform(as("motor", get("/v1/rule-sets/active"))).andExpect(header().string(HttpHeaders.ETAG, "\"7\""));
    }

    @Test
    @DisplayName("H2.4: cada mudança fica no audit_log com quem, quando, o antes e o depois")
    void auditoria() throws Exception {
        var id = newId();
        mvc.perform(json(as("analista", post("/v1/rules")), ruleBody(id))).andExpect(status().isCreated());
        mvc.perform(as("coordenador", post("/v1/rules/" + id + "/versions/1:activate"))).andExpect(status().isOk());

        var row = jdbc.sql("select actor, before::text as before, after::text as after, at is not null as has_time from audit_log where action = 'RULE_ACTIVATED' and entity = :e")
                .param("e", "rule:" + id + "@v1").query((rs, n) -> new String[] {rs.getString("actor"), rs.getString("before"), rs.getString("after"), String.valueOf(rs.getBoolean("has_time"))}).single();

        assertThat(row[0]).isEqualTo("coordenador");
        assertThat(row[1]).contains("\"ruleSetVersion\": 1");
        assertThat(row[2]).contains("\"ruleSetVersion\": 2").contains(id);
        assertThat(row[3]).isEqualTo("true");
        assertThat(jdbc.sql("select count(*) from audit_log where action = 'RULE_CREATED' and actor = 'analista'").query(Integer.class).single()).isEqualTo(1);
    }
}
