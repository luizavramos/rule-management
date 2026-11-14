package br.com.motorfraude.rules.domain;

/** Uma versão de regra dentro de um conjunto publicado, com o status que tinha naquele conjunto (ACTIVE ou SHADOW). */
public record RuleSetItem(String ruleId, int ruleVersion, RuleStatus status) {

    public RuleSetItem {
        if (status != RuleStatus.ACTIVE && status != RuleStatus.SHADOW) {
            throw new IllegalArgumentException("um conjunto só contém regras ACTIVE ou SHADOW, recebido: " + status);
        }
    }
}
