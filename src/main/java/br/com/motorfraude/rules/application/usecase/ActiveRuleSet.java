package br.com.motorfraude.rules.application.usecase;

import br.com.motorfraude.rules.domain.RuleSet;
import br.com.motorfraude.rules.domain.RuleVersion;
import java.util.List;

public record ActiveRuleSet(RuleSet ruleSet, List<RuleVersion> versions) {}
