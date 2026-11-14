package br.com.motorfraude.rules.application;

import java.util.List;

public final class RuleInvalidException extends RuntimeException {

    private final transient List<String> errors;

    public RuleInvalidException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
