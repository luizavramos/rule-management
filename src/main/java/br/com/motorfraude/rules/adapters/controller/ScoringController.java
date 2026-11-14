package br.com.motorfraude.rules.adapters.controller;

import br.com.motorfraude.rules.adapters.presenter.ApiResponses.RuleSetResponse;
import br.com.motorfraude.rules.adapters.controller.ApiRequests.ScoringRequest;
import br.com.motorfraude.rules.application.usecase.UpdateScoring;
import java.security.Principal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScoringController {

    private final UpdateScoring updateScoring;

    public ScoringController(UpdateScoring updateScoring) {
        this.updateScoring = updateScoring;
    }

    @PutMapping("/v1/scoring")
    public RuleSetResponse update(@RequestBody ScoringRequest request, Principal principal) {
        request.requireBoth();
        return RuleSetResponse.of(updateScoring.execute(principal.getName(), request.mediumThreshold(), request.highThreshold()));
    }
}
