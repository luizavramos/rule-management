package br.com.motorfraude.rules.infrastructure.observability;

import br.com.motorfraude.rules.application.gateway.RuleMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class RuleManagementMetrics implements RuleMetrics {

    private static final String[] ACTIONS = {"RULE_ACTIVATED", "RULE_SHADOWED", "RULE_DISABLED", "RULESET_ROLLED_BACK", "SCORING_UPDATED"};
    private static final String[] FAILURE_KINDS = {"rule", "rule-set"};

    private final MeterRegistry registry;

    public RuleManagementMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (String action : ACTIONS) {
            published(action, 0);
        }
        for (String kind : FAILURE_KINDS) {
            failed(kind, 0);
        }
    }

    @Override
    public void published(String action) {
        published(action, 1);
    }

    @Override
    public void compilationFailed(String kind) {
        failed(kind, 1);
    }

    private void published(String action, double amount) {
        Counter.builder("rules.published").description("Conjuntos de regras publicados, por ação").tag("action", action)
                .register(registry).increment(amount);
    }

    private void failed(String kind, double amount) {
        Counter.builder("rules.compilation.failures").description("Regras ou conjuntos que não compilaram, por tipo").tag("kind", kind)
                .register(registry).increment(amount);
    }
}
