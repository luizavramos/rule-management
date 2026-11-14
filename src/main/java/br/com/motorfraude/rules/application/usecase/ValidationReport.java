package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.application.gateway.RuleValidator.CaseResult;
import java.util.List;

/** Resultado de {@code :validate}: se a regra compila, os erros, e o resultado de cada caso de teste. */
public record ValidationReport(boolean valid, List<String> errors, List<CaseResult> cases) {}
