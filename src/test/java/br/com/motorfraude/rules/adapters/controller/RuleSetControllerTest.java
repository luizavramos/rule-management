package br.com.motorfraude.rules.adapters.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.motorfraude.contracts.Schemas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.json.JsonMapper;

/** T217 e T219 — rollback, scoring e o conjunto ativo servido ao motor (ETag, 304 e schema rule-set.v1). */
class RuleSetControllerTest extends RestTestSupport {

    private final JsonMapper json = JsonMapper.builder().build();

    private void publicaR07() {
        createRule();
        fixture.activateRule.execute("coordenador", "R07", 1);
    }

    @Test
    @DisplayName("H2.3: rollback publica N+k com o conteúdo de N e devolve 200")
    void rollback() throws Exception {
        publicaR07();                                                    // conjunto 1: R07
        fixture.disableRule.execute("analista", "R07");                  // conjunto 2: vazio

        mvc.perform(asApprover(postJson("/v1/rule-sets/1:rollback", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.rules[0].ruleId").value("R07"));
    }

    @Test
    @DisplayName("rollback para versão inexistente é 404; versão que não é número é 400")
    void rollbackErros() throws Exception {
        publicaR07();

        mvc.perform(asApprover(postJson("/v1/rule-sets/99:rollback", "")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(asApprover(postJson("/v1/rule-sets/abc:rollback", ""))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("H4.3: PUT /scoring publica um conjunto com as faixas novas")
    void scoring() throws Exception {
        publicaR07();

        mvc.perform(asApprover(putJson("/v1/scoring", "{\"mediumThreshold\": 30, \"highThreshold\": 60}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.scoring.mediumThreshold").value(30))
                .andExpect(jsonPath("$.scoring.highThreshold").value(60))
                .andExpect(jsonPath("$.rules[0].ruleId").value("R07"));
    }

    @Test
    @DisplayName("scoring inválido (média acima da alta) é 400, e faltando campo também")
    void scoringInvalido() throws Exception {
        publicaR07();

        mvc.perform(asApprover(putJson("/v1/scoring", "{\"mediumThreshold\": 80, \"highThreshold\": 70}")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(asApprover(putJson("/v1/scoring", "{\"mediumThreshold\": 80}"))).andExpect(status().isBadRequest());
        assertThat(fixture.ruleSets.all()).hasSize(1);
    }

    @Test
    @DisplayName("H3.1: GET /active devolve 200 com o conjunto, ETag da versão e o corpo válido contra rule-set.v1")
    void ativoComEtag() throws Exception {
        publicaR07();

        var result = mvc.perform(get("/v1/rule-sets/active"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.scoring.highThreshold").value(70))
                .andExpect(jsonPath("$.rules[0].ruleId").value("R07"))
                .andExpect(jsonPath("$.rules[0].version").value(1))
                .andExpect(jsonPath("$.rules[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.rules[0].params.maxCount").value(5))
                .andExpect(jsonPath("$.rules[0].paramOverrides.PJ.maxCount").value(30))
                .andReturn();

        var validation = Schemas.validate("rule-set.v1", json.readTree(result.getResponse().getContentAsString()));
        assertThat(validation.errors()).isEmpty();
    }

    @Test
    @DisplayName("o conjunto servido usa o status do item (SHADOW), e também valida contra o schema")
    void ativoComSombra() throws Exception {
        createRule();
        fixture.shadowRule.execute("analista", "R07", 1);

        var result = mvc.perform(get("/v1/rule-sets/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[0].status").value("SHADOW"))
                .andReturn();

        assertThat(Schemas.validate("rule-set.v1", json.readTree(result.getResponse().getContentAsString())).errors()).isEmpty();
    }

    @Test
    @DisplayName("H3.2: If-None-Match com a versão atual devolve 304 sem corpo")
    void naoMudou() throws Exception {
        publicaR07();

        var result = mvc.perform(get("/v1/rule-sets/active").header(HttpHeaders.IF_NONE_MATCH, "\"1\""))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("versão publicada depois do If-None-Match: 200 com o conjunto e o ETag novo")
    void mudou() throws Exception {
        publicaR07();
        fixture.updateScoring.execute("coordenador", 30, 60);

        mvc.perform(get("/v1/rule-sets/active").header(HttpHeaders.IF_NONE_MATCH, "\"1\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.scoring.mediumThreshold").value(30));
    }

    @Test
    @DisplayName("If-None-Match com lista de ETags ou curinga também funciona; ETag que não é número é ignorado")
    void variantesDoHeader() throws Exception {
        publicaR07();

        mvc.perform(get("/v1/rule-sets/active").header(HttpHeaders.IF_NONE_MATCH, "\"0\", \"1\"")).andExpect(status().isNotModified());
        mvc.perform(get("/v1/rule-sets/active").header(HttpHeaders.IF_NONE_MATCH, "*")).andExpect(status().isNotModified());
        mvc.perform(get("/v1/rule-sets/active").header(HttpHeaders.IF_NONE_MATCH, "\"abc\"")).andExpect(status().isOk());
        mvc.perform(get("/v1/rule-sets/active").header(HttpHeaders.IF_NONE_MATCH, "W/\"1\"")).andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("sem nenhum conjunto publicado: 404 em Problem Details")
    void semConjunto() throws Exception {
        mvc.perform(get("/v1/rule-sets/active"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
