package br.com.motorfraude.rules.adapters.controller;

import br.com.motorfraude.rules.adapters.presenter.ApiResponses.ActiveRuleSetResponse;
import br.com.motorfraude.rules.adapters.presenter.ApiResponses.RuleSetResponse;
import br.com.motorfraude.rules.application.RuleNotFoundException;
import br.com.motorfraude.rules.application.usecase.GetActiveRuleSet;
import br.com.motorfraude.rules.application.usecase.RollbackRuleSet;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuleSetController {

    private final RollbackRuleSet rollbackRuleSet;
    private final GetActiveRuleSet getActiveRuleSet;

    public RuleSetController(RollbackRuleSet rollbackRuleSet, GetActiveRuleSet getActiveRuleSet) {
        this.rollbackRuleSet = rollbackRuleSet;
        this.getActiveRuleSet = getActiveRuleSet;
    }

    @PostMapping("/v1/rule-sets/{version}:rollback")
    public RuleSetResponse rollback(@PathVariable long version, Principal principal) {
        return RuleSetResponse.of(rollbackRuleSet.execute(principal.getName(), version));
    }

    @GetMapping("/v1/rule-sets/active")
    public ResponseEntity<ActiveRuleSetResponse> active(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        var current = getActiveRuleSet.currentVersion();
        if (current.isEmpty()) {
            throw new RuleNotFoundException("nenhum conjunto de regras foi publicado");
        }
        if (matches(ifNoneMatch, current.getAsLong())) {
            return ResponseEntity.status(304).eTag(etag(current.getAsLong())).cacheControl(CacheControl.noCache()).build();
        }
        var active = getActiveRuleSet.execute().orElseThrow(() -> new RuleNotFoundException("nenhum conjunto de regras foi publicado"));
        return ResponseEntity.ok().eTag(etag(active.ruleSet().version())).cacheControl(CacheControl.noCache())
                .body(ActiveRuleSetResponse.of(active));
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static boolean matches(String ifNoneMatch, long version) {
        if (ifNoneMatch == null) {
            return false;
        }
        for (String token : ifNoneMatch.split(",")) {
            var candidate = token.strip();
            if (candidate.equals("*")) {
                return true;
            }
            if (candidate.startsWith("W/")) {
                candidate = candidate.substring(2);
            }
            if (candidate.length() >= 2 && candidate.startsWith("\"") && candidate.endsWith("\"")) {
                candidate = candidate.substring(1, candidate.length() - 1);
            }
            if (candidate.equals(Long.toString(version))) {
                return true;
            }
        }
        return false;
    }
}
