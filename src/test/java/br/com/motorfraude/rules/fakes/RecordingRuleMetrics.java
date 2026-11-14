package br.com.motorfraude.rules.fakes;

import br.com.motorfraude.rules.application.gateway.RuleMetrics;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingRuleMetrics implements RuleMetrics {

    private final List<String> published = new CopyOnWriteArrayList<>();
    private final List<String> failures = new CopyOnWriteArrayList<>();

    @Override
    public void published(String action) {
        published.add(action);
    }

    @Override
    public void compilationFailed(String kind) {
        failures.add(kind);
    }

    public List<String> published() {
        return List.copyOf(published);
    }

    public List<String> failures() {
        return List.copyOf(failures);
    }
}
