package br.com.motorfraude.rules.domain;

public final class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(RuleStatus from, RuleStatus to) {
        super("transição não permitida: " + from + " -> " + to);
    }
}
