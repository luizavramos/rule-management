package br.com.motorfraude.rules.domain;

import java.time.Instant;
import java.util.List;

/** Conjunto publicado: imutável, com versão crescente. O "ativo" é o de maior versão. */
public record RuleSet(long version, Instant publishedAt, String publishedBy, Scoring scoring, List<RuleSetItem> items) {

    public RuleSet {
        items = List.copyOf(items);
    }

    /** O item da regra neste conjunto, ou nulo se a regra não está nele. */
    public RuleSetItem item(String ruleId) {
        return items.stream().filter(i -> i.ruleId().equals(ruleId)).findFirst().orElse(null);
    }
}
