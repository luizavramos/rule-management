package br.com.motorfraude.rules.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = RuleManagementApplication.class)
@AutoConfigureMockMvc
class MetricsApiIT {

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

    private String prometheus() throws Exception {
        return mvc.perform(get("/actuator/prometheus")).andReturn().getResponse().getContentAsString();
    }

    private static String ruleBody(String id, String expression) {
        return """
                { "ruleId": "%s", "expression": "%s", "params": { "maxCount": 5 }, "paramOverrides": {}, "weight": 25, "reasonTemplate": "motivo" }
                """.formatted(id, expression);
    }

    private static double sample(String text, String prefix) {
        return text.lines().filter(l -> l.startsWith(prefix)).mapToDouble(l -> Double.parseDouble(l.substring(l.lastIndexOf(' ') + 1))).sum();
    }

    @Test
    @DisplayName("publicação, falha de compilação e leitura do conjunto ativo aparecem no endpoint do Prometheus")
    void metricasNoPrometheus() throws Exception {
        var antes = prometheus();
        double ativacoesAntes = sample(antes, "rules_published_total{action=\"RULE_ACTIVATED\"");
        double falhasAntes = sample(antes, "rules_compilation_failures_total{kind=\"rule\"");
        var id = "M" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        mvc.perform(post("/v1/rules").with(httpBasic("analista", "analista-local")).contentType(MediaType.APPLICATION_JSON)
                .content(ruleBody(id, "features.count1m > params.maxCount"))).andReturn();
        mvc.perform(post("/v1/rules").with(httpBasic("analista", "analista-local")).contentType(MediaType.APPLICATION_JSON)
                .content(ruleBody(id + "X", "features.nada > 1"))).andReturn();
        mvc.perform(post("/v1/rules/" + id + "/versions/1:activate").with(httpBasic("coordenador", "coordenador-local"))).andReturn();
        var first = mvc.perform(get("/v1/rule-sets/active").with(httpBasic("motor", "motor-local"))).andReturn().getResponse();
        mvc.perform(get("/v1/rule-sets/active").with(httpBasic("motor", "motor-local"))
                .header(HttpHeaders.IF_NONE_MATCH, first.getHeader(HttpHeaders.ETAG))).andReturn();

        var depois = prometheus();
        assertThat(sample(depois, "rules_published_total{action=\"RULE_ACTIVATED\"")).isEqualTo(ativacoesAntes + 1);
        assertThat(sample(depois, "rules_compilation_failures_total{kind=\"rule\"")).isEqualTo(falhasAntes + 1);
        assertThat(depois).contains("rule_set_active_request_seconds_count{").contains("status=\"200\"").contains("status=\"304\"");
        assertThat(depois).contains("rule_set_active_request_seconds_bucket{");
        assertThat(sample(depois, "rule_set_active_request_seconds_count{")).isGreaterThanOrEqualTo(2);
    }
}
