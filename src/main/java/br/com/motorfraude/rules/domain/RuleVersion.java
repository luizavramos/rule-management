package br.com.motorfraude.rules.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Uma versão de regra. O conteúdo é imutável: mudar a expressão, os parâmetros ou o peso cria outra versão. Só o
 * status muda (por {@link #transitionTo}, que devolve uma nova instância). Os limites são os do contrato rule-set.v1.
 */
public record RuleVersion(String ruleId, int version, String expression, Map<String, Object> params,
        Map<String, Map<String, Object>> paramOverrides, int weight, String reasonTemplate, RuleStatus status,
        String author, String approvedBy, Instant createdAt) {

    private static final Pattern RULE_ID = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{1,31}$");
    private static final Pattern PARAM_NAME = Pattern.compile("^[a-z][a-zA-Z0-9_.]{0,63}$");
    private static final Set<String> SEGMENTS = Set.of("PF", "PJ");
    private static final Map<RuleStatus, Set<RuleStatus>> TRANSITIONS = new EnumMap<>(RuleStatus.class);

    static {
        TRANSITIONS.put(RuleStatus.DRAFT, EnumSet.of(RuleStatus.ACTIVE, RuleStatus.SHADOW, RuleStatus.DISABLED));
        TRANSITIONS.put(RuleStatus.SHADOW, EnumSet.of(RuleStatus.ACTIVE, RuleStatus.DISABLED));
        TRANSITIONS.put(RuleStatus.ACTIVE, EnumSet.of(RuleStatus.SHADOW, RuleStatus.DISABLED));
        TRANSITIONS.put(RuleStatus.DISABLED, EnumSet.of(RuleStatus.ACTIVE, RuleStatus.SHADOW));
    }

    public RuleVersion {
        var errors = new ArrayList<String>();
        if (ruleId == null || !RULE_ID.matcher(ruleId).matches()) {
            errors.add("ruleId inválido: deve começar com letra e ter 2 a 32 caracteres (letras, números, _ ou -)");
        }
        if (version < 1) {
            errors.add("version deve ser 1 ou mais");
        }
        if (expression == null || expression.isBlank()) {
            errors.add("a expressão não pode ser vazia");
        } else if (expression.length() > 2000) {
            errors.add("a expressão deve ter no máximo 2000 caracteres");
        }
        if (weight < 0 || weight > 1000) {
            errors.add("weight deve estar entre 0 e 1000");
        }
        if (reasonTemplate == null || reasonTemplate.isBlank() || reasonTemplate.length() > 200) {
            errors.add("reasonTemplate deve ter de 1 a 200 caracteres");
        }
        if (author == null || author.isBlank()) {
            errors.add("author é obrigatório");
        }
        params = params == null ? Map.of() : params;
        paramOverrides = paramOverrides == null ? Map.of() : paramOverrides;
        for (String name : params.keySet()) {
            checkParamName(name, errors);
        }
        for (Map.Entry<String, Map<String, Object>> segment : paramOverrides.entrySet()) {
            if (!SEGMENTS.contains(segment.getKey())) {
                errors.add("segmento de override inválido: " + segment.getKey() + " (use PF ou PJ)");
            }
            for (String name : segment.getValue().keySet()) {
                checkParamName(name, errors);
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidRuleDefinitionException(errors);
        }
        params = Map.copyOf(params);
        var copy = new LinkedHashMap<String, Map<String, Object>>();
        paramOverrides.forEach((segment, values) -> copy.put(segment, Map.copyOf(values)));
        paramOverrides = Map.copyOf(copy);
        status = status == null ? RuleStatus.DRAFT : status;
    }

    private static void checkParamName(String name, List<String> errors) {
        String message = "nome de parâmetro inválido: " + name;
        if (!PARAM_NAME.matcher(name).matches() && !errors.contains(message)) {
            errors.add(message);
        }
    }

    /** Nova versão em rascunho. */
    public static RuleVersion draft(String ruleId, int version, String expression, Map<String, Object> params,
            Map<String, Map<String, Object>> paramOverrides, int weight, String reasonTemplate, String author,
            Instant createdAt) {
        return new RuleVersion(ruleId, version, expression, params, paramOverrides, weight, reasonTemplate,
                RuleStatus.DRAFT, author, null, createdAt);
    }

    public RuleVersion transitionTo(RuleStatus target, String actor) {
        if (!TRANSITIONS.get(status).contains(target)) {
            throw new InvalidTransitionException(status, target);
        }
        var approver = target == RuleStatus.ACTIVE ? actor : approvedBy;
        return new RuleVersion(ruleId, version, expression, params, paramOverrides, weight, reasonTemplate, target,
                author, approver, createdAt);
    }

    /** Ativa a versão; {@code approver} fica registrado em {@code approvedBy}. */
    public RuleVersion activate(String approver) {
        return transitionTo(RuleStatus.ACTIVE, approver);
    }

    public RuleVersion shadow() {
        return transitionTo(RuleStatus.SHADOW, null);
    }

    public RuleVersion disable() {
        return transitionTo(RuleStatus.DISABLED, null);
    }
}
