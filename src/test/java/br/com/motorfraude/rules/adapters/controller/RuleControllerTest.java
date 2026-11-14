package br.com.motorfraude.rules.adapters.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class RuleControllerTest extends RestTestSupport {

    private static final MediaType PROBLEM = MediaType.valueOf("application/problem+json");

    @Test
    @DisplayName("H1.1: criar regra válida devolve 201 com a versão 1 em DRAFT e o Location")
    void cria() throws Exception {
        mvc.perform(asAuthor(postJson("/v1/rules", RULE_BODY)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/rules/R07/versions/1"))
                .andExpect(jsonPath("$.ruleId").value("R07"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.author").value("analista"))
                .andExpect(jsonPath("$.params.maxCount").value(5))
                .andExpect(jsonPath("$.paramOverrides.PJ.maxCount").value(30));
    }

    @Test
    @DisplayName("H1.2: feature inexistente devolve 422 em Problem Details com a mensagem do kernel")
    void featureDesconhecida() throws Exception {
        var body = RULE_BODY.replace("features.count5m > params.maxCount", "features.contagem5m > 3");

        mvc.perform(asAuthor(postJson("/v1/rules", body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.errors[0]").value("feature desconhecida: contagem5m"));
    }

    @Test
    @DisplayName("H1.3: tipo errado devolve 422 com erro de tipo")
    void tipoErrado() throws Exception {
        var body = RULE_BODY.replace("features.count5m > params.maxCount", "tx.amount > \\\"mil\\\"");

        mvc.perform(asAuthor(postJson("/v1/rules", body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("erro de tipo:")));
    }

    @Test
    @DisplayName("campo fora dos limites do contrato (peso 5000) devolve 400 com o erro do campo")
    void campoInvalido() throws Exception {
        mvc.perform(asAuthor(postJson("/v1/rules", RULE_BODY.replace("\"weight\": 25", "\"weight\": 5000"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors[0]").value("weight deve estar entre 0 e 1000"));
    }

    @Test
    @DisplayName("JSON malformado e campo obrigatório ausente devolvem 400")
    void corpoRuim() throws Exception {
        mvc.perform(asAuthor(postJson("/v1/rules", "{ nao e json")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(asAuthor(postJson("/v1/rules", "{\"ruleId\": \"R07\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    @DisplayName("id repetido devolve 409")
    void duplicada() throws Exception {
        createRule();

        mvc.perform(asAuthor(postJson("/v1/rules", RULE_BODY)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    @DisplayName("H4.1: nova versão só com o threshold novo devolve 201 com a versão 2; regra inexistente é 404")
    void novaVersao() throws Exception {
        createRule();
        var body = """
                { "expression": "features.count5m > params.maxCount", "params": { "maxCount": 3 },
                  "paramOverrides": {}, "weight": 25, "reasonTemplate": "motivo" }
                """;

        mvc.perform(asAuthor(postJson("/v1/rules/R07/versions", body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/rules/R07/versions/2"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.params.maxCount").value(3));
        mvc.perform(asAuthor(postJson("/v1/rules/NAO/versions", body)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    @DisplayName("listar e consultar o histórico; regra inexistente é 404")
    void consultas() throws Exception {
        createRule();

        mvc.perform(get("/v1/rules")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleId").value("R07"));
        mvc.perform(get("/v1/rules/R07")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId").value("R07"))
                .andExpect(jsonPath("$.versions[0].version").value(1))
                .andExpect(jsonPath("$.versions[0].expression").value("features.count5m > params.maxCount"));
        mvc.perform(get("/v1/rules/NAO")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("H1.4: :validate devolve o resultado de cada caso de teste")
    void validarComCasos() throws Exception {
        var body = """
                { "expression": "features.count5m > params.maxCount", "params": { "maxCount": 5 },
                  "paramOverrides": { "PJ": { "maxCount": 30 } }, "weight": 25, "reasonTemplate": "motivo",
                  "testCases": [
                    { "name": "6 transações dispara", "context": { "features.count5m": 6 }, "expectedHit": true },
                    { "name": "PJ com 6 não dispara", "context": { "features.count5m": 6, "profile.segment": "PJ" }, "expectedHit": false },
                    { "name": "esperava disparar com 1", "context": { "features.count5m": 1 }, "expectedHit": true }
                  ] }
                """;

        mvc.perform(asAuthor(postJson("/v1/rules:validate", body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.cases.length()").value(3))
                .andExpect(jsonPath("$.cases[0].passed").value(true))
                .andExpect(jsonPath("$.cases[1].passed").value(true))
                .andExpect(jsonPath("$.cases[2].passed").value(false))
                .andExpect(jsonPath("$.cases[2].actualHit").value(false));
    }

    @Test
    @DisplayName(":validate com expressão inválida devolve 200 e valid=false com os erros (não é um erro da chamada)")
    void validarInvalida() throws Exception {
        var body = RULE_BODY.replace("features.count5m > params.maxCount", "features.contagem5m > 3");

        mvc.perform(asAuthor(postJson("/v1/rules:validate", body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0]").value("feature desconhecida: contagem5m"));
        mvc.perform(asAuthor(get("/v1/rules"))).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("H2.1: ativar publica o conjunto e devolve 200 com a regra ACTIVE")
    void ativa() throws Exception {
        createRule();

        mvc.perform(asApprover(postJson("/v1/rules/R07/versions/1:activate", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.publishedBy").value("coordenador"))
                .andExpect(jsonPath("$.scoring.mediumThreshold").value(40))
                .andExpect(jsonPath("$.rules[0].ruleId").value("R07"))
                .andExpect(jsonPath("$.rules[0].ruleVersion").value(1))
                .andExpect(jsonPath("$.rules[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("ativar versão inexistente é 404; ativar o que já está ativo é 409")
    void ativaErros() throws Exception {
        createRule();
        mvc.perform(asApprover(postJson("/v1/rules/R07/versions/9:activate", ""))).andExpect(status().isNotFound());
        mvc.perform(asApprover(postJson("/v1/rules/R07/versions/1:activate", ""))).andExpect(status().isOk());

        mvc.perform(asApprover(postJson("/v1/rules/R07/versions/1:activate", "")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    @DisplayName("H5.1: :shadow coloca a regra no conjunto com status SHADOW")
    void sombra() throws Exception {
        createRule();

        mvc.perform(asAuthor(postJson("/v1/rules/R07/versions/1:shadow", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[0].status").value("SHADOW"));
    }

    @Test
    @DisplayName("H2.2: :disable publica o conjunto sem a regra; desligar o que não está no conjunto é 409")
    void desliga() throws Exception {
        createRule();
        mvc.perform(asApprover(postJson("/v1/rules/R07/versions/1:activate", ""))).andExpect(status().isOk());

        mvc.perform(asAuthor(postJson("/v1/rules/R07:disable", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.rules").isEmpty());
        mvc.perform(asAuthor(postJson("/v1/rules/R07:disable", "")))
                .andExpect(status().isConflict());
        mvc.perform(asAuthor(postJson("/v1/rules/NAO:disable", ""))).andExpect(status().isNotFound());
    }
}
