package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.domain.Scoring;
import java.util.List;

public final class UpdateScoring {

    private final RuleSetPublisher publisher;

    public UpdateScoring(RuleSetPublisher publisher) {
        this.publisher = publisher;
    }

    public RuleSet execute(String actor, int mediumThreshold, int highThreshold) {
        var scoring = new Scoring(mediumThreshold, highThreshold);
        return publisher.publish(actor, current ->
                new RuleSetPublisher.Change("SCORING_UPDATED", "scoring", current.items(), scoring, List.of()));
    }
}
