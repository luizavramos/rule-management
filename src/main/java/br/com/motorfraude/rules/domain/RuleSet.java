package br.com.motorfraude.rules.domain;

import java.time.Instant;
import java.util.List;

public record RuleSet(long version, Instant publishedAt, String publishedBy, Scoring scoring, List<RuleSetItem> items) {

    public RuleSet {
        items = List.copyOf(items);
    }

    public RuleSetItem item(String ruleId) {
        return items.stream().filter(i -> i.ruleId().equals(ruleId)).findFirst().orElse(null);
    }
}
