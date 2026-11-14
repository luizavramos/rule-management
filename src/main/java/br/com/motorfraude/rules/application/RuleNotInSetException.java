package br.com.motorfraude.rules.application;

public final class RuleNotInSetException extends RuntimeException {

    public RuleNotInSetException(String ruleId) {
        super("a regra " + ruleId + " não está no conjunto ativo");
    }
}
