package br.com.motorfraude.rules.application;

public final class RuleAlreadyExistsException extends RuntimeException {

    public RuleAlreadyExistsException(String ruleId) {
        super("já existe uma regra com o id " + ruleId);
    }
}
