package br.com.motorfraude.rules.adapters.in.rest;

import br.com.motorfraude.rules.adapters.in.rest.ApiModels.RuleHistoryResponse;
import br.com.motorfraude.rules.adapters.in.rest.ApiModels.RuleRequest;
import br.com.motorfraude.rules.adapters.in.rest.ApiModels.RuleSetResponse;
import br.com.motorfraude.rules.adapters.in.rest.ApiModels.RuleSummary;
import br.com.motorfraude.rules.adapters.in.rest.ApiModels.ValidationResponse;
import br.com.motorfraude.rules.adapters.in.rest.ApiModels.VersionResponse;
import br.com.motorfraude.rules.application.usecase.ActivateRule;
import br.com.motorfraude.rules.application.usecase.CreateRule;
import br.com.motorfraude.rules.application.usecase.CreateRuleVersion;
import br.com.motorfraude.rules.application.usecase.DisableRule;
import br.com.motorfraude.rules.application.usecase.GetRule;
import br.com.motorfraude.rules.application.usecase.ListRules;
import br.com.motorfraude.rules.application.usecase.ShadowRule;
import br.com.motorfraude.rules.application.usecase.ValidateRule;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Regras e versões (plano 002, §4.4). Quem pode o quê é decidido pela segurança (papéis), não aqui; o ator de cada
 * mudança é o usuário autenticado.
 */
@RestController
public class RuleController {

    private final CreateRule createRule;
    private final CreateRuleVersion createRuleVersion;
    private final ValidateRule validateRule;
    private final ActivateRule activateRule;
    private final ShadowRule shadowRule;
    private final DisableRule disableRule;
    private final ListRules listRules;
    private final GetRule getRule;

    public RuleController(CreateRule createRule, CreateRuleVersion createRuleVersion, ValidateRule validateRule,
            ActivateRule activateRule, ShadowRule shadowRule, DisableRule disableRule, ListRules listRules, GetRule getRule) {
        this.createRule = createRule;
        this.createRuleVersion = createRuleVersion;
        this.validateRule = validateRule;
        this.activateRule = activateRule;
        this.shadowRule = shadowRule;
        this.disableRule = disableRule;
        this.listRules = listRules;
        this.getRule = getRule;
    }

    @PostMapping("/v1/rules")
    public ResponseEntity<VersionResponse> create(@RequestBody RuleRequest request, Principal principal) {
        var content = request.toContent(true);
        var version = createRule.execute(principal.getName(), request.ruleId(), content);
        return created(version.ruleId(), version.version(), VersionResponse.of(version));
    }

    @PostMapping("/v1/rules/{ruleId}/versions")
    public ResponseEntity<VersionResponse> createVersion(@PathVariable String ruleId, @RequestBody RuleRequest request, Principal principal) {
        var version = createRuleVersion.execute(principal.getName(), ruleId, request.toContent(false));
        return created(ruleId, version.version(), VersionResponse.of(version));
    }

    @GetMapping("/v1/rules")
    public List<RuleSummary> list() {
        return listRules.execute().stream().map(RuleSummary::of).toList();
    }

    @GetMapping("/v1/rules/{ruleId}")
    public RuleHistoryResponse get(@PathVariable String ruleId) {
        var history = getRule.execute(ruleId);
        return new RuleHistoryResponse(history.rule().ruleId(), history.rule().createdBy(), history.rule().createdAt().toString(),
                history.versions().stream().map(VersionResponse::of).toList());
    }

    /** Valida sem gravar nada. Expressão inválida não é erro da chamada: volta 200 com {@code valid=false}. */
    @PostMapping("/v1/rules:validate")
    public ValidationResponse validate(@RequestBody RuleRequest request) {
        return ValidationResponse.of(validateRule.execute(request.toContent(false), request.cases()));
    }

    @PostMapping("/v1/rules/{ruleId}/versions/{version}:activate")
    public RuleSetResponse activate(@PathVariable String ruleId, @PathVariable int version, Principal principal) {
        return RuleSetResponse.of(activateRule.execute(principal.getName(), ruleId, version));
    }

    @PostMapping("/v1/rules/{ruleId}/versions/{version}:shadow")
    public RuleSetResponse shadow(@PathVariable String ruleId, @PathVariable int version, Principal principal) {
        return RuleSetResponse.of(shadowRule.execute(principal.getName(), ruleId, version));
    }

    @PostMapping("/v1/rules/{ruleId}:disable")
    public RuleSetResponse disable(@PathVariable String ruleId, Principal principal) {
        return RuleSetResponse.of(disableRule.execute(principal.getName(), ruleId));
    }

    private static ResponseEntity<VersionResponse> created(String ruleId, int version, VersionResponse body) {
        return ResponseEntity.created(URI.create("/v1/rules/" + ruleId + "/versions/" + version)).body(body);
    }
}
