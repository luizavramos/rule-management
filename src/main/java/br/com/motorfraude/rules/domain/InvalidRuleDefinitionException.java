package br.com.motorfraude.rules.domain;

import java.util.List;

/** A definição viola uma invariante (campo fora dos limites do contrato rule-set.v1). Traz todos os erros juntos. */
public final class InvalidRuleDefinitionException extends RuntimeException {

    private final transient List<String> errors;

    public InvalidRuleDefinitionException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
