package br.com.motorfraude.rules.domain;

/** Transição de status proibida (ex.: voltar a DRAFT, ou repetir o mesmo status). */
public final class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(RuleStatus from, RuleStatus to) {
        super("transição não permitida: " + from + " -> " + to);
    }
}
