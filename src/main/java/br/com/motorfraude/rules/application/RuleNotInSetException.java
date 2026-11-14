package br.com.motorfraude.rules.application;

/** A regra não está no conjunto ativo, então não há o que desligar (vira 409). */
public final class RuleNotInSetException extends RuntimeException {

    public RuleNotInSetException(String ruleId) {
        super("a regra " + ruleId + " não está no conjunto ativo");
    }
}
