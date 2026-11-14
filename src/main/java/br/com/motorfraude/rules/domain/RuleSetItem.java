package br.com.motorfraude.rules.domain;

public record RuleSetItem(String ruleId, int ruleVersion, RuleStatus status) {

    public RuleSetItem {
        if (status != RuleStatus.ACTIVE && status != RuleStatus.SHADOW) {
            throw new IllegalArgumentException("um conjunto só contém regras ACTIVE ou SHADOW, recebido: " + status);
        }
    }
}
