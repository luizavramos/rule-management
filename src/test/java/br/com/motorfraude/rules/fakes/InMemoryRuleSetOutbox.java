package br.com.motorfraude.rules.fakes;

import br.com.motorfraude.rules.application.gateway.RuleSetOutbox;
import br.com.motorfraude.rules.application.usecase.ActiveRuleSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryRuleSetOutbox implements RuleSetOutbox {

    private final List<ActiveRuleSet> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(ActiveRuleSet published) {
        events.add(published);
    }

    public List<ActiveRuleSet> events() {
        return List.copyOf(events);
    }
}
