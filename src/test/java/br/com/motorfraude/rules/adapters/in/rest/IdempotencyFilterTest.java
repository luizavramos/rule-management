package br.com.motorfraude.rules.adapters.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.motorfraude.rules.application.usecase.GetRule;
import br.com.motorfraude.rules.application.usecase.IdempotencyGuard;
import br.com.motorfraude.rules.application.usecase.ListRules;
import br.com.motorfraude.rules.fakes.InMemoryIdempotencyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/** Idempotency-Key nos POSTs e PUTs: a mesma chave não repete a ação, e devolve a mesma resposta. */
class IdempotencyFilterTest extends RestTestSupport {

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    private MockMvc mvcWithKeys;

    @BeforeEach
    void withFilter() {
        var guard = new IdempotencyGuard(store, fixture.clock);
        mvcWithKeys = MockMvcBuilders.standaloneSetup(
                        new RuleController(fixture.createRule, fixture.createRuleVersion, fixture.validateRule, fixture.activateRule,
                                fixture.shadowRule, fixture.disableRule, new ListRules(fixture.rules), new GetRule(fixture.rules)),
                        new RuleSetController(fixture.rollbackRuleSet, fixture.getActiveRuleSet),
                        new ScoringController(fixture.updateScoring))
                .setControllerAdvice(new RestExceptionHandler())
                .addFilters(new IdempotencyFilter(guard, JsonMapper.builder().build()))
                .build();
    }

    private MockHttpServletRequestBuilder keyed(MockHttpServletRequestBuilder request, String key) {
        return request.header("Idempotency-Key", key);
    }

    @Test
    @DisplayName("criar com a mesma chave duas vezes: as duas dão 201 com o mesmo corpo, e só uma regra é criada")
    void criarDuasVezes() throws Exception {
        var first = mvcWithKeys.perform(keyed(asAuthor(postJson("/v1/rules", RULE_BODY)), "criar-r07"))
                .andExpect(status().isCreated()).andReturn();

        var second = mvcWithKeys.perform(keyed(asAuthor(postJson("/v1/rules", RULE_BODY)), "criar-r07"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(header().string("Location", "/v1/rules/R07/versions/1"))
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(first.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(fixture.rules.listVersions("R07")).hasSize(1);
        assertThat(fixture.audit.all()).hasSize(1);
    }

    @Test
    @DisplayName("sem a chave, repetir o POST não é protegido: a segunda vez dá 409 (regra já existe)")
    void semChave() throws Exception {
        mvcWithKeys.perform(asAuthor(postJson("/v1/rules", RULE_BODY))).andExpect(status().isCreated());

        mvcWithKeys.perform(asAuthor(postJson("/v1/rules", RULE_BODY))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("ativar com a mesma chave: não publica um segundo conjunto nem dá 409, repete o 200")
    void ativarDuasVezes() throws Exception {
        createRule();

        var first = mvcWithKeys.perform(keyed(asApprover(postJson("/v1/rules/R07/versions/1:activate", "")), "ativar-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andReturn();
        var second = mvcWithKeys.perform(keyed(asApprover(postJson("/v1/rules/R07/versions/1:activate", "")), "ativar-1"))
                .andExpect(status().isOk()).andExpect(header().string("Idempotent-Replayed", "true")).andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(fixture.ruleSets.all()).hasSize(1);
    }

    @Test
    @DisplayName("PUT /scoring também: a mesma chave não publica de novo")
    void scoringDuasVezes() throws Exception {
        createRule();
        fixture.activateRule.execute("coordenador", "R07", 1);
        var body = "{\"mediumThreshold\": 30, \"highThreshold\": 60}";

        mvcWithKeys.perform(keyed(asApprover(putJson("/v1/scoring", body)), "faixas")).andExpect(status().isOk());
        mvcWithKeys.perform(keyed(asApprover(putJson("/v1/scoring", body)), "faixas"))
                .andExpect(status().isOk()).andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(fixture.ruleSets.all()).hasSize(2);
    }

    @Test
    @DisplayName("a mesma chave com outro corpo é 422: a chave foi reaproveitada para outra requisição")
    void chaveReaproveitada() throws Exception {
        mvcWithKeys.perform(keyed(asAuthor(postJson("/v1/rules", RULE_BODY)), "k")).andExpect(status().isCreated());

        mvcWithKeys.perform(keyed(asAuthor(postJson("/v1/rules", RULE_BODY.replace("R07", "R08"))), "k"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:motorfraude:problem:idempotency-key-reused"));
        assertThat(fixture.rules.findRule("R08")).isEmpty();
    }

    @Test
    @DisplayName("falha não gasta a chave: depois de um 422 a origem corrige o corpo e repete com a mesma chave")
    void falhaLiberaAChave() throws Exception {
        var ruim = RULE_BODY.replace("features.count5m > params.maxCount", "features.contagem5m > 3");
        mvcWithKeys.perform(keyed(asAuthor(postJson("/v1/rules", ruim)), "k")).andExpect(status().isUnprocessableEntity());

        mvcWithKeys.perform(keyed(asAuthor(postJson("/v1/rules", RULE_BODY)), "k"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
    }

    @Test
    @DisplayName("repetir a mesma requisição que falhou executa de novo e falha de novo (não guarda o erro)")
    void erroNaoEGuardado() throws Exception {
        createRule();

        mvcWithKeys.perform(keyed(asApprover(postJson("/v1/rules/R07/versions/9:activate", "")), "k")).andExpect(status().isNotFound());
        fixture.rules.saveVersion(br.com.motorfraude.rules.domain.RuleVersion.draft("R07", 9, "tx.amount > 0",
                java.util.Map.of(), java.util.Map.of(), 10, "m", "analista", fixture.clock.instant()));

        mvcWithKeys.perform(keyed(asApprover(postJson("/v1/rules/R07/versions/9:activate", "")), "k")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("chave em andamento: 409 com Retry-After; chave inválida: 400; GET ignora a chave")
    void casosDeBorda() throws Exception {
        // reserva a chave como se outra requisição ainda estivesse executando
        store.claim("analista", "andando", "qualquer", fixture.clock.instant(), fixture.clock.instant().minusSeconds(86400), fixture.clock.instant().minusSeconds(60));
        mvcWithKeys.perform(keyed(asAuthor(postJson("/v1/rules", RULE_BODY)), "andando"))
                .andExpect(status().isUnprocessableEntity());

        var body = RULE_BODY;
        // mesma requisição (mesmo hash) enquanto outra executa
        var hash = IdempotencyFilter.hash("POST", "/v1/rules", null, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        store.claim("analista", "andando2", hash, fixture.clock.instant(), fixture.clock.instant().minusSeconds(86400), fixture.clock.instant().minusSeconds(60));
        mvcWithKeys.perform(keyed(asAuthor(postJson("/v1/rules", body)), "andando2"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.type").value("urn:motorfraude:problem:idempotency-in-progress"));

        mvcWithKeys.perform(keyed(asAuthor(postJson("/v1/rules", body)), "chave com espaço"))
                .andExpect(status().isBadRequest());
        mvcWithKeys.perform(keyed(asAuthor(postJson("/v1/rules", body)), "x".repeat(256)))
                .andExpect(status().isBadRequest());

        mvcWithKeys.perform(keyed(asAuthor(get("/v1/rules")), "qualquer")).andExpect(status().isOk());
        assertThat(store.find("analista", "qualquer")).isEmpty();
    }
}
