package br.com.motorfraude.rules.adapters.gateway.kernel;

import br.com.motorfraude.rules.application.gateway.RuleValidator;
import br.com.motorfraude.rules.domain.RuleVersion;
import br.com.motorfraude.rules.domain.Scoring;
import br.com.motorfraude.rules.kernel.catalog.FeatureCatalog;
import br.com.motorfraude.rules.kernel.compiler.RuleCompiler;
import br.com.motorfraude.rules.kernel.compiler.RuleSetCompilationException;
import br.com.motorfraude.rules.kernel.evaluation.EvaluationContext;
import br.com.motorfraude.rules.kernel.model.RuleDefinition;
import br.com.motorfraude.rules.kernel.model.RuleSetDefinition;
import br.com.motorfraude.rules.kernel.model.RuleStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class KernelRuleValidator implements RuleValidator {

    private final RuleCompiler compiler = new RuleCompiler();
    private final FeatureCatalog catalog = FeatureCatalog.standardCatalog();

    @Override
    public List<String> validate(RuleVersion version) {
        return compiler.compile(toKernel(version, RuleStatus.ACTIVE)).errors();
    }

    @Override
    public List<SetIssue> validateSet(Scoring scoring, List<RuleVersion> versions) {
        var rules = versions.stream()
                .map(v -> toKernel(v, RuleStatus.valueOf(v.status().name())))
                .toList();
        try {
            compiler.compileAll(new RuleSetDefinition(0, Instant.EPOCH,
                    new br.com.motorfraude.rules.kernel.model.Scoring(scoring.mediumThreshold(), scoring.highThreshold()), rules));
            return List.of();
        } catch (RuleSetCompilationException e) {
            return e.failures().stream().map(f -> new SetIssue(f.ruleId(), f.errors())).toList();
        }
    }

    @Override
    public List<CaseResult> runCases(RuleVersion version, List<TestCase> cases) {
        var results = new ArrayList<CaseResult>();
        var definition = toKernel(version, RuleStatus.ACTIVE);
        br.com.motorfraude.rules.kernel.compiler.CompiledRuleSet compiled;
        try {
            compiled = compiler.compileAll(new RuleSetDefinition(0, Instant.EPOCH,
                    new br.com.motorfraude.rules.kernel.model.Scoring(1, 1), List.of(definition)));
        } catch (RuleSetCompilationException e) {
            cases.forEach(c -> results.add(new CaseResult(c.name(), c.expectedHit(), false, false, e.getMessage())));
            return results;
        }
        for (TestCase testCase : cases) {
            try {
                var evaluation = compiled.evaluate(context(testCase.context()));
                if (!evaluation.errors().isEmpty()) {
                    results.add(new CaseResult(testCase.name(), testCase.expectedHit(), false, false, evaluation.errors().get(0).message()));
                    continue;
                }
                boolean hit = !evaluation.activeHits().isEmpty();
                results.add(new CaseResult(testCase.name(), testCase.expectedHit(), hit, hit == testCase.expectedHit(), null));
            } catch (IllegalArgumentException e) {
                results.add(new CaseResult(testCase.name(), testCase.expectedHit(), false, false, e.getMessage()));
            }
        }
        return results;
    }

    private EvaluationContext context(Map<String, Object> given) {
        var builder = EvaluationContext.builder();
        catalog.features().forEach((name, type) -> builder.put(name, switch (type) {
            case INT -> 0L;
            case BOOL -> false;
            case STRING -> "";
        }));
        builder.put("profile.hoursSinceCredentialChange", -1L);
        builder.put("profile.segment", "PF");
        given.forEach(builder::put);
        return builder.build();
    }

    private static RuleDefinition toKernel(RuleVersion v, RuleStatus status) {
        return new RuleDefinition(v.ruleId(), v.version(), status, v.expression(), v.params(), v.paramOverrides(),
                v.weight(), v.reasonTemplate());
    }
}
