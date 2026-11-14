package br.com.motorfraude.rules.application;

/** Regra, versão de regra ou conjunto inexistente (vira 404). */
public final class RuleNotFoundException extends RuntimeException {

    public RuleNotFoundException(String message) {
        super(message);
    }

    public static RuleNotFoundException rule(String ruleId) {
        return new RuleNotFoundException("regra não encontrada: " + ruleId);
    }

    public static RuleNotFoundException version(String ruleId, int version) {
        return new RuleNotFoundException("versão não encontrada: " + ruleId + " v" + version);
    }

    public static RuleNotFoundException ruleSet(long version) {
        return new RuleNotFoundException("conjunto de regras não encontrado: " + version);
    }
}
