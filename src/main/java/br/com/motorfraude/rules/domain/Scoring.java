package br.com.motorfraude.rules.domain;

import java.util.List;

public record Scoring(int mediumThreshold, int highThreshold) {

    public Scoring {
        if (mediumThreshold < 1 || highThreshold < 1 || mediumThreshold > highThreshold) {
            throw new InvalidRuleDefinitionException(List.of(
                    "scoring inválido: é preciso 1 <= mediumThreshold <= highThreshold (recebido "
                            + mediumThreshold + " e " + highThreshold + ")"));
        }
    }
}
