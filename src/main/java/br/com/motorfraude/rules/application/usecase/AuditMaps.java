package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.domain.RuleVersion;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Instantâneos (antes e depois) gravados na auditoria. Só tipos simples, para virarem JSON sem surpresa. */
final class AuditMaps {

    private AuditMaps() {}

    static Map<String, Object> of(RuleVersion v) {
        var map = new LinkedHashMap<String, Object>();
        map.put("ruleId", v.ruleId());
        map.put("version", v.version());
        map.put("status", v.status().name());
        map.put("expression", v.expression());
        map.put("params", v.params());
        map.put("paramOverrides", v.paramOverrides());
        map.put("weight", v.weight());
        map.put("author", v.author());
        return map;
    }

    static Map<String, Object> of(RuleSet set) {
        var map = new LinkedHashMap<String, Object>();
        map.put("ruleSetVersion", set.version());
        map.put("scoring", Map.of("mediumThreshold", set.scoring().mediumThreshold(), "highThreshold", set.scoring().highThreshold()));
        List<Map<String, Object>> items = set.items().stream()
                .<Map<String, Object>>map(i -> Map.of("ruleId", i.ruleId(), "ruleVersion", i.ruleVersion(), "status", i.status().name()))
                .toList();
        map.put("items", items);
        return map;
    }
}
