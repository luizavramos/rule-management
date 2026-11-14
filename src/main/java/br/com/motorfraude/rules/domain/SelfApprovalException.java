package br.com.motorfraude.rules.domain;

public final class SelfApprovalException extends RuntimeException {

    public SelfApprovalException(String ruleId, int version, String actor) {
        super("quem criou a regra não pode ativá-la: " + ruleId + " v" + version + " foi criada por " + actor);
    }
}
