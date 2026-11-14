package br.com.motorfraude.rules.application;

/** Já existe uma regra com esse id (vira 409). */
public final class RuleAlreadyExistsException extends RuntimeException {

    public RuleAlreadyExistsException(String ruleId) {
        super("já existe uma regra com o id " + ruleId);
    }
}
