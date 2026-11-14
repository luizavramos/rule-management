package br.com.motorfraude.rules.adapters.controller;

import br.com.motorfraude.rules.adapters.presenter.RestExceptionHandler;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.com.motorfraude.rules.adapters.gateway.kernel.KernelRuleValidator;
import br.com.motorfraude.rules.fakes.Fixture;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

abstract class RestTestSupport {

    protected static final String RULE_BODY = """
            {
              "ruleId": "R07",
              "expression": "features.count5m > params.maxCount",
              "params": { "maxCount": 5 },
              "paramOverrides": { "PJ": { "maxCount": 30 } },
              "weight": 25,
              "reasonTemplate": "Mais de {params.maxCount} transações em 5 min"
            }
            """;

    protected Fixture fixture;
    protected MockMvc mvc;

    @BeforeEach
    void setUpRest() {
        fixture = new Fixture(new KernelRuleValidator());
        mvc = MockMvcBuilders.standaloneSetup(
                        new RuleController(fixture.createRule, fixture.createRuleVersion, fixture.validateRule, fixture.activateRule,
                                fixture.shadowRule, fixture.disableRule, new br.com.motorfraude.rules.application.usecase.ListRules(fixture.rules),
                                new br.com.motorfraude.rules.application.usecase.GetRule(fixture.rules)),
                        new RuleSetController(fixture.rollbackRuleSet, fixture.getActiveRuleSet),
                        new ScoringController(fixture.updateScoring))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    protected static MockHttpServletRequestBuilder asAuthor(MockHttpServletRequestBuilder request) {
        return request.principal(() -> "analista");
    }

    protected static MockHttpServletRequestBuilder asApprover(MockHttpServletRequestBuilder request) {
        return request.principal(() -> "coordenador");
    }

    protected MockHttpServletRequestBuilder postJson(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder putJson(String path, String body) {
        return put(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected void createRule() {
        fixture.createRule.execute("analista", "R07", new br.com.motorfraude.rules.application.usecase.RuleContent(
                "features.count5m > params.maxCount", java.util.Map.of("maxCount", 5),
                java.util.Map.of("PJ", java.util.Map.of("maxCount", 30)), 25, "Mais de {params.maxCount} transações em 5 min"));
    }
}
