package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.gateway.RuleValidator.CaseResult;
import java.util.List;

public record ValidationReport(boolean valid, List<String> errors, List<CaseResult> cases) {}
