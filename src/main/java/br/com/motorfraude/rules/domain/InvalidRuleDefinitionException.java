package br.com.motorfraude.rules.domain;

import java.util.List;

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
